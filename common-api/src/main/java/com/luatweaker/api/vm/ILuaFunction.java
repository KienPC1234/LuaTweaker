package com.luatweaker.api.vm;

@FunctionalInterface
public interface ILuaFunction {
    ILuaValue invoke(ILuaValue[] args) throws Exception;
}
