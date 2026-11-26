package com.luatweaker.common.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.pal.IPlatformInteraction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Platform entry point for the Lua interaction/world API.
 *
 * <p>This class is deliberately a thin dispatching facade: every implementation lives in a
 * focused helper ({@link WorldBlockOps}, {@link WorldBulkOps}, {@link WorldQueryOps},
 * {@link WorldTransferOps}, {@link WorldEffectsOps}, {@link MachineBlockOps},
 * {@link CuboidValidator}) so this file only maps PAL calls onto them.
 */
public class MinecraftInteractionPlatform implements IPlatformInteraction {
    private static final String COMMAND_PERMISSION = "commands.execute";

    @Override
    public void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType, double speed,
                                double inaccuracy) {
        EntityInteractionHelper.shootProjectile(shooter, projectileType, speed, inaccuracy);
    }

    @Override
    @Nullable
    public IEntity shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType, @NotNull IEntity target,
                                     double speed) {
        return EntityInteractionHelper.shootProjectileAt(shooter, projectileType, target, speed);
    }

    @Override
    public void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed, double transition) {
        EntityAnimationHelper.playAnimation(entity, animName, speed, transition);
    }

    @Override
    public boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z) {
        return BlockInteractionHelper.performBlockBreak(actor, x, y, z);
    }

    @Override
    public boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) {
        return BlockInteractionHelper.performBlockPlace(actor, x, y, z, blockId);
    }

    @Override
    public boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z) {
        return BlockInteractionHelper.performBlockUse(actor, x, y, z);
    }

    @Override
    public boolean performItemUse(@NotNull IEntity actor, int slot) {
        return InventoryInteractionHelper.performItemUse(actor, slot);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, double x, double y, double z) {
        EntityInteractionHelper.lookAt(actor, x, y, z);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {
        EntityInteractionHelper.lookAt(actor, target);
    }

    @Override
    public boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) {
        return InventoryInteractionHelper.moveInventoryItem(actor, fromSlot, toSlot);
    }

    @Override
    public boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) {
        return InventoryInteractionHelper.dropInventoryItem(actor, slot, count);
    }

    @Override
    @NotNull
    public List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) {
        return BlockInteractionHelper.getNearbyBlocks(entity, radius);
    }

    @Override
    @NotNull
    public List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) {
        return InventoryInteractionHelper.getInventoryItems(entity);
    }

    @Override
    @NotNull
    public List<IEntity> getNearbyEntities(@NotNull IEntity center, double radius) {
        List<IEntity> result = new ArrayList<>();
        if (center.getRawEntity() instanceof net.minecraft.world.entity.Entity mcEntity) {
            collectNearbyEntities(mcEntity, radius, result);
        }
        return result;
    }

    private static void collectNearbyEntities(net.minecraft.world.entity.Entity mcEntity, double radius,
                                              List<IEntity> result) {
        if (!(mcEntity.level() instanceof ServerLevel)) {
            return;
        }
        var area = mcEntity.getBoundingBox().inflate(radius);
        ServerLevels.run(ServerLevels.asServer(mcEntity.level()), level -> {
            for (net.minecraft.world.entity.LivingEntity living
                    : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area,
                    e -> !Objects.equals(e, mcEntity))) {
                result.add(new MinecraftInteractableEntity(living));
            }
        });
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(@NotNull String dimension, int x, int y,
                                                                                 int z) {
        return new MinecraftInteractableBlock(dimension, x, y, z);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableItem getInteractableItem(@NotNull Object entityOrBlock, int slot) {
        return new MinecraftInteractableItem(entityOrBlock, slot);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull String uuid) {
        return EntityInteractionHelper.getInteractableEntity(uuid);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull Object rawEntity) {
        return rawEntity instanceof net.minecraft.world.entity.Entity mcEntity
                ? new MinecraftInteractableEntity(mcEntity)
                : null;
    }

    @Override
    @Nullable
    public Map<String, Object> getBlockState(@NotNull String dimension, int x, int y, int z) {
        return WorldBlockOps.getBlockState(dimension, x, y, z);
    }

    @Override
    public boolean setBlockState(@NotNull String dimension, int x, int y, int z, @NotNull String blockId,
                                 @Nullable Map<String, Object> properties) {
        return WorldBlockOps.writeBlockState(dimension, x, y, z, blockId, properties);
    }

    @Override
    @Nullable
    public Map<String, Object> getBlockEntityData(@NotNull String dimension, int x, int y, int z) {
        return WorldBlockOps.getBlockEntityData(dimension, x, y, z);
    }

    @Override
    public boolean setBlockEntityData(@NotNull String dimension, int x, int y, int z,
                                      @NotNull Map<String, Object> data) {
        return WorldBlockOps.writeBlockEntityData(dimension, x, y, z, data);
    }

    @Override
    public boolean ejectContainerItem(@NotNull String dimension, int x, int y, int z, int slot, int count) {
        return WorldBlockOps.ejectContainerItem(dimension, x, y, z, slot, count);
    }

    @Override
    public long fillBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                           @NotNull String blockId, @Nullable Map<String, Object> properties) {
        return WorldBulkOps.fillBlocks(dimension, x1, y1, z1, x2, y2, z2, blockId, properties);
    }

    @Override
    public long replaceBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                              @NotNull String fromId, @NotNull String toId) {
        return WorldBulkOps.replaceBlocks(dimension, x1, y1, z1, x2, y2, z2, fromId, toId);
    }

    @Override
    public boolean placeStructure(@NotNull String dimension, @NotNull String templateId,
                                  int x, int y, int z, int rotationDegrees) {
        return WorldBlockOps.placeStructure(dimension, templateId, x, y, z, rotationDegrees);
    }

    @Override
    public boolean executeCommand(@NotNull String command) {
        return executeOn(com.luatweaker.common.CommonServerHolder.get(), command);
    }

    /**
     * Runs a console command on the server thread.
     *
     * <p>{@code Commands#performPrefixedCommand} parses and queues the command itself and reports
     * syntax/execution failures through the command source, so there is no local exception path
     * to swallow here.
     */
    private static boolean executeOn(@Nullable MinecraftServer server, @Nullable String command) {
        if (server == null || command == null || command.isBlank()) {
            return false;
        }
        if (!hasCommandPermission()) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "ExecuteCommand rejected: no loaded mod declares the '" + COMMAND_PERMISSION
                            + "' permission in manifest.json. Command: '" + command + "'");
            return false;
        }
        if (!server.isSameThread()) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "ExecuteCommand rejected: must run on the server thread (command: '" + command + "')");
            return false;
        }
        // Commands need an active world (game rules); during server startup the
        // overworld is not created yet, so defer or skip.
        if (server.overworld() == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "executeCommand skipped (world not ready): '" + command + "'");
            return false;
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        return true;
    }

    /** Per-installation grant: any loaded mod opting in unlocks console commands. */
    private static boolean hasCommandPermission() {
        for (com.luatweaker.core.mod.LuaMod mod
                : com.luatweaker.core.mod.LuaModManager.getLoadedMods().values()) {
            if (mod.getManifest().permissions().contains(COMMAND_PERMISSION)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @NotNull
    public com.luatweaker.api.spatial.RaycastResult raycast(
            @NotNull String dimension,
            double originX, double originY, double originZ,
            double dirX, double dirY, double dirZ,
            @NotNull com.luatweaker.api.spatial.RaycastParams params) {
        return MinecraftSpatialHelper.raycast(dimension, originX, originY, originZ, dirX, dirY, dirZ, params);
    }

    @Override
    public void spawnParticle(@NotNull String dimension, @NotNull String particleId,
                              double x, double y, double z,
                              double vx, double vy, double vz,
                              int count, double speed) {
        WorldParticleOps.spawnParticle(dimension, particleId,
                new com.luatweaker.api.particle.ParticleSpawn(
                        x, y, z, vx, vy, vz, count, speed));
    }

    @Override
    public void spawnParticleOptions(@NotNull String dimension, @NotNull String particleId,
                                     @NotNull com.luatweaker.api.particle.ParticleSpawn spawn,
                                     @NotNull com.luatweaker.api.particle.ParticleOptionsSpec options) {
        WorldParticleOps.spawnParticleOptions(dimension, particleId, spawn, options);
    }

    @Override
    public void playSound(@NotNull String dimension, @NotNull String soundId,
                          double x, double y, double z,
                          float volume, float pitch, @NotNull String source) {
        WorldEffectsOps.playSound(dimension, soundId, x, y, z, volume, pitch, source);
    }

    @Override
    public boolean stopSound(@NotNull String dimension, @NotNull String soundId, @NotNull String source) {
        return WorldEffectsOps.stopSound(dimension, soundId, source);
    }

    @Override
    public int getRedstoneSignal(@NotNull String dimension, int x, int y, int z, @Nullable String direction) {
        return WorldEffectsOps.getRedstoneSignal(dimension, x, y, z, direction);
    }

    @Override
    public boolean checkPattern(@NotNull String dimension, int originX, int originY, int originZ,
                                @NotNull List<Map<String, Object>> pattern) {
        return WorldQueryOps.checkPattern(dimension, originX, originY, originZ, pattern);
    }

    @Override
    public int transferEnergy(@NotNull String dimension, int fromX, int fromY, int fromZ,
                              int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        return WorldTransferOps.transferEnergy(dimension, fromX, fromY, fromZ, toX, toY, toZ, maxAmount, simulate);
    }

    @Override
    public int transferFluid(@NotNull String dimension, int fromX, int fromY, int fromZ,
                             int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        return WorldTransferOps.transferFluid(dimension, fromX, fromY, fromZ, toX, toY, toZ, maxAmount, simulate);
    }

    @Override
    public int transferItem(@NotNull String dimension, int fromX, int fromY, int fromZ, int fromSlot,
                            int toX, int toY, int toZ, int toSlot, int maxCount, boolean simulate) {
        return WorldTransferOps.transferItem(dimension,
                new WorldTransferOps.Transfer(fromX, fromY, fromZ, fromSlot, toX, toY, toZ, toSlot,
                        maxCount, simulate));
    }

    @Override
    @NotNull
    public Map<String, Object> validateCuboid(@NotNull String dimension,
                                              int x1, int y1, int z1,
                                              int x2, int y2, int z2,
                                              @NotNull Map<String, Object> spec) {
        return CuboidValidator.validate(dimension, x1, y1, z1, x2, y2, z2, spec);
    }

    @Override
    public int setBlockStatesInBox(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                                   @Nullable String filterBlockId, @NotNull Map<String, Object> properties) {
        return WorldBulkOps.patchBlockStatesInBox(dimension, x1, y1, z1, x2, y2, z2, filterBlockId, properties);
    }

    @Override
    @NotNull
    public List<Map<String, Object>> findBlocks(@NotNull String dimension, int centerX, int centerY, int centerZ,
                                                int radius, @NotNull String filter, int maxResults) {
        return WorldQueryOps.findBlocks(dimension, centerX, centerY, centerZ, radius, filter, maxResults);
    }

    @Override
    public boolean setEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
        return MachineBlockOps.writeEnergy(dimension, x, y, z, amount);
    }

    @Override
    public int produceEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
        return MachineBlockOps.produceEnergy(dimension, x, y, z, amount);
    }

    @Override
    public boolean setProgress(@NotNull String dimension, int x, int y, int z, float progress) {
        return MachineBlockOps.writeProgress(dimension, x, y, z, progress);
    }

    @Override
    public float getProgress(@NotNull String dimension, int x, int y, int z) {
        return MachineBlockOps.getProgress(dimension, x, y, z);
    }

    @Override
    public boolean triggerGeoAnimation(@NotNull Object target, @NotNull String controllerName, @NotNull String animName,
                                       double speed, boolean loop) {
        return com.luatweaker.common.integration.geckolib.GeckoLibAnimationBridge.triggerAnimation(
                target, controllerName, animName, speed, loop);
    }

    @Override
    public boolean stopGeoAnimation(@NotNull Object target, @NotNull String controllerName, @NotNull String animName) {
        return com.luatweaker.common.integration.geckolib.GeckoLibAnimationBridge.stopAnimation(
                target, controllerName, animName);
    }

    @Override
    public long getDayTime(@NotNull String dimension) {
        return WorldEnvOps.getDayTime(dimension);
    }

    @Override
    public boolean setDayTime(@NotNull String dimension, long dayTime) {
        return WorldEnvOps.applyDayTime(dimension, dayTime);
    }

    @Override
    public long getGameTime(@NotNull String dimension) {
        return WorldEnvOps.getGameTime(dimension);
    }

    @Override
    @Nullable
    public Map<String, Object> getWeather(@NotNull String dimension) {
        return WorldEnvOps.getWeather(dimension);
    }

    @Override
    public boolean setWeather(@NotNull String dimension, @NotNull String kind, int durationTicks) {
        return WorldEnvOps.applyWeather(dimension, kind, durationTicks);
    }

    @Override
    @Nullable
    public String getBiome(@NotNull String dimension, int x, int y, int z) {
        return WorldEnvOps.getBiome(dimension, x, y, z);
    }

    @Override
    public int getLightLevel(@NotNull String dimension, int x, int y, int z, @NotNull String kind) {
        return WorldEnvOps.getLightLevel(dimension, x, y, z, kind);
    }

    @Override
    public boolean canSeeSky(@NotNull String dimension, int x, int y, int z) {
        return WorldEnvOps.canSeeSky(dimension, x, y, z);
    }

    @Override
    public int getHighestBlockY(@NotNull String dimension, int x, int z, @NotNull String kind) {
        return WorldEnvOps.getHighestBlockY(dimension, x, z, kind);
    }

    @Override
    public boolean isChunkLoaded(@NotNull String dimension, int x, int z) {
        return ServerLevels.map(ServerLevels.resolve(dimension), false,
                level -> BlockStateCodec.isChunkLoaded(level, x, z));
    }

    @Override
    @Nullable
    public String getDifficulty(@NotNull String dimension) {
        return ServerLevels.map(ServerLevels.resolve(dimension), null,
                level -> level.getDifficulty().name());
    }

    @Override
    public long getSeed(@NotNull String dimension) {
        return ServerLevels.map(ServerLevels.resolve(dimension), 0L, ServerLevel::getSeed);
    }

    @Override
    @Nullable
    public Map<String, Object> getWorldSpawn(@NotNull String dimension) {
        return WorldBorderOps.getWorldSpawn(dimension);
    }

    @Override
    public boolean setWorldSpawn(@NotNull String dimension, int x, int y, int z, float angle) {
        return WorldBorderOps.applyWorldSpawn(dimension, x, y, z, angle);
    }

    @Override
    @Nullable
    public Map<String, Object> getWorldBorder(@NotNull String dimension) {
        return WorldBorderOps.getWorldBorder(dimension);
    }

    @Override
    public boolean setWorldBorder(@NotNull String dimension, double centerX, double centerZ,
                                  double size, long lerpMillis) {
        return WorldBorderOps.applyWorldBorder(dimension, centerX, centerZ, size, lerpMillis);
    }

    @Override
    @Nullable
    public Object getGameRule(@NotNull String ruleName) {
        return GameRuleAccess.get(ruleName);
    }

    @Override
    public boolean setGameRule(@NotNull String ruleName, @NotNull Object value) {
        return GameRuleAccess.set(ruleName, value);
    }
}

