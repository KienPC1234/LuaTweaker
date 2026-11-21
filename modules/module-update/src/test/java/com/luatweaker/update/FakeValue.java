package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.Objects;

/**
 * Wraps a plain Java value as an {@link ILuaValue} for unit tests. Values compare by the wrapped
 * object, so they can also serve as fake-table keys.
 */
final class FakeValue implements ILuaValue {

    static final FakeValue NIL = new FakeValue(null);

    private final Object value;

    FakeValue(Object value) {
        this.value = value;
    }

    Object getValue() {
        return value;
    }

    @Override
    public String asString() {
        return value != null ? String.valueOf(value) : "";
    }

    @Override
    public int asInt() {
        return value instanceof Number number ? number.intValue() : 0;
    }

    @Override
    public double asDouble() {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    @Override
    public boolean asBoolean() {
        return value instanceof Boolean bool && bool;
    }

    @Override
    public boolean isNil() {
        return value == null;
    }

    @Override
    public boolean isTable() {
        return value instanceof ILuaTable;
    }

    @Override
    public boolean isFunction() {
        return false;
    }

    @Override
    public boolean isNumber() {
        return value instanceof Number;
    }

    @Override
    public boolean isString() {
        return value instanceof String;
    }

    @Override
    public boolean isBoolean() {
        return value instanceof Boolean;
    }

    @Override
    public ILuaTable asTable() {
        return value instanceof ILuaTable table ? table : null;
    }

    @Override
    public Object toJavaObject() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FakeValue fake && Objects.equals(value, fake.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }
}
