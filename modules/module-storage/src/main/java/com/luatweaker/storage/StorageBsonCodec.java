package com.luatweaker.storage;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts between the storable subset and BSON documents / Lua values.
 * The storable subset itself is validated by {@link StorageValueCodec}.
 */
final class StorageBsonCodec {

    private StorageBsonCodec() {}

    /**
     * BSON documents are ordered maps: the field order is part of the on-disk format, so a
     * ConcurrentHashMap (unordered) would change the produced bytes. Declared as the Map
     * interface so callers stay decoupled from the BSON implementation class.
     */
    @NotNull
    static Map<String, Object> newBsonDocument() {
        return new Document();
    }

    @NotNull
    static Map<String, Object> toDocument(Map<String, Object> values) {
        Map<String, Object> document = newBsonDocument();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            document.put(entry.getKey(), toBson(entry.getValue()));
        }
        return document;
    }

    static Object toBson(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> document = newBsonDocument();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                document.put(String.valueOf(entry.getKey()), toBson(entry.getValue()));
            }
            return document;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(toBson(item));
            }
            return out;
        }
        return value;
    }

    static Object convertToJavaObject(@NotNull ILuaEngine engine, Object raw) {
        if (raw == null || raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
            return raw;
        }
        if (raw instanceof Map<?, ?> map) {
            ILuaTable table = engine.createTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object convertedValue = convertToJavaObject(engine, entry.getValue());
                ILuaValue val = wrapObject(engine, convertedValue);
                table.rawset(String.valueOf(entry.getKey()), val);
            }
            return table;
        }
        if (raw instanceof List<?> list) {
            ILuaTable table = engine.createTable();
            int index = 1;
            for (Object item : list) {
                table.rawset(index++, wrapObject(engine, convertToJavaObject(engine, item)));
            }
            return table;
        }
        return raw;
    }

    @NotNull
    static ILuaValue wrapObject(@NotNull ILuaEngine engine, Object obj) {
        if (obj == null) {
            return engine.nilValue();
        }
        if (obj instanceof ILuaValue lv) {
            return lv;
        }
        if (obj instanceof String s) {
            return engine.wrapString(s);
        }
        if (obj instanceof Number n) {
            return engine.wrapNumber(n.doubleValue());
        }
        if (obj instanceof Boolean b) {
            return engine.wrapBoolean(b);
        }
        return engine.wrapUserdata(obj);
    }
}
