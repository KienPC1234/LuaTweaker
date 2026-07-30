package com.luatweaker.api.vm;

import java.io.File;

public interface ILuaEngine {
    void registerService(String name, ILuaTable service);
    void registerGlobal(String name, ILuaValue value);
    void registerGlobal(String name, ILuaFunction function);
    void executeScript(File file, String context);
    
    ILuaTable createTable();
    ILuaValue wrapUserdata(Object userdata);
    ILuaValue wrapString(String value);
    ILuaValue wrapNumber(double value);
    ILuaValue wrapBoolean(boolean value);
    ILuaValue nilValue();
}
