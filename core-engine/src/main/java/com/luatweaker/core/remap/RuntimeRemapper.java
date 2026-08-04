package com.luatweaker.core.remap;

import com.luatweaker.api.remap.IRemappingService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class RuntimeRemapper implements IRemappingService {

    private static final RuntimeRemapper INSTANCE = new RuntimeRemapper();

    private static final Pattern OBFUSCATED_MOJMAP = Pattern.compile("^m_\\d+_$");
    private static final Pattern OBFUSCATED_INTERMEDIARY = Pattern.compile("^method_\\d+$");
    private static final Pattern OBFUSCATED_ANY = Pattern.compile("^(m_\\d+_$|method_\\d+|f_\\d+_$|field_\\d+)$");

    private final Map<String, Map<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    private final Map<String, Method> SIGNATURE_CACHE = new ConcurrentHashMap<>();
    private volatile Boolean obfuscated;

    private RuntimeRemapper() {
        this.obfuscated = null;
    }

    public static RuntimeRemapper getInstance() {
        return INSTANCE;
    }

    private boolean detectObfuscation(@NotNull Class<?> sampleClass) {
        if (obfuscated != null) return obfuscated;

        String envProp = System.getProperty("luatweaker.obfuscated");
        if (envProp != null) {
            obfuscated = Boolean.parseBoolean(envProp);
            return obfuscated;
        }

        Class<?> current = sampleClass;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (OBFUSCATED_ANY.matcher(m.getName()).matches()) {
                    obfuscated = true;
                    return true;
                }
            }
            current = current.getSuperclass();
        }

        obfuscated = false;
        return false;
    }

    @Override
    @Nullable
    public Method resolveMethod(@NotNull Class<?> clazz, @NotNull String methodName) {
        String cacheKey = clazz.getName() + "#" + methodName;
        Map<String, Method> classCache = METHOD_CACHE.computeIfAbsent(clazz.getName(), k -> new ConcurrentHashMap<>());

        if (classCache.containsKey(methodName)) {
            return classCache.get(methodName);
        }

        Method result = resolveMethodImpl(clazz, methodName, null);
        if (result != null) {
            classCache.put(methodName, result);
        }
        return result;
    }

    @Nullable
    public Method resolveMethod(@NotNull Class<?> clazz, @NotNull String methodName, @Nullable Class<?>[] paramHints) {
        String cacheKey = clazz.getName() + "#" + methodName + "#" + (paramHints != null ? paramHints.length : "null");
        if (SIGNATURE_CACHE.containsKey(cacheKey)) {
            return SIGNATURE_CACHE.get(cacheKey);
        }

        Method result = resolveMethodImpl(clazz, methodName, paramHints);
        if (result != null) {
            SIGNATURE_CACHE.put(cacheKey, result);
        }
        return result;
    }

    @Nullable
    private Method resolveMethodImpl(@NotNull Class<?> clazz, @NotNull String methodName, @Nullable Class<?>[] paramHints) {
        List<Method> candidates = new ArrayList<>();

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getMethods()) {
                if (m.getName().equals(methodName)) {
                    candidates.add(m);
                }
            }
            current = current.getSuperclass();
        }

        current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (!m.getName().equals(methodName) && matchesHeuristic(m.getName(), methodName)) {
                    try { m.setAccessible(true); } catch (Exception ignored) {}
                    candidates.add(m);
                }
            }
            current = current.getSuperclass();
        }

        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        return resolveOverload(candidates, paramHints);
    }

    @Nullable
    private Method resolveOverload(@NotNull List<Method> candidates, @Nullable Class<?>[] paramHints) {
        if (paramHints == null || paramHints.length == 0) {
            return candidates.stream()
                    .min(Comparator.comparingInt(m -> m.getParameterCount()))
                    .orElse(null);
        }

        List<Method> exactParamMatch = new ArrayList<>();
        List<Method> compatibleParamMatch = new ArrayList<>();

        for (Method m : candidates) {
            Class<?>[] methodParams = m.getParameterTypes();
            if (methodParams.length != paramHints.length) continue;

            boolean exactMatch = true;
            boolean compatible = true;
            for (int i = 0; i < methodParams.length; i++) {
                if (methodParams[i].equals(paramHints[i])) {
                    continue;
                } else if (methodParams[i].isAssignableFrom(paramHints[i])
                        || isBoxingCompatible(methodParams[i], paramHints[i])) {
                    exactMatch = false;
                } else {
                    compatible = false;
                    break;
                }
            }

            if (exactMatch) exactParamMatch.add(m);
            else if (compatible) compatibleParamMatch.add(m);
        }

        if (!exactParamMatch.isEmpty()) return exactParamMatch.get(0);
        if (!compatibleParamMatch.isEmpty()) return compatibleParamMatch.get(0);

        return candidates.get(0);
    }

    private boolean isBoxingCompatible(Class<?> a, Class<?> b) {
        if (a.isPrimitive() && !b.isPrimitive()) {
            return getWrapperType(a).equals(b);
        }
        if (!a.isPrimitive() && b.isPrimitive()) {
            return a.equals(getWrapperType(b));
        }
        return false;
    }

    private Class<?> getWrapperType(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == double.class) return Double.class;
        if (primitive == float.class) return Float.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == short.class) return Short.class;
        if (primitive == char.class) return Character.class;
        return primitive;
    }

    private boolean matchesHeuristic(@NotNull String actualName, @NotNull String requestedName) {
        if (OBFUSCATED_ANY.matcher(actualName).matches()) return false;
        if (actualName.equalsIgnoreCase(requestedName)) return true;

        String lowerRequested = requestedName.toLowerCase();
        String lowerActual = actualName.toLowerCase();

        if (lowerActual.endsWith(lowerRequested)) return true;

        String stripped = requestedName;
        if (stripped.startsWith("get") || stripped.startsWith("set") || stripped.startsWith("is")) {
            stripped = stripped.substring(3);
            if (stripped.isEmpty()) return false;
            stripped = stripped.substring(0, 1).toLowerCase() + stripped.substring(1);
        }

        return lowerActual.contains(stripped.toLowerCase());
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

        if (SIGNATURE_CACHE.containsKey(sigKey)) {
            return SIGNATURE_CACHE.get(sigKey);
        }

        Method result = resolveBySignatureImpl(clazz, baseName, paramTypes, returnType);
        if (result != null) {
            SIGNATURE_CACHE.put(sigKey, result);
        }
        return result;
    }

    @Nullable
    private Method resolveBySignatureImpl(
            @NotNull Class<?> clazz,
            @NotNull String baseName,
            @Nullable Class<?>[] paramTypes,
            @Nullable Class<?> returnType
    ) {
        List<Method> candidates = new ArrayList<>();

        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getMethods()) {
                if (paramTypes != null && m.getParameterCount() != paramTypes.length) continue;
                if (returnType != null && !m.getReturnType().equals(returnType)
                        && !returnType.isAssignableFrom(m.getReturnType())) continue;

                if (m.getName().equals(baseName)) {
                    if (paramTypes == null) return m;
                    candidates.add(m);
                } else if (matchesHeuristic(m.getName(), baseName)) {
                    candidates.add(m);
                }
            }
            current = current.getSuperclass();
        }

        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        if (paramTypes != null) {
            Method exact = resolveOverload(candidates, paramTypes);
            if (exact != null) return exact;
        }

        return candidates.get(0);
    }

    @Override
    @Nullable
    public String remapMethodName(@NotNull String methodName) {
        return methodName;
    }

    @Override
    public boolean isObfuscatedEnvironment() {
        if (obfuscated != null) return obfuscated;
        return false;
    }

    public void clearCache() {
        METHOD_CACHE.clear();
        SIGNATURE_CACHE.clear();
        obfuscated = null;
    }
}
