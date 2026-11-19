package com.luatweaker.api.client;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.annotation.LuaDefault;

@LuaDoc(description = "Service for client-side visual and audio effects.")
public interface IClientEffectsService {
    @LuaDoc(
        description = "Spawns a particle in the world on the client side. Only particles that are plain "
                    + "ParticleOptions (flame, heart, end_rod, ...) can be spawned by id; dust/block/item "
                    + "particles require options and are rejected with a warning.",
        params = {"particleId: string", "x: number", "y: number", "z: number",
                  "vx: number", "vy: number", "vz: number"},
        returnType = "void"
    )
    void spawnParticle(String particleId, double x, double y, double z, @LuaDefault("0.0") double vx, @LuaDefault("0.0") double vy, @LuaDefault("0.0") double vz);

    @LuaDoc(
        description = "Plays a non-positional (UI) sound. Returns a handle when loop=true (0 otherwise) "
                    + "which must be passed to StopSound. source is a Minecraft sound category: "
                    + "MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE.",
        params = {"soundId: string", "[volume: number]", "[pitch: number]", "[source: string]", "[loop: boolean]"},
        returnType = "integer"
    )
    long playSound(String soundId,
                   @LuaDefault("1.0") double volume,
                   @LuaDefault("1.0") double pitch,
                   @LuaDefault("MASTER") String source,
                   @LuaDefault("false") boolean loop);

    @LuaDoc(
        description = "Plays a positional world sound with distance attenuation. Returns a handle when "
                    + "loop=true (0 otherwise) which must be passed to StopSound.",
        params = {"soundId: string", "x: number", "y: number", "z: number",
                  "[volume: number]", "[pitch: number]", "[source: string]", "[loop: boolean]"},
        returnType = "integer"
    )
    long playSoundAt(String soundId, double x, double y, double z,
                     @LuaDefault("1.0") double volume,
                     @LuaDefault("1.0") double pitch,
                     @LuaDefault("NEUTRAL") String source,
                     @LuaDefault("false") boolean loop);

    @LuaDoc(
        description = "Stops a looping sound started with PlaySound/PlaySoundAt. Returns false when the "
                    + "handle is already stopped, invalid, or refers to a one-shot sound.",
        params = {"handle: integer"},
        returnType = "boolean"
    )
    boolean stopSound(long handle);

    @LuaDoc(
        description = "Stops every playing instance of one sound id in one category.",
        params = {"soundId: string", "[source: string]"},
        returnType = "void"
    )
    void stopSounds(String soundId, @LuaDefault("MASTER") String source);

    @LuaDoc(
        description = "Stops every sound the game is playing (music, ambience and Lua sounds). "
                    + "Use sparingly; the music manager may start a new track. Returns the number of Lua handles dropped.",
        params = {},
        returnType = "integer"
    )
    int stopAllSounds();

    @LuaDoc(
        description = "Returns how many looping Lua sounds are still tracked (handles not yet stopped).",
        params = {},
        returnType = "integer"
    )
    int getActiveSoundCount();

    @LuaDoc(
        description = "Flashes the screen with a specific hex color for a given duration. "
                    + "Accepts '#RRGGBB', '#AARRGGBB' and the same values without the leading '#'.",
        params = {"hexColor: string", "[duration: number]"},
        returnType = "void"
    )
    void flashScreen(String hexColor, @LuaDefault("0.3") double duration);
}
