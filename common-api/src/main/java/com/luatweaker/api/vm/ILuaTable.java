package com.luatweaker.api.vm;

public interface ILuaTable extends ILuaValue {
    void rawset(String key, ILuaValue value);
    void rawset(String key, String value);
    void rawset(String key, double value);
    void rawset(String key, boolean value);
    void rawset(String key, ILuaFunction function);
    void rawset(String key, Object userdata);
    
    void rawset(int index, ILuaValue value);
    void rawset(int index, String value);
    void rawset(int index, double value);
    void rawset(int index, boolean value);
    
    ILuaValue rawget(String key);
    ILuaValue rawget(int index);
    
    int length();
    
    // For iteration
    java.util.Map<ILuaValue, ILuaValue> asMap();
}
