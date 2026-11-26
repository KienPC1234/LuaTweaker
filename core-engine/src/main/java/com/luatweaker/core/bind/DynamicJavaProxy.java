package com.luatweaker.core.bind;

import org.luava.runtime.LuaException;
import org.luava.runtime.LuaFunction;
import org.luava.runtime.LuaNil;
import org.luava.runtime.LuaState;
import org.luava.runtime.LuaString;
import org.luava.runtime.LuaTable;
import org.luava.runtime.LuaUserdata;
import org.luava.runtime.LuaValue;

import java.lang.reflect.Method;

/**
 * Universal Proxy for Java Objects in LuaTweaker.
 * Intercepts property accesses (e.g. {@code event.amount}) and maps them to Java getters
 * ({@code event.getAmount()}), and assignments ({@code event.canceled = true}) to Java setters
 * ({@code event.setCanceled(true)}). Method names resolve exact matches first, then score
 * overloads by argument compatibility.
 *
 * <p>Thin facade: reflection caching and the security gate live in
 * {@link JavaMethodResolver}, Lua/Java value conversion in {@link LuaCoercion},
 * and property read/write mapping in {@link ProxyPropertyBridge}.</p>
 */
public final class DynamicJavaProxy {
    private DynamicJavaProxy() {}

    /**
     * Creates a dynamic bridge userdata backed by luava. Property reads are translated to
     * getters ({@code Health -> getHealth()}, {@code isAlive -> isAlive()}), writes to setters,
     * and exact method names become invokable luava functions. Reflection is blocked by the
     * project package blacklist plus the active {@code JavaAccessPolicy}.
     *
     * @param state      active luava state (used to consult its Java access policy)
     * @param javaObject object to expose, must not be {@code null}
     * @return userdata with {@code __index}/{@code __newindex} metatable handlers
     */
    public static LuaUserdata createLuava(LuaState state, Object javaObject) {
        LuaUserdata userdata = new LuaUserdata(javaObject);
        Class<?> clazz = javaObject.getClass();
        LuaTable metatable = new LuaTable();
        metatable.rawset(LuaValue.Meta.INDEX,
            ProxyPropertyBridge.indexHandler(state, clazz, javaObject));
        metatable.rawset(LuaValue.Meta.NEWINDEX,
            ProxyPropertyBridge.newIndexHandler(state, clazz, javaObject));
        userdata.setMetatable(metatable);
        return userdata;
    }

    private static boolean isSelfArgument(LuaValue arg, Object instance) {
        if (arg instanceof LuaUserdata userdata) {
            return userdata.getJavaInstance() == instance;
        }
        if (arg instanceof LuaTable table) {
            LuaValue entityVal = table.rawget(LuaString.interned("__entity"));
            return entityVal instanceof LuaUserdata userdata
                    && matchesInstance(userdata.getJavaInstance(), instance);
        }
        return false;
    }

    private static boolean matchesInstance(Object javaObj, Object instance) {
        return javaObj == instance
                || (javaObj instanceof com.luatweaker.api.entity.IEntity entity
                && entity.getRawEntity() == instance);
    }

    static LuaFunction wrapMethod(LuaState state, Object instance, String methodName) {
        return LuaFunction.of(args -> {
            int luaArgIndex = 0;
            if (args.length >= 1 && isSelfArgument(args[0], instance)) {
                luaArgIndex = 1; // Skip self on colon calls.
            }
            int luaArgCount = args.length - luaArgIndex;
            LuaValue[] luaArgs = new LuaValue[luaArgCount];
            System.arraycopy(args, luaArgIndex, luaArgs, 0, luaArgCount);

            Method method = JavaMethodResolver.selectMethod(instance.getClass(), methodName, luaArgs);
            if (method == null) {
                throw new LuaException("No matching Java overload for method " + methodName + " with "
                        + luaArgCount + " argument(s)");
            }

            int paramCount = method.getParameterCount();
            Object[] javaArgs = new Object[paramCount];
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramCount; i++) {
                javaArgs[i] = LuaCoercion.coerceLuaToJava(luaArgs[i], paramTypes[i]);
            }

            try {
                Object result = method.invoke(instance, javaArgs);
                return wrapResultLuava(state, result);
            } catch (ReflectiveOperationException e) {
                throw LuaFunction.hostError(
                        "Error invoking Java method " + methodName + ": " + e.getMessage(), e);
            }
        });
    }

    /**
     * Converts a Java return value into a luava value.
     *
     * @param state      active luava state
     * @param javaObject value to wrap, may be {@code null}
     * @return the equivalent luava value; complex objects become dynamic proxies
     */
    public static LuaValue wrapResultLuava(LuaState state, Object javaObject) {
        if (javaObject == null) {
            return LuaNil.NIL;
        }
        if (javaObject instanceof String text) {
            return LuaValue.valueOf(text);
        }
        if (javaObject instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        if (javaObject instanceof Boolean flag) {
            return LuaValue.valueOf(flag);
        }
        if (javaObject instanceof LuaValue luavaValue) {
            return luavaValue;
        }
        // Complex objects (including foreign ILuaValue implementations) stay usable
        // through the dynamic bridge instead of throwing a ClassCastException.
        return createLuava(state, javaObject);
    }
}
