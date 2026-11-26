package com.luatweaker.core.bind;

import com.luatweaker.api.annotation.LuaDefault;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Precomputes and caches {@link LuaBinder} method metadata per Java API interface so
 * repeated table/prototype binding avoids reflection and annotation scans.
 */
final class LuaBindPlan {

    private static final String RAW_ENTITY_GETTER = "getRawEntity";
    private static final int MAX_BIND_PARAMETERS = 12;

    record BoundMethod(Method method, String camelName, String pascalName,
            Class<?>[] paramTypes, String[] defaultRaws, boolean[] defaultReadyFlags,
            Object[] parsedDefaults, Class<?> returnType) {}

    private static final Map<Class<?>, List<BoundMethod>> CACHE = new ConcurrentHashMap<>();

    private LuaBindPlan() {}

    static List<BoundMethod> forInterface(Class<?> api) {
        return CACHE.computeIfAbsent(api, LuaBindPlan::computePlan);
    }

    private static List<BoundMethod> computePlan(Class<?> api) {
        List<Method> methods = collectBindableMethods(api);
        methods.sort(Comparator
                .comparing(Method::getName)
                .thenComparing(Comparator.comparingInt(Method::getParameterCount).reversed()));

        Set<String> seen = new HashSet<>();
        List<BoundMethod> plan = new ArrayList<>(methods.size());
        for (Method method : methods) {
            if (seen.add(method.getName())) {
                plan.add(buildBoundMethod(method));
            }
        }
        return List.copyOf(plan);
    }

    private static List<Method> collectBindableMethods(Class<?> api) {
        List<Method> methods = new ArrayList<>();
        for (Method method : api.getMethods()) {
            if (!isSkippedBindingMethod(method)) {
                methods.add(method);
            }
        }
        return methods;
    }

    private static boolean isSkippedBindingMethod(Method method) {
        return method.isSynthetic() || method.isBridge()
                || Modifier.isStatic(method.getModifiers())
                || RAW_ENTITY_GETTER.equals(method.getName())
                || method.getParameterCount() > MAX_BIND_PARAMETERS;
    }

    private static BoundMethod buildBoundMethod(Method method) {
        Parameter[] params = method.getParameters();
        Class<?>[] paramTypes = method.getParameterTypes();
        String[] defaultRaws = new String[params.length];
        boolean[] defaultReadyFlags = new boolean[params.length];
        Object[] parsedDefaults = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            LuaDefault annotation = params[i].getAnnotation(LuaDefault.class);
            if (annotation == null) {
                continue;
            }
            String raw = annotation.value();
            defaultRaws[i] = raw;
            Object parsed = LuaArgConverter.tryPreparseDefault(raw, paramTypes[i]);
            if (parsed != LuaArgConverter.INVALID_DEFAULT) {
                parsedDefaults[i] = parsed;
                defaultReadyFlags[i] = true;
            }
        }
        String methodName = method.getName();
        String pascalName = methodName.isEmpty() || Character.isUpperCase(methodName.charAt(0))
                ? methodName
                : Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
        return new BoundMethod(method, methodName, pascalName, paramTypes, defaultRaws,
                defaultReadyFlags, parsedDefaults, method.getReturnType());
    }
}
