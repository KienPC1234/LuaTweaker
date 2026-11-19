package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaTable;
import org.luava.runtime.LuaUserdata;
import org.luava.runtime.LuaValue;

/**
 * Adapts a luava {@link LuaValue} to the engine-neutral {@link ILuaValue} SPI.
 *
 * <p>String conversion uses {@link LuaValue#toLuaString()} directly: luava already stores
 * strings as proper UTF-8, so no ISO-8859-1 re-decoding is required.
 */
public class LuavaLuaValue implements ILuaValue {
    protected final LuaValue value;

    /**
     * Creates an adapter around a luava value.
     *
     * @param value underlying luava value; must not be {@code null}
     */
    public LuavaLuaValue(@NotNull LuaValue value) {
        this.value = value;
    }

    /**
     * Wraps a luava value, using {@link LuavaLuaTable} for tables so that Java bindings can
     * detect them with {@code instanceof ILuaTable}. Wrapping every value as a plain
     * {@code LuavaLuaValue} made every {@code instanceof ILuaTable} check on a Lua argument
     * silently fail (tags, vector operators, Instance parents, ...).
     *
     * @param value luava value to wrap
     * @return the SPI wrapper with the matching concrete type
     */
    public static @NotNull ILuaValue wrap(@NotNull LuaValue value) {
        if (value instanceof LuaTable table) {
            return new LuavaLuaTable(table);
        }
        return new LuavaLuaValue(value);
    }

    /**
     * Returns the underlying luava value.
     *
     * @return the wrapped luava value
     */
    public @NotNull LuaValue getLuavaValue() {
        return value;
    }

    @Override
    public @NotNull String asString() {
        return value.toLuaString();
    }

    @Override
    public int asInt() {
        if (value.isNumber()) {
            // long -> int wraps (Lua 5.1 behavior); double -> int saturates, which
            // would turn 0xFF00E676 into Integer.MAX_VALUE instead of its bit pattern.
            return (int) value.toLong();
        }
        Double parsed = parseNumericString();
        return parsed != null ? (int) parsed.longValue() : 0;
    }

    @Override
    public double asDouble() {
        if (value.isNumber()) {
            return value.toDouble();
        }
        // Lua 5.1 (Cobalt) coerced numeric strings to numbers; Lua 5.4 removed that.
        // The SPI keeps the old behavior so existing configs using numeric or hex
        // strings (e.g. "0xFF88FF" for a color) keep working.
        Double parsed = parseNumericString();
        return parsed != null ? parsed : 0.0;
    }

    @Override
    public boolean asBoolean() {
        return value.toBoolean();
    }

    @Override
    public boolean isNil() {
        return value.isNil();
    }

    @Override
    public boolean isTable() {
        return value.isTable();
    }

    @Override
    public boolean isFunction() {
        return value.isFunction();
    }

    @Override
    public boolean isBoolean() {
        return value.isBoolean();
    }

    @Override
    public boolean isNumber() {
        return value.isNumber() || parseNumericString() != null;
    }

    /** Returns the numeric value of a Lua string, accepting decimal and 0x hex forms. */
    private @Nullable Double parseNumericString() {
        if (!value.isString()) {
            return null;
        }
        String text = value.toLuaString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            if (text.startsWith("0x") || text.startsWith("0X")
                    || text.startsWith("-0x") || text.startsWith("-0X")) {
                return Long.decode(text).doubleValue();
            }
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean isString() {
        return value.isString();
    }

    @Override
    public @NotNull ILuaTable asTable() {
        if (value instanceof LuaTable tbl) {
            return new LuavaLuaTable(tbl);
        }
        throw new IllegalStateException("Value is not a LuaTable: " + value);
    }

    @Override
    public @Nullable Object toJavaObject() {
        if (value.isBoolean()) {
            return value.toBoolean();
        }
        if (value.isInteger()) {
            return value.toLong();
        }
        if (value.isFloat()) {
            return value.toDouble();
        }
        if (value.isString()) {
            return value.toLuaString();
        }
        if (value.isUserdata()) {
            return ((LuaUserdata) value).getJavaInstance();
        }
        return value;
    }

    @Override
    public @NotNull String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LuavaLuaValue other) {
            return value.equals(other.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
