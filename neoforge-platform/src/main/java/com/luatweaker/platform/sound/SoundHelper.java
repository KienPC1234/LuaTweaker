package com.luatweaker.platform.sound;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ResourceLocationException;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * Single place that turns Lua-facing sound primitives into Minecraft values.
 *
 * <p>Every caller (entity, player, world, client effects) must use this class so the
 * same input behaves the same way everywhere: unnamespaced ids resolve to
 * {@code minecraft:}, malformed ids and unknown categories are reported instead of
 * throwing, and non-positive pitch is raised to an audible minimum.</p>
 */
public final class SoundHelper {

    /** OpenAL rejects a zero playback rate, so pitch is never allowed to reach 0. */
    public static final float MIN_PITCH = 0.01F;

    private SoundHelper() {
    }

    /**
     * Parses a sound id, defaulting the namespace to {@code minecraft}.
     *
     * @return the id, or {@code null} when the input is empty/malformed (a warning is logged)
     */
    public static @Nullable ResourceLocation parseId(@Nullable String soundId) {
        if (soundId == null || soundId.isBlank()) {
            warn("empty sound id");
            return null;
        }
        String trimmed = soundId.trim();
        try {
            return trimmed.contains(":")
                    ? ResourceLocation.parse(trimmed)
                    : ResourceLocation.fromNamespaceAndPath("minecraft", trimmed);
        } catch (ResourceLocationException e) {
            warn("malformed sound id '" + soundId + "': " + e.getMessage());
            return null;
        }
    }

    /** Resolves a registered sound event, warning when the id is not in the registry. */
    public static @Nullable SoundEvent registered(@Nullable ResourceLocation id, @NotNull String context) {
        if (id == null) {
            return null;
        }
        Optional<SoundEvent> event = BuiltInRegistries.SOUND_EVENT.getOptional(id);
        if (event.isEmpty()) {
            warn("unknown sound event '" + id + "' (" + context + ")");
            return null;
        }
        return event.get();
    }

    /**
     * Parses a sound category. Accepts the enum name in any case ({@code PLAYERS},
     * {@code players}) and falls back to {@code fallback} with a warning.
     */
    public static @NotNull SoundSource parseSource(@Nullable String source, @NotNull SoundSource fallback) {
        if (source == null || source.isBlank()) {
            return fallback;
        }
        try {
            return SoundSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn("unknown sound source '" + source + "'; known values: " + knownSources()
                    + " (using " + fallback.getName() + ")");
            return fallback;
        }
    }

    /** Negative volume mutes; Minecraft itself does not guard against it. */
    public static float volume(double volume) {
        return (float) Math.max(0.0, volume);
    }

    /** Keeps pitch strictly positive so the audio channel always has a valid rate. */
    public static float pitch(double pitch) {
        return (float) Math.max(MIN_PITCH, pitch);
    }

    private static @NotNull String knownSources() {
        StringBuilder builder = new StringBuilder();
        for (SoundSource value : SoundSource.values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(value.name());
        }
        return builder.toString();
    }

    private static void warn(@NotNull String message) {
        LuaTweakerLog.get().warn(LogStage.SYSTEM, "[Sound] " + message);
    }
}
