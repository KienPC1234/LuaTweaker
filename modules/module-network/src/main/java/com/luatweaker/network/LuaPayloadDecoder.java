package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

/**
 * Binary decoding half of {@link LuaPayloadCodec}: the tagged wire stream back to Lua values.
 *
 * <p>Every failure is an {@link IllegalArgumentException}: the caller logs it and drops the
 * packet. A malformed payload must never surface as an index or allocation failure that can
 * tear the connection.</p>
 */
final class LuaPayloadDecoder {

    private LuaPayloadDecoder() {}

    static void readAll(@NotNull ILuaEngine engine, @NotNull BinaryReader in,
                        ILuaValue[] values, int depth) {
        for (int i = 0; i < values.length; i++) {
            values[i] = readValue(engine, in, depth);
        }
    }

    static ILuaValue readValue(@NotNull ILuaEngine engine, @NotNull BinaryReader in, int depth) {
        if (depth > LuaPayloadCodec.MAX_NESTING) {
            throw new IllegalArgumentException(
                    "Network payload nesting is too deep (max " + LuaPayloadCodec.MAX_NESTING + " levels)");
        }
        int tag = in.readByte();
        if (tag == LuaPayloadCodec.TAG_ARRAY || tag == LuaPayloadCodec.TAG_MAP) {
            return readCollection(engine, in, tag, depth);
        }
        return readScalar(engine, in, tag);
    }

    private static ILuaValue readScalar(@NotNull ILuaEngine engine, @NotNull BinaryReader in, int tag) {
        switch (tag) {
            case LuaPayloadCodec.TAG_NIL:
                return engine.nilValue();
            case LuaPayloadCodec.TAG_FALSE:
                return engine.wrapBoolean(false);
            case LuaPayloadCodec.TAG_TRUE:
                return engine.wrapBoolean(true);
            case LuaPayloadCodec.TAG_INT:
                return engine.wrapNumber(in.readZigZagInt());
            case LuaPayloadCodec.TAG_DOUBLE:
                return engine.wrapNumber(in.readDouble());
            case LuaPayloadCodec.TAG_STRING:
                return engine.wrapString(in.readUtf8(LuaPayloadCodec.MAX_STRING_BYTES));
            default:
                throw new IllegalArgumentException("Unknown network value tag " + tag);
        }
    }

    private static ILuaValue readCollection(@NotNull ILuaEngine engine, @NotNull BinaryReader in,
                                            int tag, int depth) {
        if (tag == LuaPayloadCodec.TAG_ARRAY) {
            int count = readCollectionSize(in, "array");
            ILuaTable table = engine.createTable();
            for (int i = 1; i <= count; i++) {
                table.rawset(i, readValue(engine, in, depth + 1));
            }
            return table;
        }
        int count = readCollectionSize(in, "map");
        ILuaTable table = engine.createTable();
        for (int i = 0; i < count; i++) {
            String key = in.readUtf8(LuaPayloadCodec.MAX_STRING_BYTES);
            table.rawset(key, readValue(engine, in, depth + 1));
        }
        return table;
    }

    private static int readCollectionSize(@NotNull BinaryReader in, @NotNull String kind) {
        int count = in.readPositiveVarInt(kind + " size");
        if (count > LuaPayloadCodec.MAX_ELEMENTS) {
            throw new IllegalArgumentException(kind + " size " + count
                    + " exceeds " + LuaPayloadCodec.MAX_ELEMENTS);
        }
        // Every entry costs at least one tag byte, so this also rejects absurd counts early.
        if (count > in.remaining()) {
            throw new IllegalArgumentException(kind + " size " + count + " exceeds the payload size");
        }
        return count;
    }
}
