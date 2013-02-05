/**
 * Copyright 2013 the original author or authors.
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

import io.informant.core.util.Static;
import checkers.nullness.quals.PolyNull;

import com.google.common.collect.ImmutableList;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Static
final class TypeNames {

    @PolyNull
    public static String fromInternal(@PolyNull String typeName) {
        if (typeName == null) {
            return null;
        } else {
            return typeName.replace('/', '.');
        }
    }

    public static ImmutableList<String> fromInternal(String/*@Nullable*/[] typeNames) {
        if (typeNames == null) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (String typeName : typeNames) {
            builder.add(typeName.replace('/', '.'));
        }
        return builder.build();
    }

    @PolyNull
    public static String toInternal(@PolyNull String typeName) {
        if (typeName == null) {
            return null;
        } else {
            return typeName.replace('.', '/');
        }
    }

    private TypeNames() {}
}
