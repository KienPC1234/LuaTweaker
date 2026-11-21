package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Hash-backed {@link ILuaTable} for unit tests. Storage is split by key type: string keys live in
 * {@link FakeTableStringKeys}, integer keys in {@link FakeTable}, and the stateless value
 * coercion a Lua table needs comes from {@link FakeTableValueDefaults}.
 */
final class FakeTable extends FakeTableStringKeys implements FakeTableValueDefaults {

    private final Map<ILuaValue, ILuaValue> indexed = new ConcurrentHashMap<>();

    @Override
    public void rawset(int index, ILuaValue value) {
        indexed.put(new FakeValue(index), value);
    }

    @Override
    public void rawset(int index, String value) {
        indexed.put(new FakeValue(index), new FakeValue(value));
    }

    @Override
    public void rawset(int index, double value) {
        indexed.put(new FakeValue(index), new FakeValue(value));
    }

    @Override
    public void rawset(int index, boolean value) {
        indexed.put(new FakeValue(index), new FakeValue(value));
    }

    @Override
    public ILuaValue rawget(int index) {
        return indexed.get(new FakeValue(index));
    }

    @Override
    public int length() {
        return indexed.size();
    }

    @Override
    public void setMetatable(ILuaTable meta) {
        // The update tests never read __index/__newindex; the fake has no metatable support.
    }
}

/** String-key half of the fake table; {@link FakeTable} adds the integer-key half. */
class FakeTableStringKeys {

    protected final Map<ILuaValue, ILuaValue> entries = new ConcurrentHashMap<>();

    public void rawset(String key, ILuaValue value) {
        entries.put(new FakeValue(key), value);
    }

    public void rawset(String key, String value) {
        entries.put(new FakeValue(key), new FakeValue(value));
    }

    public void rawset(String key, double value) {
        entries.put(new FakeValue(key), new FakeValue(value));
    }

    public void rawset(String key, boolean value) {
        entries.put(new FakeValue(key), new FakeValue(value));
    }

    public void rawset(String key, ILuaFunction function) {
        entries.put(new FakeValue(key), new FakeValue(function));
    }

    public void rawset(String key, Object userdata) {
        entries.put(new FakeValue(key), new FakeValue(userdata));
    }

    public ILuaValue rawget(String key) {
        return entries.get(new FakeValue(key));
    }

    public void forEach(BiConsumer<ILuaValue, ILuaValue> action) {
        entries.forEach(action);
    }

    public Map<ILuaValue, ILuaValue> asMap() {
        return Map.copyOf(entries);
    }
}

/**
 * Lua value coercion for the fake table. Scripts never run, so a table is never a number, boolean
 * or function: those coercions return the Lua-side neutral value instead of inventing data.
 */
interface FakeTableValueDefaults extends ILuaTable {

    @Override
    default String asString() {
        return "{table}";
    }

    @Override
    default int asInt() {
        return 0;
    }

    @Override
    default double asDouble() {
        return 0;
    }

    @Override
    default boolean asBoolean() {
        return false;
    }

    @Override
    default boolean isNil() {
        return false;
    }

    @Override
    default boolean isTable() {
        return true;
    }

    @Override
    default boolean isFunction() {
        return false;
    }

    @Override
    default boolean isNumber() {
        return false;
    }

    @Override
    default boolean isString() {
        return false;
    }

    @Override
    default ILuaTable asTable() {
        return this;
    }

    @Override
    default Object toJavaObject() {
        return this;
    }
}
