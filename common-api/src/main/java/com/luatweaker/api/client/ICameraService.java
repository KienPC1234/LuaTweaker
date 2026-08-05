package com.luatweaker.api.client;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.annotation.LuaDefault;

@LuaDoc(description = "Service for camera manipulation.")
public interface ICameraService {
    @LuaDoc(
        description = "Shakes the camera with the given intensity and duration.",
        params = {"intensity: number", "duration: number"},
        returnType = "void"
    )
    void shake(@LuaDefault("1.0") double intensity, @LuaDefault("0.5") double duration);
}
