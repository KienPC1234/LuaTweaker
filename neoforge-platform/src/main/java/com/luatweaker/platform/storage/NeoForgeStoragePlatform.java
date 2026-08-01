package com.luatweaker.platform.storage;

import com.luatweaker.api.pal.IPlatformStorage;
import net.minecraft.server.MinecraftServer;

public class NeoForgeStoragePlatform implements IPlatformStorage {
    @Override
    public java.io.File getStorageDirectory() {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            java.io.File worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
            return new java.io.File(worldDir, "luatweaker/storage");
        }
        java.io.File gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toFile();
        return new java.io.File(gameDir, "luatweaker/storage");
    }
}
