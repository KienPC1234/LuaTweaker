package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Hashmap-backed ILuaTable for unit tests: string and array entries, plus a fluent
 * {@code put} helper that accepts plain Java values (wrapped on write).
 */
public class FakeLuaTable extends FakeLuaStringKeyTable {

    private final Map<Integer, ILuaValue> indexed = new ConcurrentHashMap<>();

    public FakeLuaTable put(String key, Object value) {
        entries.put(key, FakeLuaTables.wrap(value));
        return this;
    }

    public FakeLuaTable put(int index, Object value) {
        indexed.put(index, FakeLuaTables.wrap(value));
        return this;
    }

    @Override
    public void rawset(int index, ILuaValue value) {
        indexed.put(index, value);
    }

    @Override
    public void rawset(int index, String value) {
        indexed.put(index, new FakeLuaValue(value));
    }

    @Override
    public void rawset(int index, double value) {
        indexed.put(index, new FakeLuaValue(value));
    }

    @Override
    public void rawset(int index, boolean value) {
        indexed.put(index, new FakeLuaValue(value));
    }

    @Override
    public ILuaValue rawget(int index) {
        return indexed.get(index);
    }

    @Override
    public int length() {
        return indexed.size();
    }

    @Override
    public void forEach(BiConsumer<ILuaValue, ILuaValue> action) {
        entries.forEach((key, value) -> action.accept(new FakeLuaValue(key), value));
    }

    @Override
    public void setMetatable(ILuaTable metatable) {
    }

    @Override
    public Map<ILuaValue, ILuaValue> asMap() {
        Map<ILuaValue, ILuaValue> map = new ConcurrentHashMap<>();
        entries.forEach((key, value) -> map.put(new FakeLuaValue(key), value));
        return map;
    }
}
