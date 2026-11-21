package com.luatweaker.core.bind;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;
import org.luava.runtime.LuaFunction;
import org.luava.runtime.LuaNil;
import org.luava.runtime.LuaState;
import org.luava.runtime.LuaValue;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Property read/write mapping for {@link DynamicJavaProxy}: Lua property
 * accesses become Java getter calls, assignments become setter calls, and
 * exact method names stay invokable.
 *
 * <p>Lookup order for a read {@code obj.Key}: boolean {@code isKey} getter,
 * exact method name, decapitalized method name, then {@code getKey} /
 * {@code isKey}. A heuristic match (e.g. {@code "Entity"} to
 * {@code getEntity}) must never become a callable: property access has to
 * yield the value.</p>
 */
final class ProxyPropertyBridge {
    private ProxyPropertyBridge() {}

    /** Builds the {@code __index} handler closing over one proxied object. */
    static LuaFunction indexHandler(@NotNull LuaState state, @NotNull Class<?> clazz,
            @NotNull Object javaObject) {
        return LuaFunction.of(args -> {
            if (args.length < 2 || args[1] == null || args[1].isNil()) {
                return LuaNil.NIL;
            }
            if (JavaMethodResolver.isClassBlocked(state, clazz)) {
                return LuaNil.NIL;
            }
            String key = args[1].toLuaString();
            if (key.isEmpty()) {
                return LuaNil.NIL;
            }
            LuaValue booleanResult = tryBooleanGetter(state, clazz, javaObject, key);
            if (booleanResult != null) {
                return booleanResult;
            }
            LuaValue methodResult = tryMethodReference(state, clazz, javaObject, key);
            if (methodResult != null) {
                return methodResult;
            }
            return tryPropertyGetter(state, clazz, javaObject, key);
        });
    }

    /**
     * @return the getter result, or {@code null} when the key is not a
     *         {@code isXxx} boolean getter
     */
    private static @Nullable LuaValue tryBooleanGetter(@NotNull LuaState state, @NotNull Class<?> clazz,
            @NotNull Object javaObject, @NotNull String key) {
        if (key.length() <= 2 || !(key.startsWith("is") || key.startsWith("Is"))) {
            return null;
        }
        Method booleanGetter = JavaMethodResolver.findMethodCached(clazz, "is" + key.substring(2));
        if (booleanGetter == null || booleanGetter.getParameterCount() != 0
                || !isBooleanReturn(booleanGetter)) {
            return null;
        }
        try {
            return DynamicJavaProxy.wrapResultLuava(state, booleanGetter.invoke(javaObject));
        } catch (ReflectiveOperationException e) {
            throw LuaFunction.hostError("Error invoking getter for " + key + ": " + e.getMessage(), e);
        }
    }

    private static boolean isBooleanReturn(@NotNull Method method) {
        return method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class;
    }

    /**
     * @return a wrapped method, an eagerly-resolved property value, or
     *         {@code null} when the key names no method at all
     */
    private static @Nullable LuaValue tryMethodReference(@NotNull LuaState state, @NotNull Class<?> clazz,
            @NotNull Object javaObject, @NotNull String key) {
        // Exact method name: a method-call intent (obj:getX() / obj:setX(...)).
        if (JavaMethodResolver.hasPublicMethodNamed(clazz, key)) {
            return DynamicJavaProxy.wrapMethod(state, javaObject, key);
        }
        // PascalCase method name (obj:GetCustomData(...) -> getCustomData(...)).
        if (!key.isEmpty() && Character.isUpperCase(key.charAt(0))) {
            String decapitalized = Character.toLowerCase(key.charAt(0)) + key.substring(1);
            if (JavaMethodResolver.hasPublicMethodNamed(clazz, decapitalized)) {
                return tryEagerGetter(state, clazz, javaObject, key, decapitalized);
            }
        }
        return null;
    }

    /**
     * A zero-argument non-void decapitalized method that is not a getter-style
     * call is read eagerly (property access yields the value, not a callable).
     */
    private static @NotNull LuaValue tryEagerGetter(@NotNull LuaState state, @NotNull Class<?> clazz,
            @NotNull Object javaObject, @NotNull String key, @NotNull String methodName) {
        Method method = JavaMethodResolver.findMethodCached(clazz, methodName);
        if (method != null && method.getParameterCount() == 0 && method.getReturnType() != void.class
                && !key.startsWith("Get") && !key.startsWith("Set")) {
            try {
                Object result = method.invoke(javaObject);
                return result == null ? LuaNil.NIL : DynamicJavaProxy.wrapResultLuava(state, result);
            } catch (ReflectiveOperationException e) {
                throw LuaFunction.hostError("Error invoking getter for " + key + ": " + e.getMessage(), e);
            }
        }
        return DynamicJavaProxy.wrapMethod(state, javaObject, methodName);
    }

    /** Property-to-getter translation ({@code "Health"} to {@code getHealth} / {@code isHealth}). */
    private static @NotNull LuaValue tryPropertyGetter(@NotNull LuaState state, @NotNull Class<?> clazz,
            @NotNull Object javaObject, @NotNull String key) {
        String capitalized = key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
        Method getter = JavaMethodResolver.findMethodCached(clazz, "get" + capitalized);
        if (getter == null) {
            getter = JavaMethodResolver.findMethodCached(clazz, "is" + capitalized);
        }
        if (getter != null && getter.getParameterCount() == 0) {
            try {
                Object result = getter.invoke(javaObject);
                return result == null ? LuaNil.NIL : DynamicJavaProxy.wrapResultLuava(state, result);
            } catch (ReflectiveOperationException e) {
                throw LuaFunction.hostError("Error invoking getter for " + key + ": " + e.getMessage(), e);
            }
        }
        return LuaNil.NIL;
    }

    /** Builds the {@code __newindex} handler closing over one proxied object. */
    static LuaFunction newIndexHandler(@NotNull LuaState state, @NotNull Class<?> clazz,
            @NotNull Object javaObject) {
        return LuaFunction.of(args -> {
            if (args.length < 3 || args[1] == null || args[1].isNil()) {
                return LuaNil.NIL;
            }
            if (JavaMethodResolver.isClassBlocked(state, clazz)) {
                return LuaNil.NIL;
            }
            String key = args[1].toLuaString();
            if (key.isEmpty()) {
                return LuaNil.NIL;
            }
            String capitalized = key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
            Method setter = JavaMethodResolver.selectMethod(clazz, "set" + capitalized, args[2]);
            if (setter == null) {
                // Fail loudly: a silent no-op made `entity.Owner = x` disappear with no trace.
                throw new LuaException("No Java setter for property '" + key + "' on "
                        + clazz.getSimpleName());
            }
            try {
                Object javaArg = LuaCoercion.coerceLuaToJava(args[2], setter.getParameterTypes()[0]);
                setter.invoke(javaObject, javaArg);
            } catch (ReflectiveOperationException e) {
                throw LuaFunction.hostError(
                    "Error invoking setter for " + key + ": " + e.getMessage(), e);
            }
            return LuaNil.NIL;
        });
    }
}
