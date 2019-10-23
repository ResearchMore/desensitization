/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package red.zyc.desensitization.metadata.resolver;

import red.zyc.desensitization.SensitiveUtil;
import red.zyc.desensitization.util.Optional;
import red.zyc.desensitization.util.ReflectionUtil;

import java.lang.reflect.*;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * @author zyc
 */
public class CollectionResolver implements Resolver<Collection<?>> {

    @Override
    public Collection<?> resolve(Collection<?> value, AnnotatedType annotatedType) {
        if (!(annotatedType instanceof AnnotatedParameterizedType)) {
            return value;
        }
        AnnotatedParameterizedType annotatedParameterizedType = (AnnotatedParameterizedType) annotatedType;
        AnnotatedType typeArgument = annotatedParameterizedType.getAnnotatedActualTypeArguments()[0];
        if (ReflectionUtil.isCollection(typeArgument)) {
            return value.stream().map(o -> resolve((Collection<?>) o, typeArgument)).collect(collectCollection(value));
        } else if (ReflectionUtil.isMap(typeArgument)) {
            return value.stream().map(o -> MAP_RESOLVER.resolve((Map<?, ?>) o, typeArgument)).collect(collectMap(value));
        } else if (typeArgument instanceof AnnotatedArrayType) {
            System.out.println(value);
            return value.stream().map(o -> ARRAY_RESOLVER.resolve((Object[]) o, typeArgument)).collect(collectArray(value));
        } else if (typeArgument instanceof AnnotatedTypeVariable) {
            for (AnnotatedType annotatedBound : ((AnnotatedTypeVariable) typeArgument).getAnnotatedBounds()) {
                if (ReflectionUtil.isCollection(annotatedBound)) {
                    value = value.stream().map(o -> resolve((Collection<?>) o, annotatedBound)).collect(collectCollection(value));
                } else if (ReflectionUtil.isMap(annotatedBound)) {
                    value = value.stream().map(o -> MAP_RESOLVER.resolve((Map<?, ?>) o, annotatedBound)).collect(collectMap(value));
                }
            }
            return resolveValue(value, typeArgument);
        } else if (typeArgument instanceof AnnotatedWildcardType) {
            AnnotatedWildcardType annotatedWildcardType = (AnnotatedWildcardType) typeArgument;
            AnnotatedType[] annotatedUpperBounds = annotatedWildcardType.getAnnotatedUpperBounds();
            AnnotatedType[] annotatedBounds = annotatedUpperBounds.length == 0 ? annotatedWildcardType.getAnnotatedLowerBounds() : annotatedUpperBounds;
            for (AnnotatedType annotatedBound : annotatedBounds) {
                if (ReflectionUtil.isCollection(annotatedBound)) {
                    value = value.stream().map(o -> resolve((Collection<?>) o, annotatedBound)).collect(collectCollection(value));
                } else if (ReflectionUtil.isMap(annotatedBound)) {
                    value = value.stream().map(o -> MAP_RESOLVER.resolve((Map<?, ?>) o, annotatedBound)).collect(collectMap(value));
                }
            }
            return resolveValue(value, typeArgument);
        } else {
            return resolveValue(value, typeArgument);
        }
    }

    @Override
    public Collection<?> resolveValue(Collection<?> value, AnnotatedType typeArgument) {
        // 泛型参数是没有标明泛型参数的Collection或Map
        if (ReflectionUtil.isCollection(typeArgument) || ReflectionUtil.isMap(typeArgument)) {
            return value;
        }
        return Optional.ofNullable(ReflectionUtil.getFirstSensitiveAnnotationOnAnnotatedType(typeArgument))
                .map(sensitiveAnnotation -> value.stream().map(o -> SensitiveUtil.handling(o, sensitiveAnnotation)).collect(collectValue(value)))
                .or(() -> Optional.ofNullable(ReflectionUtil.getEraseSensitiveAnnotationOnAnnotatedType(typeArgument))
                        .map(eraseSensitiveAnnotation -> value.stream().map(SensitiveUtil::desensitize).collect(collectValue(value))))
                .orElse((Collection<Object>) value);

    }

    private Collector<Collection<?>, ?, Collection<Collection<?>>> collectCollection(Collection<?> collections) {
        @SuppressWarnings("unchecked")
        Collection<Collection<?>> original = (Collection<Collection<?>>) collections;
        return Collectors.toCollection(() -> ReflectionUtil.constructCollection(ReflectionUtil.getClass(original)));
    }

    private Collector<Map<?, ?>, ?, Collection<Map<?, ?>>> collectMap(Collection<?> maps) {
        @SuppressWarnings("unchecked")
        Collection<Map<?, ?>> original = (Collection<Map<?, ?>>) maps;
        return Collectors.toCollection(() -> ReflectionUtil.constructCollection(ReflectionUtil.getClass(original)));
    }

    private Collector<Object[], ?, Collection<Object[]>> collectArray(Collection<?> arrays) {
        @SuppressWarnings("unchecked")
        Collection<Object[]> original = (Collection<Object[]>) arrays;
        return Collectors.toCollection(() -> ReflectionUtil.constructCollection(ReflectionUtil.getClass(original)));
    }

    private Collector<Object, ?, Collection<Object>> collectValue(Collection<?> values) {
        @SuppressWarnings("unchecked")
        Collection<Object> original = (Collection<Object>) values;
        return Collectors.toCollection(() -> ReflectionUtil.constructCollection(ReflectionUtil.getClass(original)));
    }
}
