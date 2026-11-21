package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * String-keyed half of the fake Lua table.
 *
 * <p>Split from {@link FakeLuaTable} so each fake stays small and single-purpose:
 * this class owns string-key storage and the value coercions, while the subclass
 * owns array-index storage. Instances are never nil and always report themselves
 * as a table, which is what a test fixture constructed from plain Java values means.
 */
public abstract class FakeLuaStringKeyTable extends FakeLuaValue implements ILuaTable {

    protected final Map<String, ILuaValue> entries = new ConcurrentHashMap<>();

    protected FakeLuaStringKeyTable() {
        super(null);
    }

    @Override
    public void rawset(String key, ILuaValue value) {
        entries.put(key, value);
    }

    @Override
    public void rawset(String key, String value) {
        entries.put(key, new FakeLuaValue(value));
    }

    @Override
    public void rawset(String key, double value) {
        entries.put(key, new FakeLuaValue(value));
    }

    @Override
    public void rawset(String key, boolean value) {
        entries.put(key, new FakeLuaValue(value));
    }

    @Override
    public void rawset(String key, ILuaFunction function) {
        entries.put(key, new FakeLuaValue(function));
    }

    @Override
    public void rawset(String key, Object userdata) {
        entries.put(key, new FakeLuaValue(userdata));
    }

    @Override
    public ILuaValue rawget(String key) {
        return entries.get(key);
    }

    @Override
    public String asString() {
        return "{table}";
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
    public ILuaTable asTable() {
        return this;
    }

    @Override
    public Object toJavaObject() {
        return this;
    }
}
