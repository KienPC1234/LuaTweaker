package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

/**
 * Wraps a plain Java value as an ILuaValue for unit tests.
 */
public class FakeLuaValue implements ILuaValue {

    public static final FakeLuaValue NIL = new FakeLuaValue(null);

    private final Object value;

    public FakeLuaValue(Object value) {
        this.value = value;
    }

    @Override
    public String asString() {
        return value != null ? String.valueOf(value) : "";
    }

    @Override
    public int asInt() {
        return value instanceof Number n ? n.intValue() : 0;
    }

    @Override
    public double asDouble() {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    @Override
    public boolean asBoolean() {
        return value instanceof Boolean b && b;
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
        return value instanceof ILuaTable t ? t : null;
    }

    @Override
    public Object toJavaObject() {
        return value;
    }
}
