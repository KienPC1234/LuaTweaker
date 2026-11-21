package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * List/table readers for structure config tables. Per-item validation lives
 * in small helpers so the loops stay readable and each error names the exact
 * key and index that failed.
 */
final class StructureListReaders {

    private StructureListReaders() {}

    @NotNull
    static List<String> stringList(ILuaTable t, String key) {
        ILuaValue value = t.rawget(key);
        if (value == null || value.isNil()) {
            return List.of();
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException(key + " must be a list of resource-location strings");
        }
        ILuaTable table = value.asTable();
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= table.length(); i++) {
            out.add(readListItem(key, table, i));
        }
        return out;
    }

    @NotNull
    private static String readListItem(String key, ILuaTable table, int index) {
        ILuaValue item = table.rawget(index);
        if (item == null || item.isNil() || !item.isString()) {
            throw new IllegalArgumentException(key + "[" + index + "] must be a resource-location string");
        }
        String id = item.asString();
        if (!StructureScalarReaders.isResourceLocation(id)) {
            throw new IllegalArgumentException(key + "[" + index + "] must be a valid resource location, got: " + id);
        }
        return id;
    }

    @NotNull
    static Map<String, String> stringMap(ILuaTable t, String key) {
        ILuaValue value = t.rawget(key);
        if (value == null || value.isNil()) {
            return Map.of();
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException(key + " must be a table of name -> loot-table id strings");
        }
        Map<String, String> out = new ConcurrentHashMap<>();
        for (Map.Entry<ILuaValue, ILuaValue> entry : value.asTable().asMap().entrySet()) {
            putMapEntry(key, out, entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static void putMapEntry(String key, Map<String, String> out,
                                    ILuaValue mapKey, ILuaValue mapValue) {
        String name = requireMapKey(key, mapKey);
        out.put(name, requireMapValue(key, name, mapValue));
    }

    @NotNull
    private static String requireMapKey(String key, ILuaValue mapKey) {
        if (mapKey == null || mapKey.isNil() || !mapKey.isString()) {
            throw new IllegalArgumentException(key + " keys must be strings");
        }
        return mapKey.asString();
    }

    @NotNull
    private static String requireMapValue(String key, String name, ILuaValue mapValue) {
        if (mapValue == null || mapValue.isNil() || !mapValue.isString()) {
            throw new IllegalArgumentException(key + "['" + name + "'] must be a string");
        }
        String tableId = mapValue.asString();
        if (!StructureScalarReaders.isResourceLocation(tableId)) {
            throw new IllegalArgumentException(key + "['" + name
                    + "'] must be a valid resource location, got: " + tableId);
        }
        return tableId;
    }
}
