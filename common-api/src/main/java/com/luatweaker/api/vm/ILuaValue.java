package com.luatweaker.api.vm;

public interface ILuaValue {
    String asString();
    int asInt();
    double asDouble();
    boolean asBoolean();
    boolean isNil();
    boolean isTable();
    ILuaTable asTable();
    Object toJavaObject();
}
