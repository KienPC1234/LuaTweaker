package com.luatweaker.common.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.spatial.RaycastParams;
import com.luatweaker.api.spatial.RaycastResult;
import com.luatweaker.common.CommonServerHolder;
import com.luatweaker.common.entity.MinecraftEntityWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class MinecraftSpatialHelper {

    private static final double CHUNK_SAMPLE_STEP = 8.0;
    private static final double MIN_RAY_LENGTH = 0.1;

    private MinecraftSpatialHelper() {}

    @NotNull
    public static RaycastResult raycast(
            @NotNull String dimension,
            double originX, double originY, double originZ,
            double dirX, double dirY, double dirZ,
            @NotNull RaycastParams params) {
        return ServerLevels.map(ServerLevels.asServer(resolveOn(CommonServerHolder.get(), dimension)),
                RaycastResult.miss(originX, originY, originZ),
                level -> trace(level, originX, originY, originZ, dirX, dirY, dirZ, params));
    }

    @NotNull
    private static RaycastResult trace(ServerLevel level, double originX, double originY, double originZ,
                                       double dirX, double dirY, double dirZ, RaycastParams params) {
        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len == 0) {
            return RaycastResult.miss(originX, originY, originZ);
        }
        double normDirX = dirX / len;
        double normDirY = dirY / len;
        double normDirZ = dirZ / len;

        double maxDist = clampToLoadedChunks(level, originX, originZ, normDirX, normDirZ,
                Math.max(MIN_RAY_LENGTH, params.maxDistance()));
        if (maxDist <= 0) {
            return RaycastResult.miss(originX, originY, originZ);
        }
        Vec3 from = new Vec3(originX, originY, originZ);
        Vec3 to = new Vec3(originX + normDirX * maxDist, originY + normDirY * maxDist,
                originZ + normDirZ * maxDist);
        Ignores ignores = collectIgnores(params);
        BlockHitResult blockHit = level.clip(new ClipContext(from, to,
                params.ignorePassableBlocks() ? ClipContext.Block.COLLIDER : ClipContext.Block.OUTLINE,
                params.ignoreLiquids() ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY,
                ignores.primary()));
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, ignores.primary(), from, to,
                new AABB(from, to).inflate(1.0),
                e -> !ignores.entities().contains(e) && !e.isSpectator() && e.isPickable());
        return resolveHit(level, from, to, blockHit, entityHit);
    }

    @NotNull
    private static RaycastResult resolveHit(ServerLevel level, Vec3 from, Vec3 to,
                                            BlockHitResult blockHit, EntityHitResult entityHit) {
        double blockDistSqr = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE : from.distanceToSqr(blockHit.getLocation());
        double entityDistSqr = entityHit == null ? Double.MAX_VALUE : from.distanceToSqr(entityHit.getLocation());
        if (blockDistSqr == Double.MAX_VALUE && entityDistSqr == Double.MAX_VALUE) {
            return RaycastResult.miss(to.x, to.y, to.z);
        }
        if (entityDistSqr < blockDistSqr && entityHit != null) {
            return entityResult(from, entityHit, entityDistSqr);
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            return blockResult(level, blockHit, blockDistSqr);
        }
        return RaycastResult.miss(to.x, to.y, to.z);
    }

    @NotNull
    private static RaycastResult entityResult(Vec3 from, EntityHitResult entityHit, double entityDistSqr) {
        Vec3 hitPos = entityHit.getLocation();
        Vec3 norm = from.subtract(hitPos);
        double normLen = norm.length();
        double nx = normLen > 0 ? norm.x / normLen : 0;
        double ny = normLen > 0 ? norm.y / normLen : 1;
        double nz = normLen > 0 ? norm.z / normLen : 0;
        return new RaycastResult(
                true,
                hitPos.x, hitPos.y, hitPos.z,
                nx, ny, nz,
                "ENTITY",
                null,
                new MinecraftEntityWrapper(entityHit.getEntity()),
                Math.sqrt(entityDistSqr));
    }

    @NotNull
    private static RaycastResult blockResult(ServerLevel level, BlockHitResult blockHit, double blockDistSqr) {
        Vec3 hitPos = blockHit.getLocation();
        Direction face = blockHit.getDirection();
        BlockPos bpos = blockHit.getBlockPos();
        var bstate = level.getBlockState(bpos);
        String bid = BuiltInRegistries.BLOCK.getKey(bstate.getBlock()).toString();
        String dimName = level.dimension().location().toString();
        return new RaycastResult(
                true,
                hitPos.x, hitPos.y, hitPos.z,
                face.getStepX(), face.getStepY(), face.getStepZ(),
                "BLOCK",
                new MinecraftWorldBlock(bid, bpos.getX(), bpos.getY(), bpos.getZ(), dimName, bstate),
                null,
                Math.sqrt(blockDistSqr));
    }

    private record Ignores(Set<Entity> entities, @Nullable Entity primary) {
    }

    @NotNull
    private static Ignores collectIgnores(RaycastParams params) {
        Set<Entity> entities = new HashSet<>();
        var ordered = new ArrayList<Entity>();
        if (params.ignoreEntities() != null) {
            for (IEntity candidate : params.ignoreEntities()) {
                if (candidate != null && candidate.getRawEntity() instanceof Entity mcEntity
                        && entities.add(mcEntity)) {
                    ordered.add(mcEntity);
                }
            }
        }
        return new Ignores(entities, ordered.isEmpty() ? null : ordered.get(0));
    }

    private static double clampToLoadedChunks(Level level, double originX, double originZ,
                                              double dirX, double dirZ, double maxDist) {
        return ServerLevels.map(ServerLevels.asServer(level), maxDist,
                serverLevel -> clampIn(serverLevel, originX, originZ, dirX, dirZ, maxDist));
    }

    private static double clampIn(ServerLevel level, double originX, double originZ,
                                  double dirX, double dirZ, double maxDist) {
        double lastLoaded = 0.0;
        for (double travelled = 0.0; travelled <= maxDist; travelled += CHUNK_SAMPLE_STEP) {
            int blockX = (int) Math.floor(originX + dirX * travelled);
            int blockZ = (int) Math.floor(originZ + dirZ * travelled);
            if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) {
                if (lastLoaded < maxDist) {
                    LuaTweakerLog.get().debug(
                            LogStage.SYSTEM,
                            "Raycast clamped at " + lastLoaded + " blocks: the next chunk is not loaded");
                }
                return lastLoaded;
            }
            lastLoaded = travelled;
        }
        return maxDist;
    }

    @Nullable
    private static Level resolveOn(@Nullable MinecraftServer server, @Nullable String dimension) {
        if (server == null) {
            return null;
        }
        if (dimension == null || dimension.isBlank()) {
            return server.overworld();
        }
        ResourceLocation dimRl = dimension.contains(":")
                ? ResourceLocation.tryParse(dimension)
                : ResourceLocation.fromNamespaceAndPath("minecraft", dimension);
        if (dimRl == null) {
            return warnUnknown(dimension);
        }
        ServerLevel resolved = server.getLevel(
                ResourceKey.create(Registries.DIMENSION, dimRl));
        return resolved != null ? resolved : warnUnknown(dimension);
    }

    @Nullable
    private static Level warnUnknown(String dimension) {
        LuaTweakerLog.get().warn(
                LogStage.SYSTEM,
                "Raycast: unknown dimension '" + dimension + "'; returning a miss instead of using the overworld");
        return null;
    }
}
