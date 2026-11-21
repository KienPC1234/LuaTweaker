package com.luatweaker.core.remap;

import com.luatweaker.api.remap.IRemappingService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeRemapper implements IRemappingService {

    private static final RuntimeRemapper INSTANCE = new RuntimeRemapper();

    private final Map<String, Map<String, Method>> methodCache = new ConcurrentHashMap<>();
    private final Map<String, Method> signatureCache = new ConcurrentHashMap<>();
    private final AtomicReference<Boolean> obfuscated = new AtomicReference<>();

    private RuntimeRemapper() {
    }

    public static RuntimeRemapper getInstance() {
        return INSTANCE;
    }

    private boolean detectObfuscation(@NotNull Class<?> sampleClass) {
        if (Boolean.TRUE.equals(obfuscated.get())) {
            return true;
        }

        String envProp = System.getProperty("luatweaker.obfuscated");
        if (envProp != null) {
            boolean forced = Boolean.parseBoolean(envProp);
            obfuscated.set(forced);
            return forced;
        }

        Class<?> current = sampleClass;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (ObfuscationHeuristics.OBFUSCATED_ANY.matcher(m.getName()).matches()) {
                    obfuscated.set(true);
                    return true;
                }
            }
            current = current.getSuperclass();
        }

        return false;
    }

    @Override
    @Nullable
    public Method resolveMethod(@NotNull Class<?> clazz, @NotNull String methodName) {
        Map<String, Method> classCache = methodCache.computeIfAbsent(clazz.getName(), k -> new ConcurrentHashMap<>());

        if (classCache.containsKey(methodName)) {
            return classCache.get(methodName);
        }

        Method result = resolveMethodImpl(clazz, methodName, (Class<?>[]) null);
        if (result != null) {
            classCache.put(methodName, result);
        }
        return result;
    }

    @Nullable
    public Method resolveMethod(@NotNull Class<?> clazz, @NotNull String methodName, @Nullable Class<?>... paramHints) {
        String cacheKey = clazz.getName() + "#" + methodName + "#" + (paramHints != null ? paramHints.length : "null");
        if (signatureCache.containsKey(cacheKey)) {
            return signatureCache.get(cacheKey);
        }

        Method result = resolveMethodImpl(clazz, methodName, paramHints);
        if (result != null) {
            signatureCache.put(cacheKey, result);
        }
        return result;
    }

    /** Single place where overload ambiguity is counted: exactly one candidate wins outright. */
    private static final int SINGLE_CANDIDATE_COUNT = 1;

    @Nullable
    private Method resolveMethodImpl(@NotNull Class<?> clazz, @NotNull String methodName, @Nullable Class<?>... paramHints) {
        detectObfuscation(clazz);
        List<Method> candidates = MethodCandidateCollector.collectExact(clazz, methodName);
        candidates.addAll(MethodCandidateCollector.collectHeuristic(clazz, methodName));

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == SINGLE_CANDIDATE_COUNT) {
            return candidates.get(0);
        }
        return OverloadResolver.resolveOverload(candidates, paramHints);
    }

    @Override
    @Nullable
    public Method resolveMethodBySignature(
            @NotNull Class<?> clazz,
            @NotNull String baseName,
            @Nullable Class<?>[] paramTypes,
            @Nullable Class<?> returnType
    ) {
        String sigKey = clazz.getName() + "#" + baseName + "#" +
                (paramTypes != null ? Arrays.toString(paramTypes) : "null") + "#" +
                (returnType != null ? returnType.getName() : "null");

        if (signatureCache.containsKey(sigKey)) {
            return signatureCache.get(sigKey);
        }

        Method result = resolveBySignatureImpl(clazz, baseName, paramTypes, returnType);
        if (result != null) {
            signatureCache.put(sigKey, result);
        }
        return result;
    }

    private static final int SINGLE_MATCH_COUNT = 1;

    @Nullable
    private Method resolveBySignatureImpl(
            @NotNull Class<?> clazz,
            @NotNull String baseName,
            @Nullable Class<?>[] paramTypes,
            @Nullable Class<?> returnType
    ) {
        List<Method> candidates = MethodCandidateCollector.collectBySignature(clazz, baseName, paramTypes, returnType);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == SINGLE_MATCH_COUNT) {
            return candidates.get(0);
        }
        if (paramTypes != null) {
            Method exact = OverloadResolver.resolveOverload(candidates, paramTypes);
            if (exact != null) {
                return exact;
            }
        }
        return candidates.get(0);
    }

    @Override
    public boolean isObfuscatedEnvironment() {
        return Boolean.TRUE.equals(obfuscated.get());
    }

    public void clearCache() {
        methodCache.clear();
        signatureCache.clear();
        obfuscated.set(null);
    }
}
