package com.luatweaker.platform.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.platform.sound.SoundHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class NeoForgeClientPlatform implements IPlatformClient {

    /**
     * Safety cap for looping sounds. A script that starts a loop every tick would
     * otherwise grow this map forever; the oldest handle is stopped and evicted.
     */
    private static final int MAX_TRACKED_LOOPING_SOUNDS = 256;

    private final AtomicLong nextSoundHandle = new AtomicLong(1L);
    private final Map<Long, LuaSoundInstance> loopingSounds = new ConcurrentHashMap<>();
    private boolean capWarningLogged;

    @Override
    public void spawnParticle(String particleId, double x, double y, double z, double vx, double vy, double vz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ResourceLocation location = parseParticleId(particleId);
        if (location == null) {
            return;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(location);
        if (!(type instanceof net.minecraft.core.particles.ParticleOptions options)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Particle] '" + particleId + "' cannot be spawned by id because it needs"
                            + " particle options (dust/block/item/vibration style particles)");
            return;
        }
        mc.level.addParticle(options, x, y, z, vx, vy, vz);
    }

    private static @org.jetbrains.annotations.Nullable ResourceLocation parseParticleId(String particleId) {
        if (particleId == null || particleId.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "[Particle] empty particle id");
            return null;
        }
        String trimmed = particleId.trim();
        ResourceLocation location = trimmed.contains(":")
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.tryParse("minecraft:" + trimmed);
        if (location == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "[Particle] malformed particle id '" + particleId + "'");
        }
        return location;
    }

    @Override
    public long playSound(String soundId, double volume, double pitch, String source, boolean loop) {
        return startSound(soundId, null, volume, pitch, source, loop, SoundSource.MASTER);
    }

    @Override
    public long playSoundAt(String soundId, double x, double y, double z,
                            double volume, double pitch, String source, boolean loop) {
        return startSound(soundId, new double[] { x, y, z }, volume, pitch, source, loop, SoundSource.NEUTRAL);
    }

    private long startSound(String soundId, double[] position, double volume, double pitch,
                            String source, boolean loop, SoundSource fallbackSource) {
        ResourceLocation location = SoundHelper.parseId(soundId);
        if (location == null) {
            return 0L;
        }
        SoundSource category = SoundHelper.parseSource(source, fallbackSource);
        float safeVolume = SoundHelper.volume(volume);
        float safePitch = SoundHelper.pitch(pitch);
        LuaSoundInstance instance = position == null
                ? LuaSoundInstance.ui(location, category, safeVolume, safePitch, loop)
                : LuaSoundInstance.world(location, category, safeVolume, safePitch, loop,
                        position[0], position[1], position[2]);

        long handle = loop ? registerLooping(instance) : 0L;

        Minecraft mc = Minecraft.getInstance();
        // SoundManager is client-thread only; execute() runs inline on that thread and
        // is queued when Lua runs on the integrated server thread.
        mc.execute(() -> {
            if (mc.getSoundManager().getSoundEvent(location) == null) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Sound] no sounds.json entry for '" + soundId + "'");
                if (handle != 0L) {
                    loopingSounds.remove(handle);
                }
                return;
            }
            mc.getSoundManager().play(instance);
        });
        return handle;
    }

    private long registerLooping(LuaSoundInstance instance) {
        if (loopingSounds.size() >= MAX_TRACKED_LOOPING_SOUNDS) {
            Long oldest = loopingSounds.keySet().stream().min(Long::compare).orElse(null);
            if (oldest != null) {
                stopSound(oldest);
            }
            if (!capWarningLogged) {
                capWarningLogged = true;
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Sound] more than " + MAX_TRACKED_LOOPING_SOUNDS
                                + " looping sounds: stopping the oldest handle (call StopSound when done)");
            }
        }
        long handle = nextSoundHandle.getAndIncrement();
        loopingSounds.put(handle, instance);
        return handle;
    }

    @Override
    public boolean stopSound(long handle) {
        LuaSoundInstance instance = loopingSounds.remove(handle);
        if (instance == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getSoundManager().stop(instance));
        return true;
    }

    @Override
    public void stopSounds(String soundId, String source) {
        ResourceLocation location = SoundHelper.parseId(soundId);
        if (location == null) {
            return;
        }
        SoundSource category = SoundHelper.parseSource(source, SoundSource.MASTER);
        loopingSounds.values().removeIf(sound ->
                sound.getLocation().equals(location) && sound.getSource() == category);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getSoundManager().stop(location, category));
    }

    @Override
    public int stopAllSounds() {
        int dropped = loopingSounds.size();
        loopingSounds.clear();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.getSoundManager().stop());
        return dropped;
    }

    @Override
    public int getActiveSoundCount() {
        return loopingSounds.size();
    }

    @Override
    public int getGuiScaledWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public int getGuiScaledHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    @Override
    public void openScreen(String screenId, String title,
                           @org.jetbrains.annotations.Nullable com.luatweaker.api.vm.ILuaTable screenTable,
                           @org.jetbrains.annotations.Nullable com.luatweaker.api.vm.ILuaEngine engine,
                           boolean pauseGame) {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            net.minecraft.network.chat.Component titleComp = net.minecraft.network.chat.Component.literal(title != null ? title : "");
            mc.setScreen(new LuaCanvasScreen(screenId, titleComp, engine, screenTable, pauseGame));
        });
    }

    @Override
    public void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            if (mc.screen instanceof LuaCanvasScreen) {
                mc.setScreen(null);
            }
        });
    }

    @Override
    public boolean isScreenOpen() {
        return Minecraft.getInstance().screen instanceof LuaCanvasScreen;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public com.luatweaker.api.entity.IPlayer getLocalPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return new com.luatweaker.platform.entity.NeoForgePlayerWrapper(mc.player);
        }
        return null;
    }

    @Override
    public void handleClientSyncData(String dataJson) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || dataJson == null || dataJson.isBlank()) {
            return;
        }
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(dataJson).getAsJsonObject();
            if (!obj.has("key") || !obj.has("val") || !obj.has("type")) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Ignoring malformed client sync payload (missing key/val/type): " + dataJson);
                return;
            }
            String key = obj.get("key").getAsString();
            String val = obj.get("val").getAsString();
            String type = obj.get("type").getAsString();
            net.minecraft.nbt.CompoundTag tag = mc.player.getPersistentData();
            if ("null".equals(type) || "null".equals(val)) {
                tag.remove(key);
            } else if ("bool".equals(type)) {
                tag.putBoolean(key, Boolean.parseBoolean(val));
            } else if ("num".equals(type)) {
                tag.putDouble(key, Double.parseDouble(val));
            } else {
                tag.putString(key, val);
            }
        } catch (com.google.gson.JsonParseException | IllegalStateException | NumberFormatException
                 | UnsupportedOperationException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed to apply synced data (" + dataJson + "): " + e.getMessage());
        }
    }
}
