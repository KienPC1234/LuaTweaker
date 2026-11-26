package com.luatweaker.core.bind;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBindPlan.BoundMethod;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

/**
 * Generates Lua bindings from a plain Java interface, eliminating the
 * hand-written argument-parsing glue that used to be repeated per method.
 */
public final class LuaBinder {
    private LuaBinder() {}

    private static final Object[] EMPTY_CALL_ARGS = new Object[0];

    /** Converts a Java return value into an {@link ILuaValue} for a specific return type. */
    @FunctionalInterface
    public interface ValueConverter {
        ILuaValue toLua(ILuaEngine engine, Object value);
    }

    public static void registerReturnConverter(Class<?> type, ValueConverter converter) {
        LuaResultConverter.registerReturnConverter(type, converter);
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
        com.luatweaker.core.stub.StubCatalog.register(name, api);
        com.luatweaker.core.vm.NativeModuleResolver.registerModule(name, aliases);
        return table;
    }

    /**
     * Standard helper to determine if the first argument is a table (the self parameter).
     */
    public static int getOffset(ILuaValue... args) {
        return (args != null && args.length > 0 && args[0] != null && args[0].isTable()) ? 1 : 0;
    }

    /** Binds the interface to a fresh Lua table without registering any service or global. */
    public static ILuaTable bindTable(ILuaEngine engine, Object impl, Class<?> api) {
        ILuaTable table = engine.createTable();

        for (BoundMethod bound : LuaBindPlan.forInterface(api)) {
            ILuaFunction fn = args -> invokeChecked(engine, impl, bound, args);
            registerBoundFunction(table, bound, fn);
        }
        return table;
    }

    /**
     * Binds an API interface once into a shared prototype table whose functions extract
     * the Java receiver dynamically from {@code args[0]} (the {@code self} table).
     */
    public static ILuaTable bindPrototype(ILuaEngine engine, Class<?> api,
                                          Function<ILuaValue, Object> receiverExtractor) {
        ILuaTable prototype = engine.createTable();

        for (BoundMethod bound : LuaBindPlan.forInterface(api)) {
            ILuaFunction fn = args -> {
                Object target = (args != null && args.length > 0 && args[0] != null)
                        ? receiverExtractor.apply(args[0])
                        : null;
                return invokeChecked(engine, target, bound, args);
            };
            registerBoundFunction(prototype, bound, fn);
        }
        return prototype;
    }

    private static void registerBoundFunction(ILuaTable table, BoundMethod bound, ILuaFunction fn) {
        table.rawset(bound.camelName(), fn);
        if (!bound.pascalName().equals(bound.camelName())) {
            table.rawset(bound.pascalName(), fn);
        }
    }

    private static ILuaValue invokeChecked(ILuaEngine engine, Object impl, BoundMethod bound, ILuaValue... args) {
        try {
            return invoke(engine, impl, bound, args);
        } catch (InvocationTargetException e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            String detail = root.getMessage() != null ? root.getMessage() : root.toString();
            throw new IllegalStateException("Lua binding failed for " + bound.method().getName() + ": " + detail, e);
        } catch (ReflectiveOperationException e) {
            String detail = e.getMessage() != null ? e.getMessage() : e.toString();
            throw new IllegalStateException("Lua binding failed for " + bound.method().getName() + ": " + detail, e);
        }
    }

    private static ILuaValue invoke(ILuaEngine engine, Object impl, BoundMethod bound, ILuaValue... args)
            throws ReflectiveOperationException {
        if (impl == null) {
            return engine.nilValue();
        }
        Class<?>[] paramTypes = bound.paramTypes();
        if (paramTypes.length == 0) {
            Object result = bound.method().invoke(impl, EMPTY_CALL_ARGS);
            return LuaResultConverter.toLua(engine, bound.returnType(), result);
        }
        int off = getOffset(args);
        Object[] callArgs = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            callArgs[i] = resolveArg(bound, i, args, off);
        }

        Object result = bound.method().invoke(impl, callArgs);
        return LuaResultConverter.toLua(engine, bound.returnType(), result);
    }

    private static Object resolveArg(BoundMethod bound, int index, ILuaValue[] args, int offset) {
        int argIndex = offset + index;
        if (argIndex < args.length && args[argIndex] != null && !args[argIndex].isNil()) {
            return LuaArgConverter.convertArg(bound.paramTypes()[index], args[argIndex]);
        }
        if (bound.defaultReadyFlags()[index]) {
            return bound.parsedDefaults()[index];
        }
        String rawDefault = bound.defaultRaws()[index];
        if (rawDefault != null) {
            return LuaArgConverter.parseDefault(rawDefault, bound.paramTypes()[index],
                    bound.method().getName(), index + 1);
        }
        throw new IllegalArgumentException(bound.method().getName() + " requires argument " + (index + 1)
                + " (" + bound.paramTypes()[index].getSimpleName() + ")");
    }
}
