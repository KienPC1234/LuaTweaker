package com.luatweaker.core.remap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection scans behind {@link RuntimeRemapper}: exact-name, heuristic and
 * signature-based candidate collection.
 *
 * <p>Kept apart from the remapper so the cache/lookup policy and the reflection walk
 * can change independently. Every scan walks the class hierarchy until
 * {@link Object}, matching the order reflective lookup relies on.</p>
 */
final class MethodCandidateCollector {
    private MethodCandidateCollector() {}

    /** Public methods whose name is exactly {@code methodName}. */
    static @NotNull List<Method> collectExact(@NotNull Class<?> clazz, @NotNull String methodName) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getMethods()) {
                if (method.getName().equals(methodName)) {
                    candidates.add(method);
                }
            }
        }
        return candidates;
    }

    /** Declared methods whose obfuscated name heuristically denotes {@code methodName}. */
    static @NotNull List<Method> collectHeuristic(@NotNull Class<?> clazz, @NotNull String methodName) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)
                        && ObfuscationHeuristics.matchesHeuristic(method.getName(), methodName)) {
                    method.trySetAccessible();
                    candidates.add(method);
                }
            }
        }
        return candidates;
    }

    /**
     * Methods matching the requested signature. With no parameter hints the first
     * exact-name match wins outright, mirroring the pre-remap lookup order.
     */
    static @NotNull List<Method> collectBySignature(@NotNull Class<?> clazz, @NotNull String baseName,
                                                    @Nullable Class<?>[] paramTypes,
                                                    @Nullable Class<?> returnType) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            Method exact = scanSignatureMatches(current, baseName, paramTypes, returnType, candidates);
            if (exact != null) {
                return List.of(exact);
            }
        }
        return candidates;
    }

    /**
     * Adds this class level's signature matches to {@code candidates}.
     *
     * @return the exact-name match when no parameter hints were given (caller returns it
     *         immediately), otherwise {@code null}
     */
    private static @Nullable Method scanSignatureMatches(@NotNull Class<?> type, @NotNull String baseName,
                                                          @Nullable Class<?>[] paramTypes,
                                                          @Nullable Class<?> returnType,
                                                          @NotNull List<Method> candidates) {
        for (Method method : type.getMethods()) {
            if (!matchesSignatureFilters(method, paramTypes, returnType)) {
                continue;
            }
            if (method.getName().equals(baseName)) {
                if (paramTypes == null) {
                    return method;
                }
                candidates.add(method);
            } else if (ObfuscationHeuristics.matchesHeuristic(method.getName(), baseName)) {
                candidates.add(method);
            }
        }
        return null;
    }

    private static boolean matchesSignatureFilters(@NotNull Method method,
                                                   @Nullable Class<?>[] paramTypes,
                                                   @Nullable Class<?> returnType) {
        if (paramTypes != null && method.getParameterCount() != paramTypes.length) {
            return false;
        }
        return returnType == null || method.getReturnType().equals(returnType)
                || returnType.isAssignableFrom(method.getReturnType());
    }
}
