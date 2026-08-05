package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Hashmap-backed ILuaTable for unit tests.
 */
public class FakeTable implements ILuaTable {

    private final Map<String, ILuaValue> entries = new HashMap<>();
    private final Map<Integer, ILuaValue> indexed = new HashMap<>();

    @Override
    public void rawset(String key, ILuaValue value) {
        entries.put(key, value);
    }

    @Override
    public void rawset(String key, String value) {
        entries.put(key, new FakeValue(value));
    }

    @Override
    public void rawset(String key, double value) {
        entries.put(key, new FakeValue(value));
    }

    @Override
    public void rawset(String key, boolean value) {
        entries.put(key, new FakeValue(value));
    }

    @Override
    public void rawset(String key, ILuaFunction function) {
        entries.put(key, new FakeValue(function));
    }

    @Override
    public void rawset(String key, Object userdata) {
        entries.put(key, new FakeValue(userdata));
    }

    @Override
    public void rawset(int index, ILuaValue value) {
        indexed.put(index, value);
    }

    @Override
    public void rawset(int index, String value) {
        indexed.put(index, new FakeValue(value));
    }

    @Override
    public void rawset(int index, double value) {
        indexed.put(index, new FakeValue(value));
    }

    @Override
    public void rawset(int index, boolean value) {
        indexed.put(index, new FakeValue(value));
    }

    @Override
    public ILuaValue rawget(String key) {
        return entries.get(key);
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
        for (Map.Entry<String, ILuaValue> e : entries.entrySet()) {
            action.accept(new FakeValue(e.getKey()), e.getValue());
        }
    }

    @Override
    public void setMetatable(ILuaTable meta) {
    }

    @Override
    public Map<ILuaValue, ILuaValue> asMap() {
        Map<ILuaValue, ILuaValue> map = new HashMap<>();
        for (Map.Entry<String, ILuaValue> e : entries.entrySet()) {
            map.put(new FakeValue(e.getKey()), e.getValue());
        }
        return map;
    }

    @Override
    public String asString() {
        return "{table}";
    }

    @Override
    public int asInt() {
        return 0;
    }

    @Override
    public double asDouble() {
        return 0;
    }

    @Override
    public boolean asBoolean() {
        return false;
    }

    @Override
    public boolean isNil() {
        return false;
    }

    @Override
    public boolean isTable() {
        return true;
    }

    @Override
    public boolean isFunction() {
        return false;
    }

    @Override
    public boolean isNumber() {
        return false;
    }

    @Override
    public boolean isString() {
        return false;
    }

    @Override
    public ILuaTable asTable() {
        return this;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }
}
