package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Binary encoding half of {@link LuaPayloadCodec}: Lua values to the tagged wire stream.
 */
final class LuaPayloadEncoder {

    private LuaPayloadEncoder() {}

    static void writeAll(@NotNull BinaryWriter out, ILuaValue... values) {
        CycleGuard guard = new CycleGuard();
        for (ILuaValue value : values) {
            writeValue(out, value, 0, guard);
            if (out.size() > LuaPayloadCodec.MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException("Network payload is larger than "
                        + LuaPayloadCodec.MAX_MESSAGE_BYTES + " bytes");
            }
        }
    }

    static void writeValue(@NotNull BinaryWriter out, @Nullable ILuaValue value, int depth,
                           @NotNull CycleGuard guard) {
        if (depth > LuaPayloadCodec.MAX_NESTING) {
            throw new IllegalArgumentException(
                    "Network payload nesting is too deep (max " + LuaPayloadCodec.MAX_NESTING + " levels)");
        }
        if (value == null || value.isNil()) {
            out.writeByte(LuaPayloadCodec.TAG_NIL);
            return;
        }
        if (value.isTable()) {
            writeTable(out, value.asTable(), depth, guard);
            return;
        }
        if (value.isBoolean()) {
            out.writeByte(value.asBoolean() ? LuaPayloadCodec.TAG_TRUE : LuaPayloadCodec.TAG_FALSE);
            return;
        }
        writeScalar(out, value);
    }

    private static void writeScalar(@NotNull BinaryWriter out, @NotNull ILuaValue value) {
        Object raw = value.toJavaObject();
        if (raw instanceof String text) {
            writeString(out, text);
            return;
        }
        if (raw instanceof Number number) {
            writeNumber(out, number);
            return;
        }
        if (raw instanceof Boolean flag) {
            out.writeByte(flag ? LuaPayloadCodec.TAG_TRUE : LuaPayloadCodec.TAG_FALSE);
            return;
        }
        throw new IllegalArgumentException(
                "Network payloads only support booleans, numbers, strings and tables of those; got "
                        + value.getClass().getSimpleName());
    }

    static void writeTable(@NotNull BinaryWriter out, @NotNull ILuaTable table, int depth,
                           @NotNull CycleGuard guard) {
        Object scope = guard.enter(table);
        try {
            LuaPayloadTableWriter.writeTableBody(out, table, depth, guard);
        } finally {
            guard.exit(scope);
        }
    }

    private static void writeString(@NotNull BinaryWriter out, @NotNull String text) {
        out.writeByte(LuaPayloadCodec.TAG_STRING);
        out.writeUtf8(text, LuaPayloadCodec.MAX_STRING_BYTES);
    }

    private static void writeNumber(@NotNull BinaryWriter out, @NotNull Number number) {
        double value = number.doubleValue();
        boolean isFinite = !Double.isNaN(value) && !Double.isInfinite(value);
        // -0.0 must stay a double: casting it to int would silently lose the sign.
        boolean isNegativeZero = value == 0.0 && Double.doubleToRawLongBits(value) != 0L;
        if (isFinite && !isNegativeZero && value == Math.rint(value)
                && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            out.writeByte(LuaPayloadCodec.TAG_INT);
            out.writeZigZagInt((int) value);
            return;
        }
        out.writeByte(LuaPayloadCodec.TAG_DOUBLE);
        out.writeDouble(value);
    }
}
