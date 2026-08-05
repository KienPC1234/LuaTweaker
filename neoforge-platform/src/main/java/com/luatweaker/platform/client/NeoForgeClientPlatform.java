package com.luatweaker.platform.client;

import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class NeoForgeClientPlatform implements IPlatformClient {

    @Override
    public void spawnParticle(String particleId, double x, double y, double z, double vx, double vy, double vz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        try {
            ResourceLocation loc = ResourceLocation.parse(particleId);
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(loc);
            if (type instanceof ParticleOptions options) {
                mc.level.addParticle(options, x, y, z, vx, vy, vz);
            } else {
                LuaTweakerLog.get().warn(LogStage.SYSTEM, "Invalid particle type or not ParticleOptions: " + particleId);
            }
        } catch (Exception e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Failed to spawn particle '" + particleId + "': " + e.getMessage());
        }
    }

    @Override
    public void playSound(String soundId, double volume, double pitch) {
        Minecraft mc = Minecraft.getInstance();
        try {
            ResourceLocation loc = ResourceLocation.parse(soundId);
            SoundEvent event = SoundEvent.createVariableRangeEvent(loc);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, (float) pitch, (float) volume));
        } catch (Exception e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Failed to play sound '" + soundId + "': " + e.getMessage());
        }
    }

    @Override
    public int getGuiScaledWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public int getGuiScaledHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
