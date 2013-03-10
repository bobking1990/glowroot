/**
 * Copyright 2012-2013 the original author or authors.
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
package io.informant.weaving;

import io.informant.api.weaving.Mixin;
import io.informant.util.ThreadSafe;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import checkers.nullness.quals.Nullable;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@ThreadSafe
public class WeavingClassFileTransformer implements ClassFileTransformer {

    private static final Logger logger = LoggerFactory.getLogger(WeavingClassFileTransformer.class);

    private final ImmutableList<Mixin> mixins;
    private final ImmutableList<Advice> advisors;
    private final WeavingMetric metric;

    private final ParsedTypeCache parsedTypeCache;

    // it is important to only have a single weaver per class loader because storing state of each
    // previously parsed class in order to re-construct class hierarchy in case one or more .class
    // files aren't available via ClassLoader.getResource()
    //
    // weak keys to prevent retention of class loaders
    private final LoadingCache<ClassLoader, Weaver> weavers =
            CacheBuilder.newBuilder().weakKeys()
                    .build(new CacheLoader<ClassLoader, Weaver>() {
                        @Override
                        public Weaver load(ClassLoader loader) {
                            return new Weaver(mixins, advisors, loader, parsedTypeCache, metric);
                        }
                    });
    // the weaver for the bootstrap class loader (null) has to be stored separately since
    // LoadingCache doesn't accept null keys, and using an Optional<ClassLoader> for the key makes
    // the weakness on the Optional instance instead of on the ClassLoader instance
    private final Weaver bootLoaderWeaver;

    // because of the crazy pre-initialization of javaagent classes (see
    // io.informant.core.weaving.PreInitializeClasses), all inputs into this class should be
    // concrete, non-subclassed types so that the correct set of used classes can be computed (see
    // calculation in the test class io.informant.core.weaving.preinit.GlobalCollector, and
    // hard-coded results in io.informant.core.weaving.PreInitializeClasses)
    // note: an exception is made for WeavingMetric, see PreInitializeClassesTest for explanation
    public WeavingClassFileTransformer(Mixin[] mixins, Advice[] advisors,
            ParsedTypeCache parsedTypeCache, WeavingMetric metric) {
        this.mixins = ImmutableList.copyOf(mixins);
        this.advisors = ImmutableList.copyOf(advisors);
        this.parsedTypeCache = parsedTypeCache;
        this.metric = metric;
        bootLoaderWeaver = new Weaver(this.mixins, this.advisors, null, parsedTypeCache, metric);
        PreInitializeClasses.preInitializeClasses(WeavingClassFileTransformer.class
                .getClassLoader());
    }

    public byte[] transform(@Nullable ClassLoader loader, String className,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] bytes) {
        // don't weave informant-core classes, included shaded classes like h2 jdbc driver
        if (className.startsWith("io/informant/config/")
                || className.startsWith("io/informant/core/")
                || className.startsWith("io/informant/local/")
                || className.startsWith("io/informant/shaded/")
                || className.startsWith("io/informant/util/")
                || className.startsWith("io/informant/weaving/")) {
            return bytes;
        }
        logger.trace("transform(): className={}", className);
        Weaver weaver;
        if (loader == null) {
            weaver = bootLoaderWeaver;
        } else {
            weaver = weavers.getUnchecked(loader);
        }
        byte[] transformedBytes = weaver.weave(bytes, protectionDomain, className);
        if (transformedBytes != bytes) {
            logger.debug("transform(): transformed {}", className);
        }
        return transformedBytes;
    }
}