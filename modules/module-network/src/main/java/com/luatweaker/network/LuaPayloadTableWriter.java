package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Handles table and array wire encoding for {@link LuaPayloadEncoder}.
 */
final class LuaPayloadTableWriter {

    private LuaPayloadTableWriter() {}

    static void writeTableBody(@NotNull BinaryWriter out, @NotNull ILuaTable table, int depth,
                               @NotNull CycleGuard guard) {
        int length = table.length();
        Map<ILuaValue, ILuaValue> entries = table.asMap();
        if (isDenseArray(length, entries)) {
            if (length > LuaPayloadCodec.MAX_ELEMENTS) {
                throw new IllegalArgumentException("Array has " + length
                        + " entries (max " + LuaPayloadCodec.MAX_ELEMENTS + ")");
            }
            out.writeByte(LuaPayloadCodec.TAG_ARRAY);
            out.writeVarInt(length);
            writeArrayEntries(out, table, length, depth, guard);
            return;
        }
        if (entries.size() > LuaPayloadCodec.MAX_ELEMENTS) {
            throw new IllegalArgumentException("Table has " + entries.size()
                    + " entries (max " + LuaPayloadCodec.MAX_ELEMENTS + ")");
        }
        out.writeByte(LuaPayloadCodec.TAG_MAP);
        out.writeVarInt(entries.size());
        writeMapEntries(out, entries, depth, guard);
    }

    private static boolean isDenseArray(int length, Map<ILuaValue, ILuaValue> entries) {
        if (length <= 0 || entries.size() != length) {
            return false;
        }
        for (ILuaValue key : entries.keySet()) {
            if (!key.isNumber() || key.asInt() < 1 || key.asInt() > length) {
                return false;
            }
        }
        return true;
    }

    private static void writeArrayEntries(@NotNull BinaryWriter out, @NotNull ILuaTable table,
                                          int length, int depth, @NotNull CycleGuard guard) {
        for (int i = 1; i <= length; i++) {
            LuaPayloadEncoder.writeValue(out, table.rawget(i), depth + 1, guard);
        }
    }

    private static void writeMapEntries(@NotNull BinaryWriter out,
                                        Map<ILuaValue, ILuaValue> entries, int depth,
                                        @NotNull CycleGuard guard) {
        for (Map.Entry<ILuaValue, ILuaValue> entry : entries.entrySet()) {
            out.writeUtf8(entry.getKey().asString(), LuaPayloadCodec.MAX_STRING_BYTES);
            LuaPayloadEncoder.writeValue(out, entry.getValue(), depth + 1, guard);
        }
    }
}
