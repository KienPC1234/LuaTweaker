package com.luatweaker.core.bind;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.binding.JavaAccessPolicy;
import org.luava.runtime.LuaState;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.luava.runtime.LuaException;

/**
 * Cached reflection lookup behind {@link DynamicJavaProxy}, plus the security
 * gate that keeps dangerous packages and methods out of Lua's reach.
 *
 * <p>Three layers of defense (see {@link DynamicJavaProxy} for the policy): the
 * luava VM isolation, the method blacklist below, and the package blacklist.
 * Everything here is a pure static lookup; results are cached per class so a
 * missing property never re-scans reflection on a hot path.</p>
 */
final class JavaMethodResolver {
    private JavaMethodResolver() {}

    /** Method cache: class to method name to single best method (empty when absent). */
    private static final Map<Class<?>, Map<String, java.util.Optional<Method>>> METHOD_CACHE =
            new ConcurrentHashMap<>();
    /** Overload cache: class to method name to every candidate, including remapped ones. */
    private static final Map<Class<?>, Map<String, List<Method>>> OVERLOAD_CACHE = new ConcurrentHashMap<>();
    /** Declared public method names per class, computed once. */
    private static final Map<Class<?>, Set<String>> PUBLIC_METHOD_NAMES = new ConcurrentHashMap<>();
    /** Package blacklist verdict per class (GC-safe, no strong class references). */
    private static final ClassValue<Boolean> PACKAGE_BLOCKED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String name = type.getName();
            for (String blocked : PACKAGE_BLACKLIST) {
                if (name.startsWith(blocked)) {
                    return true;
                }
            }
            return false;
        }
    };

    /** Blocked dangerous packages (RCE, data exfiltration, DoS, reflection escape). */
    private static final Set<String> PACKAGE_BLACKLIST = Set.of(
        "java.lang.Runtime",
        "java.lang.Process",
        "java.lang.ProcessBuilder",
        "java.lang.System",
        "java.lang.Thread",
        "java.lang.Class",
        "java.lang.ClassLoader",
        "java.lang.reflect.",
        "java.lang.invoke.",
        "java.io.",
        "java.nio.",
        "java.net.",
        "javax.net.",
        "sun.",
        "com.sun.",
        "jdk."
    );

    /** Blocked dangerous reflection entry points. */
    private static final Set<String> METHOD_BLACKLIST = Set.of(
        "getClass", "wait", "notify", "notifyAll", "clone", "finalize", "hashCode"
    );

    /** True when the class must stay invisible to Lua (blacklisted package or denied policy). */
    static boolean isClassBlocked(@Nullable LuaState state, @NotNull Class<?> clazz) {
        if (Boolean.TRUE.equals(PACKAGE_BLOCKED.get(clazz))) {
            return true;
        }
        JavaAccessPolicy policy = state != null && state.getJavaAccessPolicy() != null
                ? state.getJavaAccessPolicy()
                : JavaAccessPolicy.active();
        return policy.isDenied(clazz.getName());
    }

    /** Single best method for the name, or {@code null} when absent or blocked. */
    static @Nullable Method findMethodCached(@NotNull Class<?> clazz, @NotNull String methodName) {
        if (isClassBlocked(null, clazz)) {
            return null;
        }
        if (METHOD_BLACKLIST.contains(methodName)) {
            return null;
        }
        return METHOD_CACHE
                .computeIfAbsent(clazz, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(methodName, name -> java.util.Optional.ofNullable(resolveMethod(clazz, name)))
                .orElse(null);
    }

    /** Every overload for the name, or an empty list when absent or blocked. */
    static @NotNull List<Method> findMethodsCached(@NotNull Class<?> clazz, @NotNull String methodName) {
        if (isClassBlocked(null, clazz) || METHOD_BLACKLIST.contains(methodName)) {
            return List.of();
        }
        Map<String, List<Method>> classCache = OVERLOAD_CACHE.computeIfAbsent(clazz, key -> new ConcurrentHashMap<>());
        return classCache.computeIfAbsent(methodName, name -> collectOverloads(clazz, name));
    }

    private static @NotNull List<Method> collectOverloads(@NotNull Class<?> clazz, @NotNull String name) {
        List<Method> methods = new ArrayList<>();
        for (Method candidate : clazz.getMethods()) {
            if (candidate.getName().equals(name)) {
                methods.add(candidate);
            }
        }
        Method remapped = com.luatweaker.core.remap.RuntimeRemapper.getInstance().resolveMethod(clazz, name);
        if (remapped != null && remapped.getName().equals(name)
                && methods.stream().noneMatch(known -> sameSignature(known, remapped))) {
            methods.add(remapped);
        }
        return List.copyOf(methods);
    }

    /** True when the class declares a public method with exactly this name. */
    static boolean hasPublicMethodNamed(@NotNull Class<?> clazz, @NotNull String methodName) {
        return !isClassBlocked(null, clazz)
            && !METHOD_BLACKLIST.contains(methodName)
            && PUBLIC_METHOD_NAMES
                .computeIfAbsent(clazz, key -> {
                    Set<String> names = new java.util.HashSet<>();
                    for (Method method : key.getMethods()) {
                        names.add(method.getName());
                    }
                    return Set.copyOf(names);
                })
                .contains(methodName);
    }

    /** Host-side lookup (reflection + remapper) behind the cached entry. */
    private static @Nullable Method resolveMethod(@NotNull Class<?> clazz, @NotNull String methodName) {
        // Prefer the overload with the fewest parameters (getters/setters are
        // the common case); getMethods() order is not guaranteed, so caching
        // the first hit would be non-deterministic across overloads.
        Method best = null;
        for (Method candidate : clazz.getMethods()) {
            if (candidate.getName().equals(methodName)
                    && (best == null || candidate.getParameterCount() < best.getParameterCount())) {
                best = candidate;
            }
        }
        if (best != null) {
            return best;
        }
        return com.luatweaker.core.remap.RuntimeRemapper.getInstance().resolveMethod(clazz, methodName);
    }

    private static boolean sameSignature(@NotNull Method left, @NotNull Method right) {
        return left.getName().equals(right.getName())
            && Arrays.equals(left.getParameterTypes(), right.getParameterTypes());
    }

    /**
     * Picks the overload whose parameters best fit the Lua arguments.
     *
     * @return the winning method, or {@code null} when nothing is compatible
     * @throws LuaException when two overloads tie (the call would be a guess)
     */
    static @Nullable Method selectMethod(@NotNull Class<?> clazz, @NotNull String methodName,
            org.luava.runtime.LuaValue... args) {
        Method best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (Method method : findMethodsCached(clazz, methodName)) {
            if (method.getParameterCount() != args.length) {
                continue;
            }
            int score = LuaCompatibilityScorer.scoreArguments(method.getParameterTypes(), args);
            if (score < 0) {
                continue;
            }
            if (score < bestScore) {
                best = method;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        if (ambiguous) {
            throw new LuaException("Ambiguous Java overload for method " + methodName + " with "
                    + args.length + " argument(s)");
        }
        return best;
    }
}
