package com.luatweaker.common.storage;

import com.luatweaker.api.pal.IPlatformStorage;
import net.minecraft.server.MinecraftServer;

public class MinecraftStoragePlatform implements IPlatformStorage {
    @Override
    public java.io.File getStorageDirectory() {
        MinecraftServer server = com.luatweaker.common.CommonServerHolder.get();
        if (server != null) {
            java.io.File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
            return new java.io.File(worldDir, "luatweaker/storage");
        }
        java.io.File gameDir = dev.architectury.platform.Platform.getGameFolder().toFile();
        return new java.io.File(gameDir, "luatweaker/storage");
    }
}
