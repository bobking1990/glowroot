/**
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.informant.core.weaving;

import io.informant.api.Logger;
import io.informant.api.LoggerFactory;
import io.informant.api.weaving.Mixin;
import io.informant.core.util.OnlyUsedByTests;
import io.informant.core.util.ThreadSafe;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import com.google.common.reflect.Reflection;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@OnlyUsedByTests
@ThreadSafe
public class IsolatedWeavingClassLoader extends ClassLoader {

    private static final Logger logger = LoggerFactory.getLogger(IsolatedWeavingClassLoader.class);

    private final Weaver weaver;
    // bridge classes can be either interfaces or base classes
    private final ImmutableList<Class<?>> bridgeClasses;
    private final ImmutableList<String> excludePackages;
    // guarded by 'this'
    private final Map<String, Class<?>> classes = Maps.newConcurrentMap();

    private final ThreadLocal<Boolean> inWeaving = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public static Builder builder() {
        return new Builder();
    }

    private IsolatedWeavingClassLoader(ImmutableList<Mixin> mixins, ImmutableList<Advice> advisors,
            ImmutableList<Class<?>> bridgeClasses, ImmutableList<String> excludePackages,
            WeavingMetric weavingMetric) {
        super(IsolatedWeavingClassLoader.class.getClassLoader());
        weaver = new Weaver(mixins, advisors, this, new ParsedTypeCache(), weavingMetric);
        this.bridgeClasses = bridgeClasses;
        this.excludePackages = excludePackages;
    }

    public <S, T extends S> S newInstance(Class<T> implClass, Class<S> bridgeClass)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {

        if (isBridgeable(bridgeClass.getName())) {
            return bridgeClass.cast(loadClass(implClass.getName()).newInstance());
        } else {
            throw new IllegalStateException("Class '" + bridgeClass + "' is not bridgeable");
        }
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {

        if (loadWithParentClassLoader(name)) {
            return super.loadClass(name, resolve);
        }
        Class<?> c = classes.get(name);
        if (c != null) {
            return c;
        }
        c = findClass(name);
        classes.put(name, c);
        return c;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        for (Class<?> bridgeClass : bridgeClasses) {
            if (bridgeClass.getName().equals(name)) {
                return bridgeClass;
            }
        }
        String resourceName = TypeNames.toInternal(name) + ".class";
        URL url = getResource(resourceName);
        if (url == null) {
            throw new ClassNotFoundException(name);
        }
        byte[] bytes;
        try {
            bytes = Resources.toByteArray(url);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // don't do recursive weaving (i.e. don't weave any of the classes which are performing the
        // weaving itself)
        if (!inWeaving.get()) {
            inWeaving.set(true);
            try {
                byte[] originalBytes = bytes;
                bytes = weaver.weave(originalBytes, name);
                if (bytes != originalBytes) {
                    logger.debug("findClass(): transformed {}", name);
                }
            } finally {
                inWeaving.remove();
            }
        }
        String packageName = Reflection.getPackageName(name);
        if (getPackage(packageName) == null) {
            definePackage(packageName, null, null, null, null, null, null, null);
        }
        return super.defineClass(name, bytes, 0, bytes.length);
    }

    private <S> boolean isBridgeable(String name) {
        if (isInBootstrapClassLoader(name)) {
            return true;
        }
        for (Class<?> bridgeClass : bridgeClasses) {
            if (bridgeClass.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean loadWithParentClassLoader(String name) {
        if (isInBootstrapClassLoader(name)) {
            return true;
        }
        for (String excludePackage : excludePackages) {
            if (name.startsWith(excludePackage + ".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInBootstrapClassLoader(String name) {
        try {
            Class<?> c = Class.forName(name, false, ClassLoader.getSystemClassLoader());
            return c.getClassLoader() == null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static class Builder {
        private final ImmutableList.Builder<Mixin> mixins = ImmutableList.builder();
        private final ImmutableList.Builder<Advice> advisors = ImmutableList.builder();
        private final ImmutableList.Builder<Class<?>> bridgeClasses = ImmutableList.builder();
        private final ImmutableList.Builder<String> excludePackages = ImmutableList.builder();
        private WeavingMetric weavingMetric = NopWeavingMetric.INSTANCE;
        private Builder() {}
        public void addMixins(Mixin... mixins) {
            this.mixins.add(mixins);
        }
        public void addAdvisors(Advice... advisors) {
            this.advisors.add(advisors);
        }
        public void addBridgeClasses(Class<?>... bridgeClasses) {
            this.bridgeClasses.add(bridgeClasses);
        }
        public void addExcludePackages(String... excludePackages) {
            this.excludePackages.add(excludePackages);
        }
        public void weavingMetric(WeavingMetric weavingMetric) {
            this.weavingMetric = weavingMetric;
        }
        public IsolatedWeavingClassLoader build() {
            return new IsolatedWeavingClassLoader(mixins.build(), advisors.build(),
                    bridgeClasses.build(), excludePackages.build(), weavingMetric);
        }
    }
}
