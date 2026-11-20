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
        return teleport(rawPlayerEntity, dimensionId, false);
    }

    private boolean teleport(@NotNull Object rawPlayerEntity, @NotNull String dimensionId,
                             boolean chunkMayBeGenerated) {
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

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!chunkMayBeGenerated && !target.hasChunk(chunkX, chunkZ)) {
            // Generating a chunk from inside a Lua callback deadlocks: this thread holds the
            // engine's execution gate while it waits for the worldgen worker, and the worker
            // needs that gate to run the Lua terrain callback. Server tasks run outside Lua,
            // so defer the teleport by one tick instead of blocking here.
            MinecraftServer server = target.getServer();
            server.execute(() -> teleport(rawPlayerEntity, dimensionId, true));
            return true;
        }
        // Level#getHeight returns the min build height for chunks that are not loaded yet, so
        // probing the spawn columns before generating them always reported "void" and the old
        // fallback teleported the player to the bottom of the world.
        target.getChunk(chunkX, chunkZ);
        BlockPos safe = findSafeSpawnColumn(target, x, z);
        if (safe == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Refusing to teleport " + player.getName().getString() + " to dimension '"
                            + dimensionId + "': no safe ground within 64 blocks of ("
                            + x + ", " + z + "). Check the dimension's spawn point/terrain generator.");
            return false;
        }
        int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING, safe.getX(), safe.getZ());
        player.teleportTo(target, safe.getX() + 0.5, y, safe.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Teleported " + player.getName().getString() + " to dimension '" + dimensionId
                        + "' at (" + safe.getX() + ", " + safe.getZ() + ")");
        return true;
    }

    /**
     * Scans outward in rings from (x, z) for the first column the player can actually stand on.
     * Only loaded chunks are inspected: {@code Level#getHeight} silently returns the build floor
     * for unloaded chunks, and the previous fallback then dropped the player into the void.
     *
     * @return the feet position, or {@code null} when no safe ground exists in range
     */
    private static @Nullable BlockPos findSafeSpawnColumn(ServerLevel level, int x, int z) {
        int minY = level.getMinBuildHeight();
        for (int ring = 0; ring <= 64; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int cx = x + dx;
                    int cz = z + dz;
                    if (!level.hasChunk(cx >> 4, cz >> 4)) continue;
                    int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
                    net.minecraft.world.level.block.state.BlockState ground =
                            level.getBlockState(new net.minecraft.core.BlockPos(cx, height - 1, cz));
                    net.minecraft.world.level.block.state.BlockState feet =
                            level.getBlockState(new net.minecraft.core.BlockPos(cx, height, cz));
                    net.minecraft.world.level.block.state.BlockState head =
                            level.getBlockState(new net.minecraft.core.BlockPos(cx, height + 1, cz));
                    boolean groundSolidAndDry = !ground.isAir() && ground.getFluidState().isEmpty();
                    if (!isSafeSpawnColumn(height, minY, groundSolidAndDry, feet.isAir(), head.isAir())) {
                        continue;
                    }
                    return new BlockPos(cx, height, cz);
                }
            }
        }
        return null;
    }

    /**
     * Pure policy so the "is this column safe to stand on" rule is testable without a world.
     *
     * @param surfaceY         first air block above the ground ({@code Level#getHeight} value)
     * @param minBuildHeight   dimension floor
     * @param groundSolidAndDry true when the block at {@code surfaceY - 1} is solid and not fluid
     * @param feetAir          free space at {@code surfaceY}
     * @param headAir          free space at {@code surfaceY + 1}
     */
    static boolean isSafeSpawnColumn(int surfaceY, int minBuildHeight,
                                     boolean groundSolidAndDry, boolean feetAir, boolean headAir) {
        // Above minY + 3 so a leftover bottom-layer artifact can never be treated as ground.
        return surfaceY > minBuildHeight + 3 && groundSolidAndDry && feetAir && headAir;
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

    @Override
    @Nullable
    public String getEntityDimensionId(@NotNull Object rawEntity) {
        if (rawEntity instanceof net.minecraft.world.entity.Entity entity) {
            return entity.level().dimension().location().toString();
        }
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "getEntityDimensionId requires a platform Entity, got: " + rawEntity);
        return null;
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
