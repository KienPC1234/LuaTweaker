package com.luatweaker.core.bind;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates Lua bindings from a plain Java interface, eliminating the
 * hand-written argument-parsing glue that used to be repeated per method.
 *
 * <p>Rules applied for every interface method:</p>
 * <ul>
 *   <li>Both {@code camelCase} and {@code PascalCase} names are exposed.</li>
 *   <li>Colon calls pass the receiver as the first argument and it is skipped.</li>
 *   <li>{@link LuaDefault}-annotated parameters become optional arguments.</li>
 *   <li>Missing required arguments raise a descriptive error instead of silently no-oping.</li>
 *   <li>{@link IEntity} parameters are unwrapped from Lua entity tables ({@code __entity}).</li>
 * </ul>
 */
public final class LuaBinder {
    private LuaBinder() {}

    /** Converts a Java return value into an {@link ILuaValue} for a specific return type. */
    public interface ValueConverter {
        ILuaValue toLua(ILuaEngine engine, Object value);
    }

    private static final Map<Class<?>, ValueConverter> RETURN_CONVERTERS = new ConcurrentHashMap<>();

    /**
     * Precomputed binding plan for one API interface: sorted, de-duplicated methods
     * with their parameter types and raw {@code @LuaDefault} values resolved once.
     *
     * <p>{@code bindTable} runs per entity wrapper per scan (thousands of times per
     * tick), so re-running {@code Class.getMethods()} + sort + annotation lookups
     * on every call is pure overhead. Interface metadata never changes at runtime,
     * making this cache semantically transparent.</p>
     */
    private record BoundMethod(Method method, String camelName, String pascalName,
            Class<?>[] paramTypes, String[] defaultRaws, Class<?> returnType) {}

    private static final Map<Class<?>, List<BoundMethod>> BIND_PLAN_CACHE = new ConcurrentHashMap<>();

    public static void registerReturnConverter(Class<?> type, ValueConverter converter) {
        RETURN_CONVERTERS.put(type, converter);
    }

    /**
     * Binds the interface to a Lua table, registers it as a service and global,
     * and optionally under additional alias names.
     */
    public static ILuaTable bind(ILuaEngine engine, String name, Object impl, Class<?> api, String... aliases) {
        ILuaTable table = bindTable(engine, impl, api);
        engine.registerService(name, table);
        engine.getGlobalEnvironment().rawset(name, table);
        for (String alias : aliases) {
            engine.registerService(alias, table);
            engine.getGlobalEnvironment().rawset(alias, table);
        }
        return table;
    }

    /**
     * Standard helper to determine if the first argument is a table (the self parameter).
     * Replaces the banned manual 'int off = (args.length > 0 && args[0].isTable()) ? 1 : 0' anti-pattern.
     */
    public static int getOffset(ILuaValue[] args) {
        return (args != null && args.length > 0 && args[0] != null && args[0].isTable()) ? 1 : 0;
    }

    /** Binds the interface to a fresh Lua table without registering any service or global. */
    public static ILuaTable bindTable(ILuaEngine engine, Object impl, Class<?> api) {
        ILuaTable table = engine.createTable();

        for (BoundMethod bound : bindPlan(api)) {
            ILuaFunction fn = args -> invoke(engine, impl, bound, args);
            table.rawset(bound.camelName(), fn);
            if (!bound.pascalName().equals(bound.camelName())) {
                table.rawset(bound.pascalName(), fn);
            }
        }
        return table;
    }

    /** Returns the cached binding plan for an API interface, computing it once. */
    private static List<BoundMethod> bindPlan(Class<?> api) {
        return BIND_PLAN_CACHE.computeIfAbsent(api, LuaBinder::computeBindPlan);
    }

    private static List<BoundMethod> computeBindPlan(Class<?> api) {
        List<Method> methods = new ArrayList<>();
        for (Method m : api.getMethods()) {
            if (m.isSynthetic() || m.isBridge()) continue;
            if (Modifier.isStatic(m.getModifiers())) continue;
            if ("getRawEntity".equals(m.getName())) continue;
            if (m.getParameterCount() > 12) continue;
            methods.add(m);
        }

        // Deterministic order; when overloads exist keep the most-parameter variant.
        methods.sort(Comparator
                .comparing(Method::getName)
                .thenComparing(Comparator.comparingInt(Method::getParameterCount).reversed()));

        Set<String> seen = new HashSet<>();
        List<BoundMethod> plan = new ArrayList<>(methods.size());
        for (Method m : methods) {
            if (!seen.add(m.getName())) continue;
            java.lang.reflect.Parameter[] params = m.getParameters();
            Class<?>[] paramTypes = m.getParameterTypes();
            String[] defaultRaws = new String[params.length];
            for (int i = 0; i < params.length; i++) {
                LuaDefault annotation = params[i].getAnnotation(LuaDefault.class);
                defaultRaws[i] = annotation == null ? null : annotation.value();
            }
            plan.add(new BoundMethod(m, m.getName(), pascalCase(m.getName()),
                    paramTypes, defaultRaws, m.getReturnType()));
        }
        return List.copyOf(plan);
    }

    private static ILuaValue invoke(ILuaEngine engine, Object impl, BoundMethod bound, ILuaValue[] args) throws Exception {
        if (impl == null) {
            return engine.nilValue();
        }
        int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
        Class<?>[] paramTypes = bound.paramTypes();
        String[] defaultRaws = bound.defaultRaws();
        Object[] callArgs = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            boolean provided = off + i < args.length && args[off + i] != null && !args[off + i].isNil();
            if (!provided) {
                String def = defaultRaws[i];
                if (def == null) {
                    throw new IllegalArgumentException(bound.method().getName() + " requires argument " + (i + 1)
                            + " (" + paramTypes[i].getSimpleName() + ")");
                }
                callArgs[i] = parseDefault(def, paramTypes[i], bound.method().getName(), i + 1);
            } else {
                callArgs[i] = convertArg(paramTypes[i], args[off + i]);
            }
        }

        Object result = bound.method().invoke(impl, callArgs);
        return toLua(engine, bound.returnType(), result);
    }

    private static Object convertArg(Class<?> type, ILuaValue arg) {
        if (type == String.class || type == CharSequence.class) return arg.asString();
        if (type == int.class || type == Integer.class) return arg.asInt();
        if (type == long.class || type == Long.class) return (long) arg.asDouble();
        if (type == double.class || type == Double.class) return arg.asDouble();
        if (type == float.class || type == Float.class) return (float) arg.asDouble();
        if (type == boolean.class || type == Boolean.class) return arg.asBoolean();
        if (type == ILuaValue.class) return arg;
        if (type == ILuaTable.class) return arg.asTable();
        if (type == Object.class) return arg;
        if (IEntity.class.isAssignableFrom(type)) return unwrapEntity(arg);

        // Array parameters (e.g. String[] biomes) arrive as Lua tables.
        if (type.isArray() && arg.isTable()) {
            ILuaTable tbl = arg.asTable();
            int len = tbl.length();
            Class<?> comp = type.getComponentType();
            Object array = java.lang.reflect.Array.newInstance(comp, len);
            for (int i = 1; i <= len; i++) {
                ILuaValue item = tbl.rawget(i);
                if (item == null || item.isNil()) break;
                java.lang.reflect.Array.set(array, i - 1, convertArg(comp, item));
            }
            return array;
        }

        Object raw = arg.toJavaObject();
        if (raw != null && type.isAssignableFrom(raw.getClass())) return raw;
        return raw;
    }

    private static IEntity unwrapEntity(ILuaValue arg) {
        if (arg.isTable()) {
            ILuaValue inner = arg.asTable().rawget("__entity");
            if (inner != null && !inner.isNil()) {
                Object obj = inner.toJavaObject();
                if (obj instanceof IEntity entity) {
                    return entity;
                }
            }
        }
        return null;
    }

    private static Object parseDefault(String def, Class<?> type, String methodName, int index) {
        try {
            if (type == String.class || type == CharSequence.class) return def;
            if (type == int.class || type == Integer.class) return Integer.parseInt(def);
            if (type == long.class || type == Long.class) return Long.parseLong(def);
            if (type == double.class || type == Double.class) return Double.parseDouble(def);
            if (type == float.class || type == Float.class) return Float.parseFloat(def);
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(def);
            if (type == ILuaValue.class || type == ILuaTable.class || type == Object.class) return null;
            if (IEntity.class.isAssignableFrom(type)) return null;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid @LuaDefault for " + methodName + " argument " + index + ": " + def);
        }
        return null;
    }

    private static ILuaValue toLua(ILuaEngine engine, Class<?> returnType, Object result) {
        if (result == null || returnType == void.class || returnType == Void.class) {
            return engine.nilValue();
        }
        if (result instanceof ILuaValue lv) return lv;
        if (result instanceof String s) return engine.wrapString(s);
        if (result instanceof Boolean b) return engine.wrapBoolean(b);
        if (result instanceof Number n) return engine.wrapNumber(n.doubleValue());

        // Maps and Lists must arrive as Lua tables (not userdata) so scripts
        // can index them directly (e.g. Loot:GetModifications()["mob"]).
        if (result instanceof java.util.Map<?, ?> map) {
            ILuaTable table = engine.createTable();
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                table.rawset(String.valueOf(entry.getKey()), toLua(engine, entry.getValue() == null ? Object.class : entry.getValue().getClass(), entry.getValue()));
            }
            return table;
        }
        if (result instanceof java.util.List<?> list) {
            ILuaTable table = engine.createTable();
            int i = 1;
            for (Object item : list) {
                table.rawset(i++, toLua(engine, item == null ? Object.class : item.getClass(), item));
            }
            return table;
        }

        ValueConverter converter = RETURN_CONVERTERS.get(returnType);
        if (converter != null) {
            return converter.toLua(engine, result);
        }
        return engine.wrapUserdata(result);
    }

    private static String pascalCase(String name) {
        if (name == null || name.isEmpty() || Character.isUpperCase(name.charAt(0))) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
