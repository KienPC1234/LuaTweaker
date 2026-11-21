package com.luatweaker.core.bind;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.binding.LuaDataConverter;
import org.luava.runtime.LuaUserdata;
import org.luava.runtime.LuaValue;

/**
 * Converts values between Lua and Java for {@link DynamicJavaProxy}.
 *
 * <p>{@link #coerceLuaToJava} adapts a Lua argument to a declared Java parameter
 * type (used when invoking setters and methods). Overload scoring lives in
 * {@link LuaCompatibilityScorer}, which rates how well one Lua value fits one
 * Java type so overload resolution can pick the best candidate.</p>
 */
final class LuaCoercion {
    private LuaCoercion() {}

    /**
     * @return the Java value, or {@code null} for Lua nil (callers map it to
     *         Java {@code null}, which reference-typed parameters accept)
     */
    static @Nullable Object coerceLuaToJava(@Nullable LuaValue luaVal, @NotNull Class<?> targetType) {
        if (luaVal == null || luaVal.isNil()) {
            return null;
        }
        if (targetType == Object.class) {
            return coerceToObject(luaVal);
        }
        Object scalar = coerceScalar(luaVal, targetType);
        if (scalar != null) {
            return scalar;
        }
        if (luaVal instanceof LuaUserdata userdata) {
            return userdata.getJavaInstance();
        }
        return LuaDataConverter.toJava(luaVal, targetType);
    }

    /** @return the converted scalar, or {@code null} for non-scalar types. */
    private static @Nullable Object coerceScalar(@NotNull LuaValue luaVal, @NotNull Class<?> targetType) {
        if (targetType == String.class) {
            return luaVal.toLuaString();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return luaVal.toBoolean();
        }
        return coerceNumber(luaVal, targetType);
    }

    /** @return the converted number, or {@code null} for non-numeric types. */
    private static @Nullable Object coerceNumber(@NotNull LuaValue luaVal, @NotNull Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            return (int) luaVal.toLong();
        }
        if (targetType == long.class || targetType == Long.class) {
            return (long) luaVal.toDouble();
        }
        if (targetType == double.class || targetType == Double.class) {
            return luaVal.toDouble();
        }
        if (targetType == float.class || targetType == Float.class) {
            return (float) luaVal.toDouble();
        }
        return null;
    }

    private static @NotNull Object coerceToObject(@NotNull LuaValue luaVal) {
        if (luaVal.isString()) {
            return luaVal.toLuaString();
        }
        if (luaVal.isBoolean()) {
            return luaVal.toBoolean();
        }
        if (luaVal.isNumber()) {
            double value = luaVal.toDouble();
            if (Math.rint(value) == value) {
                return (long) value;
            }
            return value;
        }
        if (luaVal instanceof LuaUserdata userdata) {
            return userdata.getJavaInstance();
        }
        return LuaDataConverter.toJava(luaVal, Object.class);
    }
}
