package com.luatweaker.platform.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.spatial.RaycastParams;
import com.luatweaker.api.spatial.RaycastResult;
import com.luatweaker.platform.NeoForgeWorldBlock;
import com.luatweaker.platform.entity.NeoForgeEntityWrapper;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class NeoForgeSpatialHelper {

    /** Ray sample step while walking loaded chunks; must be smaller than a chunk (16 blocks). */
    private static final double CHUNK_SAMPLE_STEP = 8.0;

    private NeoForgeSpatialHelper() {}

    @NotNull
    public static RaycastResult raycast(
            @NotNull String dimension,
            double originX, double originY, double originZ,
            double dirX, double dirY, double dirZ,
            @NotNull RaycastParams params) {

        Level level = resolveLevel(dimension);
        if (level == null) {
            return RaycastResult.miss(originX, originY, originZ);
        }

        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len == 0) {
            return RaycastResult.miss(originX, originY, originZ);
        }
        double normDirX = dirX / len;
        double normDirY = dirY / len;
        double normDirZ = dirZ / len;

        double maxDist = Math.max(0.1, params.maxDistance());
        // Never trace into unloaded chunks: Level#clip would force generation, and a Lua
        // callback holding the execution gate would deadlock the gen worker. The ray is
        // shortened to the last loaded chunk instead.
        maxDist = clampToLoadedChunks(level, originX, originZ, normDirX, normDirZ, maxDist);
        if (maxDist <= 0) {
            return RaycastResult.miss(originX, originY, originZ);
        }
        Vec3 from = new Vec3(originX, originY, originZ);
        Vec3 to = new Vec3(originX + normDirX * maxDist, originY + normDirY * maxDist, originZ + normDirZ * maxDist);

        Set<Entity> ignoreEntities = new HashSet<>();
        Entity primaryIgnore = null;
        if (params.ignoreEntities() != null) {
            for (IEntity ie : params.ignoreEntities()) {
                if (ie != null && ie.getRawEntity() instanceof Entity mcEntity) {
                    ignoreEntities.add(mcEntity);
                    if (primaryIgnore == null) {
                        primaryIgnore = mcEntity;
                    }
                }
            }
        }

        ClipContext.Fluid fluidMode = params.ignoreLiquids() ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY;
        ClipContext.Block blockMode = params.ignorePassableBlocks() ? ClipContext.Block.COLLIDER : ClipContext.Block.OUTLINE;
        ClipContext clipCtx = new ClipContext(from, to, blockMode, fluidMode, primaryIgnore);
        BlockHitResult blockHit = level.clip(clipCtx);

        AABB box = new AABB(from, to).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level,
                primaryIgnore,
                from,
                to,
                box,
                e -> !ignoreEntities.contains(e) && !e.isSpectator() && e.isPickable()
        );

        double blockDistSqr = (blockHit.getType() != HitResult.Type.MISS)
                ? from.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDistSqr = (entityHit != null)
                ? from.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

        if (blockDistSqr == Double.MAX_VALUE && entityDistSqr == Double.MAX_VALUE) {
            return RaycastResult.miss(to.x, to.y, to.z);
        }

        if (entityDistSqr < blockDistSqr && entityHit != null) {
            Vec3 hitPos = entityHit.getLocation();
            Vec3 norm = from.subtract(hitPos);
            double normLen = norm.length();
            double nx = normLen > 0 ? norm.x / normLen : 0;
            double ny = normLen > 0 ? norm.y / normLen : 1;
            double nz = normLen > 0 ? norm.z / normLen : 0;
            double dist = Math.sqrt(entityDistSqr);
            return new RaycastResult(
                    true,
                    hitPos.x, hitPos.y, hitPos.z,
                    nx, ny, nz,
                    "ENTITY",
                    null,
                    new NeoForgeEntityWrapper(entityHit.getEntity()),
                    dist
            );
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = blockHit.getLocation();
            Direction face = blockHit.getDirection();
            double dist = Math.sqrt(blockDistSqr);
            net.minecraft.core.BlockPos bpos = blockHit.getBlockPos();
            var bstate = level.getBlockState(bpos);
            String bid = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(bstate.getBlock()).toString();
            String dimName = level.dimension().location().toString();
            return new RaycastResult(
                    true,
                    hitPos.x, hitPos.y, hitPos.z,
                    face.getStepX(), face.getStepY(), face.getStepZ(),
                    "BLOCK",
                    new NeoForgeWorldBlock(bid, bpos.getX(), bpos.getY(), bpos.getZ(), dimName, bstate),
                    null,
                    dist
            );
        }

        return RaycastResult.miss(to.x, to.y, to.z);
    }

    /**
     * Shortens the ray so no sample leaves the loaded chunks. Samples every 8 blocks, which is
     * finer than the 16-block chunk width, so a crossed chunk is always sampled.
     */
    private static double clampToLoadedChunks(Level level, double originX, double originZ,
                                              double dirX, double dirZ, double maxDist) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return maxDist;
        }
        double lastLoaded = 0.0;
        for (double travelled = 0.0; travelled <= maxDist; travelled += CHUNK_SAMPLE_STEP) {
            int blockX = net.minecraft.util.Mth.floor(originX + dirX * travelled);
            int blockZ = net.minecraft.util.Mth.floor(originZ + dirZ * travelled);
            if (!serverLevel.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                if (lastLoaded < maxDist) {
                    com.luatweaker.api.log.LuaTweakerLog.get().debug(
                            com.luatweaker.api.log.LogStage.SYSTEM,
                            "Raycast clamped at " + lastLoaded + " blocks: the next chunk is not loaded");
                }
                return lastLoaded;
            }
            lastLoaded = travelled;
        }
        return maxDist;
    }

    private static Level resolveLevel(String dimension) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        if (dimension == null || dimension.isBlank()) {
            return server.overworld();
        }
        net.minecraft.resources.ResourceLocation dimRl = dimension.contains(":")
                ? net.minecraft.resources.ResourceLocation.tryParse(dimension)
                : net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", dimension);
        if (dimRl != null) {
            net.minecraft.resources.ResourceKey<Level> key =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimRl);
            ServerLevel sl = server.getLevel(key);
            if (sl != null) {
                return sl;
            }
        }
        com.luatweaker.api.log.LuaTweakerLog.get().warn(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "Raycast: unknown dimension '" + dimension + "'; returning a miss instead of using the overworld");
        return null;
    }
}
