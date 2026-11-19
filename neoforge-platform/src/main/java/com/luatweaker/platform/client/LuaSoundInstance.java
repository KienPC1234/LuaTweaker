package com.luatweaker.platform.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

/**
 * Sound instance for Lua-driven playback.
 *
 * <p>{@link net.minecraft.client.resources.sounds.SimpleSoundInstance#forUI} is not
 * sufficient here: it hardcodes {@link SoundSource#MASTER}, is non-positional and
 * cannot loop. This subclass exposes those three knobs while reusing Minecraft's
 * own resolution/attenuation logic.</p>
 */
final class LuaSoundInstance extends AbstractSoundInstance {

    private LuaSoundInstance(@org.jetbrains.annotations.NotNull ResourceLocation location,
                             @org.jetbrains.annotations.NotNull SoundSource source,
                             float volume, float pitch, boolean loop,
                             boolean relative, double x, double y, double z) {
        super(location, source, SoundInstance.createUnseededRandom());
        this.volume = volume;
        this.pitch = pitch;
        this.looping = loop;
        this.relative = relative;
        this.x = x;
        this.y = y;
        this.z = z;
        this.attenuation = relative ? Attenuation.NONE : Attenuation.LINEAR;
    }

    /** Non-positional UI sound (no panning, no distance falloff). */
    static LuaSoundInstance ui(@org.jetbrains.annotations.NotNull ResourceLocation location,
                               @org.jetbrains.annotations.NotNull SoundSource source,
                               float volume, float pitch, boolean loop) {
        return new LuaSoundInstance(location, source, volume, pitch, loop, true, 0.0, 0.0, 0.0);
    }

    /** Positional world sound with linear distance attenuation. */
    static LuaSoundInstance world(@org.jetbrains.annotations.NotNull ResourceLocation location,
                                  @org.jetbrains.annotations.NotNull SoundSource source,
                                  float volume, float pitch, boolean loop,
                                  double x, double y, double z) {
        return new LuaSoundInstance(location, source, volume, pitch, loop, false, x, y, z);
    }
}
