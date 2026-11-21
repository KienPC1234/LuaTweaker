package com.luatweaker.core.bind;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.lang.reflect.InvocationTargetException;
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

    private static final String RAW_ENTITY_GETTER = "getRawEntity";
    private static final int MAX_BIND_PARAMETERS = 12;

    /** Converts a Java return value into an {@link ILuaValue} for a specific return type. */
    @FunctionalInterface
    public interface ValueConverter {
        ILuaValue toLua(ILuaEngine engine, Object value);
    }

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
     * Replaces the banned manual 'int off = (args.length > 0 && args[0].isTable()) ? 1 : 0' anti-pattern.
     *
     * <p>Because a receiver table and a genuine table argument are indistinguishable here,
     * a dot call whose first real argument is a table cannot be expressed: {@code Json.Encode(t)}
     * is read as {@code Json:Encode()} with a missing argument. Lua scripts must use the colon
     * form ({@code Json:Encode(t)}) whenever the first argument is a table. See docs/JSON_API.md.</p>
     */
    public static int getOffset(ILuaValue... args) {
        return (args != null && args.length > 0 && args[0] != null && args[0].isTable()) ? 1 : 0;
    }

    /** Binds the interface to a fresh Lua table without registering any service or global. */
    public static ILuaTable bindTable(ILuaEngine engine, Object impl, Class<?> api) {
        ILuaTable table = engine.createTable();

        for (BoundMethod bound : bindPlan(api)) {
            ILuaFunction fn = args -> {
                try {
                    return invoke(engine, impl, bound, args);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(describeBindingFailure(bound, e), e);
                }
            };
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
        List<Method> methods = collectBindableMethods(api);

        // Deterministic order; when overloads exist keep the most-parameter variant.
        methods.sort(Comparator
                .comparing(Method::getName)
                .thenComparing(Comparator.comparingInt(Method::getParameterCount).reversed()));

        Set<String> seen = new HashSet<>();
        List<BoundMethod> plan = new ArrayList<>(methods.size());
        for (Method m : methods) {
            if (!seen.add(m.getName())) { continue; }
            plan.add(buildBoundMethod(m));
        }
        return List.copyOf(plan);
    }

    /** Collects the interface methods that become Lua-callable entries. */
    private static List<Method> collectBindableMethods(Class<?> api) {
        List<Method> methods = new ArrayList<>();
        for (Method m : api.getMethods()) {
            if (isSkippedBindingMethod(m)) { continue; }
            methods.add(m);
        }
        return methods;
    }

    /** Synthetic, static, internal or overly-wide methods never become Lua entries. */
    private static boolean isSkippedBindingMethod(Method m) {
        return m.isSynthetic() || m.isBridge()
            || Modifier.isStatic(m.getModifiers())
            || RAW_ENTITY_GETTER.equals(m.getName())
            || m.getParameterCount() > MAX_BIND_PARAMETERS;
    }

    /** Resolves one method's parameter types and raw {@code @LuaDefault} values. */
    private static BoundMethod buildBoundMethod(Method m) {
        java.lang.reflect.Parameter[] params = m.getParameters();
        Class<?>[] paramTypes = m.getParameterTypes();
        String[] defaultRaws = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            LuaDefault annotation = params[i].getAnnotation(LuaDefault.class);
            if (annotation != null) {
                defaultRaws[i] = annotation.value();
            }
        }
        String methodName = m.getName();
        String pascalName = methodName.isEmpty() || Character.isUpperCase(methodName.charAt(0))
            ? methodName
            : Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
        return new BoundMethod(m, methodName, pascalName, paramTypes, defaultRaws, m.getReturnType());
    }

    /**
     * Names the binding and the root failure in one message so a Lua
     * traceback shows both which binding broke and why (the reflection
     * wrapper itself is chained as the cause).
     */
    private static String describeBindingFailure(BoundMethod bound, ReflectiveOperationException e) {
        Throwable root = e instanceof InvocationTargetException ite && ite.getCause() != null
            ? ite.getCause()
            : e;
        String detail = root.getMessage() != null ? root.getMessage() : root.toString();
        return "Lua binding failed for " + bound.method().getName() + ": " + detail;
    }

    private static ILuaValue invoke(ILuaEngine engine, Object impl, BoundMethod bound, ILuaValue... args)
            throws ReflectiveOperationException {
        if (impl == null) {
            return engine.nilValue();
        }
        int off = getOffset(args);
        Class<?>[] paramTypes = bound.paramTypes();
        String[] defaultRaws = bound.defaultRaws();
        Object[] callArgs = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            if (off + i < args.length && args[off + i] != null && !args[off + i].isNil()) {
                callArgs[i] = LuaArgConverter.convertArg(paramTypes[i], args[off + i]);
            } else if (defaultRaws[i] != null) {
                callArgs[i] = LuaArgConverter.parseDefault(defaultRaws[i], paramTypes[i],
                    bound.method().getName(), i + 1);
            } else {
                throw new IllegalArgumentException(bound.method().getName() + " requires argument " + (i + 1)
                        + " (" + paramTypes[i].getSimpleName() + ")");
            }
        }

        Object result = bound.method().invoke(impl, callArgs);
        return LuaResultConverter.toLua(engine, bound.returnType(), result);
    }
}
