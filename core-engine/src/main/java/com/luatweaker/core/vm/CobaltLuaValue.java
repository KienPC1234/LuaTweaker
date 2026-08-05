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
        if (value instanceof org.squiddev.cobalt.LuaString) {
            byte[] bytes = value.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
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
    public boolean isFunction() {
        return value instanceof org.squiddev.cobalt.function.LuaFunction;
    }

    @Override
    public boolean isNumber() {
        return value.isNumber();
    }

    @Override
    public boolean isString() {
        return value.isString();
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
        if (value instanceof org.squiddev.cobalt.LuaInteger i) {
            return i.toInteger();
        }
        if (value instanceof org.squiddev.cobalt.LuaDouble d) {
            return d.toDouble();
        }
        if (value instanceof org.squiddev.cobalt.LuaBoolean b) {
            return b.toBoolean();
        }
        if (value instanceof org.squiddev.cobalt.LuaString s) {
            return s.toString();
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
