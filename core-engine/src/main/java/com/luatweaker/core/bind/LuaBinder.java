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
        return (args.length > 0 && args != null && args[0] != null && args[0].isTable()) ? 1 : 0;
    }

    /** Binds the interface to a fresh Lua table without registering any service or global. */
    public static ILuaTable bindTable(ILuaEngine engine, Object impl, Class<?> api) {
        ILuaTable table = engine.createTable();

        List<Method> methods = new ArrayList<>();
        for (Method m : api.getMethods()) {
            if (m.isSynthetic() || m.isBridge()) continue;
            if (Modifier.isStatic(m.getModifiers())) continue;
            if ("getRawEntity".equals(m.getName())) continue;
            if (m.getParameterCount() > 8) continue;
            methods.add(m);
        }

        // Deterministic order; when overloads exist keep the most-parameter variant.
        methods.sort(Comparator
                .comparing(Method::getName)
                .thenComparing(Comparator.comparingInt(Method::getParameterCount).reversed()));

        Set<String> seen = new HashSet<>();
        for (Method m : methods) {
            if (!seen.add(m.getName())) continue;
            ILuaFunction fn = args -> invoke(engine, impl, m, args);
            table.rawset(m.getName(), fn);
            String pascal = pascalCase(m.getName());
            if (!pascal.equals(m.getName())) {
                table.rawset(pascal, fn);
            }
        }
        return table;
    }

    private static ILuaValue invoke(ILuaEngine engine, Object impl, Method method, ILuaValue[] args) throws Exception {
        if (impl == null) {
            return engine.nilValue();
        }
        int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
        Class<?>[] paramTypes = method.getParameterTypes();
        java.lang.reflect.Parameter[] paramMeta = method.getParameters();
        Object[] callArgs = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            boolean provided = off + i < args.length && args[off + i] != null && !args[off + i].isNil();
            if (!provided) {
                String def = paramMeta[i].isAnnotationPresent(LuaDefault.class)
                        ? paramMeta[i].getAnnotation(LuaDefault.class).value()
                        : null;
                if (def == null) {
                    throw new IllegalArgumentException(method.getName() + " requires argument " + (i + 1)
                            + " (" + paramMeta[i].getType().getSimpleName() + ")");
                }
                callArgs[i] = parseDefault(def, paramTypes[i], method.getName(), i + 1);
            } else {
                callArgs[i] = convertArg(paramTypes[i], args[off + i]);
            }
        }

        Object result = method.invoke(impl, callArgs);
        return toLua(engine, method.getReturnType(), result);
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
