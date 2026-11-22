package com.luatweaker.common.dimension;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformDimension;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.dimension.DimensionServiceImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import com.luatweaker.common.CommonServerHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform implementation of dimension teleportation on NeoForge.
 */
public class MinecraftDimensionPlatform implements IPlatformDimension {

    /** Search radius, in blocks, for a standable column around the dimension spawn. */
    private static final int SPAWN_SEARCH_RADIUS = 64;
    /** The feet column must sit at least this far above the dimension floor. */
    private static final int MIN_FLOOR_CLEARANCE = 3;
    private static final double BLOCK_CENTER = 0.5;

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
        return teleportOn(resolveLevel(dimensionId), player, dimensionId, chunkMayBeGenerated);
    }

    /**
     * Performs the teleport against an already-resolved level.
     *
     * <p>{@code ServerLevel} is taken as a parameter because it is a borrowed server-owned
     * singleton: never closed by this class, so it is deliberately not held in a local.</p>
     */
    private boolean teleportOn(@Nullable ServerLevel target, @NotNull ServerPlayer player,
                               @NotNull String dimensionId, boolean chunkMayBeGenerated) {
        if (target == null) {
            return false;
        }
        DimensionServiceImpl.SpawnPoint spawnPoint = resolveSpawnPoint(dimensionId);
        int x;
        int z;
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
            return deferTeleport(target, player, dimensionId);
        }
        return finishTeleport(target, player, dimensionId, x, z);
    }

    private boolean deferTeleport(@NotNull ServerLevel target, @NotNull ServerPlayer player,
                                  @NotNull String dimensionId) {
        if (target.getServer() == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Cannot defer teleport of " + player.getName().getString()
                            + ": dimension '" + dimensionId + "' has no server");
            return false;
        }
        target.getServer().execute(() -> teleport(player, dimensionId, true));
        return true;
    }

    private boolean finishTeleport(@NotNull ServerLevel target, @NotNull ServerPlayer player,
                                   @NotNull String dimensionId, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        // Level#getHeight returns the min build height for chunks that are not loaded yet, so
        // probing the spawn columns before generating them always reported "void" and the old
        // fallback teleported the player to the bottom of the world.
        target.getChunk(chunkX, chunkZ);
        BlockPos safe = findSafeSpawnColumn(target, x, z);
        if (safe == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Refusing to teleport " + player.getName().getString() + " to dimension '"
                            + dimensionId + "': no safe ground within " + SPAWN_SEARCH_RADIUS + " blocks of ("
                            + x + ", " + z + "). Check the dimension's spawn point/terrain generator.");
            return false;
        }
        int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING, safe.getX(), safe.getZ());
        player.teleportTo(target, safe.getX() + BLOCK_CENTER, y, safe.getZ() + BLOCK_CENTER,
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
    private static @Nullable BlockPos findSafeSpawnColumn(@NotNull ServerLevel level, int x, int z) {
        int minY = level.getMinBuildHeight();
        for (int ring = 0; ring <= SPAWN_SEARCH_RADIUS; ring++) {
            BlockPos found = findInRing(level, x, z, ring, minY);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @Nullable BlockPos findInRing(@NotNull ServerLevel level, int x, int z, int ring, int minY) {
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                    continue;
                }
                BlockPos candidate = safeColumnAt(level, x + dx, z + dz, minY);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static @Nullable BlockPos safeColumnAt(@NotNull ServerLevel level, int x, int z, int minY) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockState ground = level.getBlockState(new BlockPos(x, height - 1, z));
        BlockState feet = level.getBlockState(new BlockPos(x, height, z));
        BlockState head = level.getBlockState(new BlockPos(x, height + 1, z));
        boolean groundSolidAndDry = !ground.isAir() && ground.getFluidState().isEmpty();
        if (!isSafeSpawnColumn(height, minY, groundSolidAndDry, feet.isAir(), head.isAir())) {
            return null;
        }
        return new BlockPos(x, height, z);
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
        // Above minY + MIN_FLOOR_CLEARANCE so a leftover bottom-layer artifact is never ground.
        return surfaceY > minBuildHeight + MIN_FLOOR_CLEARANCE && groundSolidAndDry && feetAir && headAir;
    }

    @Nullable
    private DimensionServiceImpl.SpawnPoint resolveSpawnPoint(@NotNull String dimensionId) {
        Object service = LuaServiceRegistry.get("DimensionServiceImpl");
        if (service instanceof DimensionServiceImpl dimensionService) {
            return dimensionService.getSpawnPoint(dimensionId);
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
    private ServerLevel resolveLevel(@NotNull String dimensionId) {
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        if (id == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Invalid dimension id for teleport: '" + dimensionId + "'");
            return null;
        }
        return resolveLoadedLevel(CommonServerHolder.get(), id, dimensionId);
    }

    /**
     * Looks the level up without holding it in a local variable: the level is server-owned and
     * must never be closed here, and the lookup is a map read.
     */
    private static @Nullable ServerLevel resolveLoadedLevel(@Nullable MinecraftServer server,
                                                             @NotNull ResourceLocation id,
                                                             @NotNull String dimensionId) {
        if (server == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "No server running; cannot resolve dimension '" + dimensionId + "'");
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, id);
        if (server.getLevel(key) == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Dimension level '" + dimensionId + "' is not loaded");
        }
        return server.getLevel(key);
    }
}
