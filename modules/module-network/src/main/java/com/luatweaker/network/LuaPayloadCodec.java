package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Binary wire format for every Lua value that crosses the network.
 *
 * <p>Replaces the old "Lua table -> Gson JSON string -> UTF-8 bytes -> JSON parse -> Lua
 * table" path with one compact tagged stream. JSON repeated every key on every message and
 * allocated a {@code String} plus a full {@code JsonElement} tree per packet; this codec
 * writes the values directly and decodes straight into Lua tables.</p>
 *
 * <p>Round-trip guarantees:</p>
 * <ul>
 *   <li>dense tables ({@code 1..n}) stay Lua arrays ({@code #t} and {@code ipairs} work),</li>
 *   <li>string-keyed tables stay maps,</li>
 *   <li>integers keep their integer tag, so large ids do not drift,</li>
 *   <li>keys are stringified exactly like the previous JSON format did.</li>
 * </ul>
 *
 * <p>Everything that cannot cross the wire (functions, userdata, threads) fails loudly.</p>
 */
public final class LuaPayloadCodec {

    /** Bumped whenever the byte layout changes; a mismatched version is rejected, never guessed. */
    public static final byte VERSION = 1;

    /** Deepest table/array nesting accepted in either direction. */
    public static final int MAX_NESTING = 32;
    /** Largest number of arguments or collection entries in one message. */
    public static final int MAX_ELEMENTS = 65_536;
    /** Largest single string, in UTF-8 bytes. */
    public static final int MAX_STRING_BYTES = 1_048_576;
    /** Largest decoded message. Chunking splits anything above one frame. */
    public static final int MAX_MESSAGE_BYTES = 4_194_304;

    private static final byte TAG_NIL = 0;
    private static final byte TAG_FALSE = 1;
    private static final byte TAG_TRUE = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_DOUBLE = 4;
    private static final byte TAG_STRING = 5;
    private static final byte TAG_ARRAY = 6;
    private static final byte TAG_MAP = 7;

    private static final ILuaValue[] NO_ARGS = new ILuaValue[0];

    private LuaPayloadCodec() {
    }

    /**
     * Serializes a Lua argument list.
     *
     * @throws IllegalArgumentException when a value cannot cross the wire, the payload is
     *                                  nested too deeply, or it exceeds {@link #MAX_MESSAGE_BYTES}
     */
    public static byte @NotNull [] encodeArgs(@Nullable ILuaValue[] args) {
        ILuaValue[] values = args == null ? NO_ARGS : args;
        BinaryWriter out = new BinaryWriter();
        out.writeByte(VERSION);
        out.writeVarInt(values.length);
        for (ILuaValue value : values) {
            writeValue(out, value, 0);
            if (out.size() > MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException("Network payload is larger than "
                        + MAX_MESSAGE_BYTES + " bytes");
            }
        }
        return out.toByteArray();
    }

    /**
     * Decodes a payload produced by {@link #encodeArgs(ILuaValue[])}.
     *
     * @throws IllegalArgumentException on truncation, trailing bytes, an unknown version or
     *                                  a malformed length
     */
    public static ILuaValue @NotNull [] decodeArgs(@NotNull ILuaEngine engine, byte @NotNull [] data) {
        BinaryReader in = new BinaryReader(data, 0, data.length);
        int version = in.readByte();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported network payload version " + version);
        }
        int count = in.readPositiveVarInt("argument count");
        if (count > MAX_ELEMENTS) {
            throw new IllegalArgumentException("Argument count " + count + " exceeds " + MAX_ELEMENTS);
        }
        if (count > in.remaining()) {
            throw new IllegalArgumentException("Argument count " + count + " exceeds the payload size");
        }
        ILuaValue[] values = new ILuaValue[count];
        for (int i = 0; i < count; i++) {
            values[i] = readValue(engine, in, 0);
        }
        in.requireFinished();
        return values;
    }

    private static void writeValue(@NotNull BinaryWriter out, @Nullable ILuaValue value, int depth) {
        if (depth > MAX_NESTING) {
            throw new IllegalArgumentException(
                    "Network payload nesting is too deep (max " + MAX_NESTING + " levels)");
        }
        if (value == null || value.isNil()) {
            out.writeByte(TAG_NIL);
            return;
        }
        if (value.isTable()) {
            writeTable(out, value.asTable(), depth);
            return;
        }
        if (value.isBoolean()) {
            out.writeByte(value.asBoolean() ? TAG_TRUE : TAG_FALSE);
            return;
        }
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
            out.writeByte(flag ? TAG_TRUE : TAG_FALSE);
            return;
        }
        throw new IllegalArgumentException(
                "Network payloads only support booleans, numbers, strings and tables of those; got "
                        + value.getClass().getSimpleName());
    }

    private static void writeTable(@NotNull BinaryWriter out, @NotNull ILuaTable table, int depth) {
        int length = table.length();
        Map<ILuaValue, ILuaValue> entries = table.asMap();
        boolean isArray = length > 0 && entries.size() == length;
        if (isArray) {
            for (ILuaValue key : entries.keySet()) {
                if (!key.isNumber() || key.asInt() < 1 || key.asInt() > length) {
                    isArray = false;
                    break;
                }
            }
        }
        if (isArray) {
            if (length > MAX_ELEMENTS) {
                throw new IllegalArgumentException("Array has " + length + " entries (max " + MAX_ELEMENTS + ")");
            }
            out.writeByte(TAG_ARRAY);
            out.writeVarInt(length);
            for (int i = 1; i <= length; i++) {
                writeValue(out, table.rawget(i), depth + 1);
            }
            return;
        }
        if (entries.size() > MAX_ELEMENTS) {
            throw new IllegalArgumentException("Table has " + entries.size() + " entries (max " + MAX_ELEMENTS + ")");
        }
        out.writeByte(TAG_MAP);
        out.writeVarInt(entries.size());
        for (Map.Entry<ILuaValue, ILuaValue> entry : entries.entrySet()) {
            out.writeUtf8(entry.getKey().asString(), MAX_STRING_BYTES);
            writeValue(out, entry.getValue(), depth + 1);
        }
    }

    private static void writeString(@NotNull BinaryWriter out, @NotNull String text) {
        out.writeByte(TAG_STRING);
        out.writeUtf8(text, MAX_STRING_BYTES);
    }

    private static void writeNumber(@NotNull BinaryWriter out, @NotNull Number number) {
        double value = number.doubleValue();
        boolean isFinite = !Double.isNaN(value) && !Double.isInfinite(value);
        // -0.0 must stay a double: casting it to int would silently lose the sign.
        boolean isNegativeZero = value == 0.0 && Double.doubleToRawLongBits(value) != 0L;
        if (isFinite && !isNegativeZero && value == Math.rint(value)
                && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            out.writeByte(TAG_INT);
            out.writeZigZagInt((int) value);
            return;
        }
        out.writeByte(TAG_DOUBLE);
        out.writeDouble(value);
    }

    private static ILuaValue readValue(@NotNull ILuaEngine engine, @NotNull BinaryReader in, int depth) {
        if (depth > MAX_NESTING) {
            throw new IllegalArgumentException(
                    "Network payload nesting is too deep (max " + MAX_NESTING + " levels)");
        }
        int tag = in.readByte();
        switch (tag) {
            case TAG_NIL:
                return engine.nilValue();
            case TAG_FALSE:
                return engine.wrapBoolean(false);
            case TAG_TRUE:
                return engine.wrapBoolean(true);
            case TAG_INT:
                return engine.wrapNumber(in.readZigZagInt());
            case TAG_DOUBLE:
                return engine.wrapNumber(in.readDouble());
            case TAG_STRING:
                return engine.wrapString(in.readUtf8(MAX_STRING_BYTES));
            case TAG_ARRAY: {
                int count = readCollectionSize(in, "array");
                ILuaTable table = engine.createTable();
                for (int i = 1; i <= count; i++) {
                    table.rawset(i, readValue(engine, in, depth + 1));
                }
                return table;
            }
            case TAG_MAP: {
                int count = readCollectionSize(in, "map");
                ILuaTable table = engine.createTable();
                for (int i = 0; i < count; i++) {
                    String key = in.readUtf8(MAX_STRING_BYTES);
                    table.rawset(key, readValue(engine, in, depth + 1));
                }
                return table;
            }
            default:
                throw new IllegalArgumentException("Unknown network value tag " + tag);
        }
    }

    private static int readCollectionSize(@NotNull BinaryReader in, @NotNull String kind) {
        int count = in.readPositiveVarInt(kind + " size");
        if (count > MAX_ELEMENTS) {
            throw new IllegalArgumentException(kind + " size " + count + " exceeds " + MAX_ELEMENTS);
        }
        // Every entry costs at least one tag byte, so this also rejects absurd counts early.
        if (count > in.remaining()) {
            throw new IllegalArgumentException(kind + " size " + count + " exceeds the payload size");
        }
        return count;
    }
}
