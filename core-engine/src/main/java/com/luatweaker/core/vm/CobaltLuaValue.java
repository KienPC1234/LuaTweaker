package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaUserdata;
import org.squiddev.cobalt.LuaValue;

public class CobaltLuaValue implements ILuaValue {
    protected final LuaValue value;

    public CobaltLuaValue(LuaValue value) {
        this.value = value;
    }

    public LuaValue getCobaltValue() {
        return value;
    }

    @Override
    public String asString() {
        return value.toString();
    }

    @Override
    public int asInt() {
        return value.isNumber() ? value.toInteger() : 0;
    }

    @Override
    public double asDouble() {
        return value.isNumber() ? value.toDouble() : 0.0;
    }

    @Override
    public boolean asBoolean() {
        return value.toBoolean();
    }

    @Override
    public boolean isNil() {
        return value.isNil();
    }

    @Override
    public boolean isTable() {
        return value instanceof LuaTable;
    }

    @Override
    public ILuaTable asTable() {
        if (value instanceof LuaTable tbl) {
            return new CobaltLuaTable(tbl);
        }
        throw new IllegalStateException("Value is not a LuaTable: " + value);
    }

    @Override
    public Object toJavaObject() {
        if (value instanceof LuaUserdata u) {
            return u.instance;
        }
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CobaltLuaValue other) {
            return this.value.equals(other.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
