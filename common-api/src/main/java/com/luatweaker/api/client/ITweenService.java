package com.luatweaker.api.client;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaTable;

@LuaDoc(description = "Service for interpolating values smoothly over time.")
public interface ITweenService {
    @LuaDoc(
        description = "Creates a new Tween object for interpolating the given instance's properties.",
        params = {"instance: table", "tweenInfo: table", "properties: table"},
        returnType = "table"
    )
    ILuaTable create(ILuaTable instance, ILuaTable tweenInfo, ILuaTable properties);
}
