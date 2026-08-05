package com.luatweaker.api.client;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.annotation.LuaDefault;

@LuaDoc(description = "Service for client-side visual and audio effects.")
public interface IClientEffectsService {
    @LuaDoc(
        description = "Spawns a particle in the world on the client side.",
        params = {"particleId: string", "x: number", "y: number", "z: number", "vx: number", "vy: number", "vz: number"},
        returnType = "void"
    )
    void spawnParticle(String particleId, double x, double y, double z, @LuaDefault("0.0") double vx, @LuaDefault("0.0") double vy, @LuaDefault("0.0") double vz);

    @LuaDoc(
        description = "Plays a sound on the client.",
        params = {"soundId: string", "volume: number", "pitch: number"},
        returnType = "void"
    )
    void playSound(String soundId, @LuaDefault("1.0") double volume, @LuaDefault("1.0") double pitch);

    @LuaDoc(
        description = "Flashes the screen with a specific hex color for a given duration.",
        params = {"hexColor: string", "duration: number"},
        returnType = "void"
    )
    void flashScreen(String hexColor, @LuaDefault("0.3") double duration);
}
