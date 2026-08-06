package com.luatweaker.platform.dimension;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform implementation of dimension teleportation on NeoForge.
 */
public class NeoForgeDimensionPlatform implements IPlatformDimension {

    @Override
    public boolean teleportToDimension(@NotNull Object rawPlayerEntity, @NotNull String dimensionId) {
        if (!(rawPlayerEntity instanceof ServerPlayer player)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "teleportToDimension requires a ServerPlayer, got: " + rawPlayerEntity);
            return false;
        }
        ServerLevel target = resolveLevel(dimensionId);
        if (target == null) return false;

        int x;
        int z;
        com.luatweaker.dimension.DimensionServiceImpl.SpawnPoint spawnPoint = resolveSpawnPoint(dimensionId);
        if (spawnPoint != null) {
            x = spawnPoint.x();
            z = spawnPoint.z();
        } else {
            BlockPos spawn = target.getSharedSpawnPos();
            x = spawn.getX();
            z = spawn.getZ();
        }
        // Skyland-safe: find the nearest non-void column around the spawn point.
        BlockPos safe = findSafeSpawnColumn(target, x, z);
        int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING, safe.getX(), safe.getZ());
        player.teleportTo(target, safe.getX() + 0.5, y + 1, safe.getZ() + 0.5, player.getYRot(), player.getXRot());
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Teleported " + player.getName().getString() + " to dimension '" + dimensionId
                        + "' at (" + safe.getX() + ", " + safe.getZ() + ")");
        return true;
    }

    /**
     * Scans outward in rings from (x, z) for the first column with actual
     * ground (height > minBuildHeight + 1) that is NOT water/lava, so
     * teleports never drop players into the void of a skyland dimension or
     * under water. Falls back to (x, z) itself.
     */
    private static BlockPos findSafeSpawnColumn(ServerLevel level, int x, int z) {
        for (int ring = 0; ring <= 64; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int cx = x + dx;
                    int cz = z + dz;
                    int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
                    if (height <= level.getMinBuildHeight() + 1) continue; // skyland void column
                    // The player stands ABOVE the surface; if that spot is
                    // water/lava the teleport would drop them underwater.
                    net.minecraft.world.level.block.state.BlockState above =
                            level.getBlockState(new net.minecraft.core.BlockPos(cx, height + 1, cz));
                    if (!above.getFluidState().isEmpty()) continue;
                    return new BlockPos(cx, height, cz);
                }
            }
        }
        int fallbackY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return new BlockPos(x, fallbackY, z);
    }

    @Nullable
    private com.luatweaker.dimension.DimensionServiceImpl.SpawnPoint resolveSpawnPoint(String dimensionId) {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        if (service instanceof com.luatweaker.dimension.DimensionServiceImpl dim) {
            return dim.getSpawnPoint(dimensionId);
        }
        return null;
    }

    @Override
    @Nullable
    public Object getLevel(@NotNull String dimensionId) {
        return resolveLevel(dimensionId);
    }

    @Nullable
    private ServerLevel resolveLevel(String dimensionId) {
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        if (id == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Invalid dimension id for teleport: '" + dimensionId + "'");
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "No server running; cannot resolve dimension '" + dimensionId + "'");
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Dimension level '" + dimensionId + "' is not loaded");
        }
        return level;
    }
}
