package com.luatweaker.api.vm;

public interface ILuaValue {
    String asString();
    int asInt();
    double asDouble();
    boolean asBoolean();
    boolean isNil();
    boolean isTable();
    boolean isFunction();
    boolean isNumber();
    boolean isString();
    default boolean isBoolean() { return false; }
    default boolean isUserdata() { return false; }
    ILuaTable asTable();
    Object toJavaObject();
}
