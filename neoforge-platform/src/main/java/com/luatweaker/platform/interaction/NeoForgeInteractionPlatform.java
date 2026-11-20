package com.luatweaker.platform.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.pal.IPlatformInteraction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NeoForgeInteractionPlatform implements IPlatformInteraction {
    @Override
    public void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType, double speed, double inaccuracy) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.shootProjectile(shooter, projectileType, speed, inaccuracy);
    }

    @Override
    @Nullable
    public com.luatweaker.api.entity.IEntity shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType, @NotNull IEntity target, double speed) {
        return com.luatweaker.platform.interaction.EntityInteractionHelper.shootProjectileAt(shooter, projectileType, target, speed);
    }

    @Override
    public void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed, double transition) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.playAnimation(entity, animName, speed, transition);
    }

    @Override
    public boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.performBlockBreak(actor, x, y, z);
    }

    @Override
    public boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.performBlockPlace(actor, x, y, z, blockId);
    }

    @Override
    public boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.performBlockUse(actor, x, y, z);
    }

    @Override
    public boolean performItemUse(@NotNull IEntity actor, int slot) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.performItemUse(actor, slot);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, double x, double y, double z) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.lookAt(actor, x, y, z);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.lookAt(actor, target);
    }

    @Override
    public boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.moveInventoryItem(actor, fromSlot, toSlot);
    }

    @Override
    public boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.dropInventoryItem(actor, slot, count);
    }

    @Override
    @NotNull
    public List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.getNearbyBlocks(entity, radius);
    }

    @Override
    @NotNull
    public List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.getInventoryItems(entity);
    }

    @Override
    @NotNull
    public List<IEntity> getNearbyEntities(@NotNull IEntity center, double radius) {
        java.util.List<IEntity> result = new java.util.ArrayList<>();
        if (center.getRawEntity() instanceof net.minecraft.world.entity.Entity mcEntity
                && mcEntity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            net.minecraft.world.phys.AABB area = mcEntity.getBoundingBox().inflate(radius);
            for (net.minecraft.world.entity.LivingEntity living
                    : serverLevel.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area, e -> e != mcEntity)) {
                result.add(new NeoForgeInteractableEntity(living));
            }
        }
        return result;
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(@NotNull String dimension, int x, int y, int z) {
        return new NeoForgeInteractableBlock(dimension, x, y, z);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableItem getInteractableItem(@NotNull Object entityOrBlock, int slot) {
        return new NeoForgeInteractableItem(entityOrBlock, slot);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull String uuid) {
        return com.luatweaker.platform.interaction.EntityInteractionHelper.getInteractableEntity(uuid);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull Object rawEntity) {
        if (rawEntity instanceof net.minecraft.world.entity.Entity mcEntity) {
            return new NeoForgeInteractableEntity(mcEntity);
        }
        return null;
    }

    @Override
    @Nullable
    public java.util.Map<String, Object> getBlockState(@NotNull String dimension, int x, int y, int z) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return null;
        // Never force chunk generation from a Lua callback: the server thread may hold the Lua
        // execution gate while waiting for generation, and the gen worker would then need that
        // same gate to run its Lua terrain callback (deadlock). Unloaded chunks read as null.
        if (!isChunkLoaded(level, x, z)) {
            return null;
        }
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("Id", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            properties.put(entry.getKey().getName(), entry.getValue().toString());
        }
        result.put("Properties", properties);
        return result;
    }

    @Override
    public boolean setBlockState(@NotNull String dimension, int x, int y, int z, @NotNull String blockId,
                                 @Nullable java.util.Map<String, Object> properties) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return false;
        net.minecraft.world.level.block.state.BlockState state = resolveBlockState(blockId, properties);
        if (state == null) return false;
        // Writing into an unloaded chunk forces generation; refuse it loudly instead of
        // blocking on a chunk future while holding the Lua execution gate.
        if (!isChunkLoaded(level, x, z)) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "setBlockState skipped: chunk (" + (x >> 4) + "," + (z >> 4)
                            + ") is not loaded in " + dimension);
            return false;
        }
        return level.setBlock(new net.minecraft.core.BlockPos(x, y, z), state, 3);
    }

    /**
     * Parses a block id plus optional property values into a {@link BlockState}.
     * Returns null for unknown block ids. Property values that do not match the
     * block's definition are skipped with a warning (matching per-block behavior).
     */
    @Nullable
    private static net.minecraft.world.level.block.state.BlockState resolveBlockState(
            @NotNull String blockId, @Nullable java.util.Map<String, Object> properties) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(blockId);
        if (rl == null) return null;
        net.minecraft.world.level.block.Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl);
        if (block == net.minecraft.world.level.block.Blocks.AIR && !blockId.equals("minecraft:air")) return null;
        net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
        if (properties != null) {
            for (java.util.Map.Entry<String, Object> entry : properties.entrySet()) {
                net.minecraft.world.level.block.state.properties.Property<?> property =
                        state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property == null) continue;
                try {
                    state = setProperty(state, property, String.valueOf(entry.getValue()));
                } catch (IllegalArgumentException e) {
                    com.luatweaker.api.log.LuaTweakerLog.get().warn(
                            com.luatweaker.api.log.LogStage.SYSTEM,
                            "Invalid value '" + entry.getValue() + "' for blockstate property " + entry.getKey());
                }
            }
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static net.minecraft.world.level.block.state.BlockState setProperty(
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.world.level.block.state.properties.Property property, String value) {
        for (Comparable possible : (Iterable<Comparable>) property.getPossibleValues()) {
            if (possible.toString().equals(value)) {
                return state.setValue(property, possible);
            }
        }
        return state;
    }

    @Override
    @Nullable
    public java.util.Map<String, Object> getBlockEntityData(@NotNull String dimension, int x, int y, int z) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return null;
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(new net.minecraft.core.BlockPos(x, y, z));
        if (be == null) return null;
        return NbtCodec.toMap(be.saveWithFullMetadata(level.registryAccess()));
    }

    @Override
    public boolean setBlockEntityData(@NotNull String dimension, int x, int y, int z,
                                      @NotNull java.util.Map<String, Object> data) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return false;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        try {
            // Merge: Lua data overrides the given keys, everything else (items,
            // position, id) is preserved so partial writes never wipe contents.
            net.minecraft.nbt.CompoundTag merged = be.saveWithoutMetadata(level.registryAccess());
            net.minecraft.nbt.CompoundTag update = NbtCodec.fromMap(data);
            for (String key : update.getAllKeys()) {
                merged.put(key, update.get(key));
            }
            be.loadWithComponents(merged, level.registryAccess());
            be.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 2);
            return true;
        } catch (Exception e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Failed to apply block entity data at " + pos + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean ejectContainerItem(@NotNull String dimension, int x, int y, int z, int slot, int count) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return false;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof net.minecraft.world.Container container)) return false;
        if (slot < 0 || slot >= container.getContainerSize() || count <= 0) return false;
        net.minecraft.world.item.ItemStack stack = container.removeItem(slot, count);
        if (stack.isEmpty()) return false;
        net.minecraft.world.level.block.Block.popResource(level, pos, stack);
        be.setChanged();
        return true;
    }

    /**
     * Hard safety cap for a single bulk fill operation. Scripts must enforce
     * their own (config-driven) limits for good feedback; this only prevents
     * accidental lag-bombs from a single call.
     */
    private static final long MAX_FILL_VOLUME = 200_000L;

    @Override
    public long fillBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                           @NotNull String blockId, @Nullable java.util.Map<String, Object> properties) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return -1;
        net.minecraft.world.level.block.state.BlockState state = resolveBlockState(blockId, properties);
        if (state == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "fillBlocks rejected: unknown block id '" + blockId + "'");
            return -1;
        }

        long volume = boxVolume(x1, y1, z1, x2, y2, z2);
        if (volume <= 0) return -1;
        if (volume > MAX_FILL_VOLUME) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "fillBlocks rejected: volume " + volume + " exceeds platform cap " + MAX_FILL_VOLUME);
            return -1;
        }

        long set = 0;
        long skippedUnloaded = 0;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    if (!isChunkLoaded(level, x, z)) {
                        skippedUnloaded++;
                        continue;
                    }
                    if (level.setBlock(new net.minecraft.core.BlockPos(x, y, z), state, 3)) {
                        set++;
                    }
                }
            }
        }
        warnSkippedUnloaded("fillBlocks", skippedUnloaded);
        return set;
    }

    @Override
    public long replaceBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                              @NotNull String fromId, @NotNull String toId) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return -1;
        net.minecraft.world.level.block.state.BlockState toState = resolveBlockState(toId, null);
        if (toState == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "replaceBlocks rejected: unknown target block id '" + toId + "'");
            return -1;
        }
        net.minecraft.world.level.block.Block fromBlock = parseBlock(fromId);
        if (fromBlock == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "replaceBlocks rejected: unknown source block id '" + fromId + "'");
            return -1;
        }

        long volume = boxVolume(x1, y1, z1, x2, y2, z2);
        if (volume <= 0) return -1;
        if (volume > MAX_FILL_VOLUME) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "replaceBlocks rejected: volume " + volume + " exceeds platform cap " + MAX_FILL_VOLUME);
            return -1;
        }

        long replaced = 0;
        long skippedUnloaded = 0;
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    if (!isChunkLoaded(level, x, z)) {
                        skippedUnloaded++;
                        continue;
                    }
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() == fromBlock
                            && level.setBlock(pos, toState, 3)) {
                        replaced++;
                    }
                }
            }
        }
        warnSkippedUnloaded("replaceBlocks", skippedUnloaded);
        return replaced;
    }

    /**
     * True when the chunk is already loaded. Deliberately does not trigger generation: a Lua
     * callback that blocks on a chunk future while holding the execution gate can deadlock with
     * the generation worker that needs that gate for its own Lua terrain callback.
     */
    private static boolean isChunkLoaded(net.minecraft.server.level.ServerLevel level, int x, int z) {
        return level.getChunkSource().hasChunk(x >> 4, z >> 4);
    }

    private static void warnSkippedUnloaded(String operation, long skipped) {
        if (skipped > 0) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    operation + " skipped " + skipped + " block(s) in unloaded chunks");
        }
    }

    private static long boxVolume(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = (long) Math.abs(x2 - x1) + 1;
        long dy = (long) Math.abs(y2 - y1) + 1;
        long dz = (long) Math.abs(z2 - z1) + 1;
        return dx * dy * dz;
    }

    @Nullable
    private static net.minecraft.world.level.block.Block parseBlock(@NotNull String blockId) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(blockId);
        if (rl == null) return null;
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl);
    }

    @Override
    public boolean placeStructure(@NotNull String dimension, @NotNull String templateId,
                                  int x, int y, int z, int rotationDegrees) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "placeStructure failed: dimension '" + dimension + "' is not loaded");
            return false;
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(templateId);
        if (rl == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "placeStructure failed: invalid template id '" + templateId + "'");
            return false;
        }
        java.util.Optional<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate> template =
                level.getStructureManager().get(rl);
        if (template.isEmpty()) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "placeStructure failed: template '" + templateId
                            + "' not found (data/" + rl.getNamespace() + "/structures/" + rl.getPath() + ".nbt)");
            return false;
        }
        net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings settings =
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings()
                        .setRotation(net.minecraft.world.level.block.Rotation.values()[
                                Math.floorMod(rotationDegrees / 90, 4)]);
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
        settings.setRandom(random);
        template.get().placeInWorld(level, new net.minecraft.core.BlockPos(x, y, z),
                new net.minecraft.core.BlockPos(0, 0, 0), settings, random, 2);
        return true;
    }

    /** Manifest permission required to run console-level commands from Lua. */
    public static final String COMMAND_PERMISSION = "commands.execute";

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
    public boolean executeCommand(@NotNull String command) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || command == null || command.isBlank()) return false;
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
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            return true;
        } catch (Exception e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Failed to execute server command '" + command + "': " + e.getMessage());
            return false;
        }
    }

    @Override
    @NotNull
    public com.luatweaker.api.spatial.RaycastResult raycast(
            @NotNull String dimension,
            double originX, double originY, double originZ,
            double dirX, double dirY, double dirZ,
            @NotNull com.luatweaker.api.spatial.RaycastParams params) {
        return NeoForgeSpatialHelper.raycast(dimension, originX, originY, originZ, dirX, dirY, dirZ, params);
    }

    @Override
    public void spawnParticle(@NotNull String dimension, @NotNull String particleId,
                               double x, double y, double z,
                               double vx, double vy, double vz,
                               int count, double speed) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return;
        net.minecraft.resources.ResourceLocation rl = particleId.contains(":")
                ? net.minecraft.resources.ResourceLocation.tryParse(particleId)
                : net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", particleId);
        if (rl == null) return;
        var particleOpt = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getOptional(rl);
        if (particleOpt.isPresent() && particleOpt.get() instanceof net.minecraft.core.particles.ParticleOptions pOptions) {
            level.sendParticles(pOptions, x, y, z, Math.max(1, count), vx, vy, vz, speed);
        }
    }

    @Override
    public void playSound(@NotNull String dimension, @NotNull String soundId,
                           double x, double y, double z,
                           float volume, float pitch, @NotNull String source) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) {
            return;
        }
        net.minecraft.resources.ResourceLocation rl =
                com.luatweaker.platform.sound.SoundHelper.parseId(soundId);
        net.minecraft.sounds.SoundEvent event =
                com.luatweaker.platform.sound.SoundHelper.registered(rl, "dimension " + dimension);
        if (event == null) {
            return;
        }
        net.minecraft.sounds.SoundSource category =
                com.luatweaker.platform.sound.SoundHelper.parseSource(source,
                        net.minecraft.sounds.SoundSource.PLAYERS);
        level.playSound(null, x, y, z, event, category,
                com.luatweaker.platform.sound.SoundHelper.volume(volume),
                com.luatweaker.platform.sound.SoundHelper.pitch(pitch));
    }

    @Override
    public boolean stopSound(@NotNull String dimension, @NotNull String soundId, @NotNull String source) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) {
            return false;
        }
        net.minecraft.resources.ResourceLocation rl =
                com.luatweaker.platform.sound.SoundHelper.parseId(soundId);
        if (rl == null) {
            return false;
        }
        net.minecraft.sounds.SoundSource category =
                com.luatweaker.platform.sound.SoundHelper.parseSource(source,
                        net.minecraft.sounds.SoundSource.MASTER);
        if (level.players().isEmpty()) {
            return false;
        }
        net.minecraft.network.protocol.game.ClientboundStopSoundPacket packet =
                new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(rl, category);
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
        return true;
    }

    @Override
    public int getRedstoneSignal(@NotNull String dimension, int x, int y, int z, @Nullable String direction) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return 0;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        if (direction != null && !direction.isBlank()) {
            net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(direction.toLowerCase());
            if (dir != null) {
                return level.getSignal(pos, dir);
            }
        }
        return level.getBestNeighborSignal(pos);
    }

    @Override
    public boolean checkPattern(@NotNull String dimension, int originX, int originY, int originZ,
                                @NotNull java.util.List<java.util.Map<String, Object>> pattern) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return false;
        for (java.util.Map<String, Object> entry : pattern) {
            int dx = entry.get("dx") instanceof Number n ? n.intValue() : 0;
            int dy = entry.get("dy") instanceof Number n ? n.intValue() : 0;
            int dz = entry.get("dz") instanceof Number n ? n.intValue() : 0;
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(originX + dx, originY + dy, originZ + dz);
            if (!isChunkLoaded(level, pos.getX(), pos.getZ())) {
                return false;
            }
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            String requiredBlock = (String) entry.get("block");
            if (requiredBlock != null) {
                String actualId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if (!requiredBlock.contains(":")) {
                    requiredBlock = "minecraft:" + requiredBlock;
                }
                if (!actualId.equalsIgnoreCase(requiredBlock)) {
                    return false;
                }
            }
            String requiredTag = (String) entry.get("tag");
            if (requiredTag != null) {
                net.minecraft.resources.ResourceLocation tagRl = net.minecraft.resources.ResourceLocation.tryParse(requiredTag);
                if (tagRl == null) return false;
                net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tagKey =
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, tagRl);
                if (!state.is(tagKey)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int transferEnergy(@NotNull String dimension, int fromX, int fromY, int fromZ,
                              int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null || maxAmount <= 0) return 0;
        net.minecraft.core.BlockPos fromPos = new net.minecraft.core.BlockPos(fromX, fromY, fromZ);
        net.minecraft.core.BlockPos toPos = new net.minecraft.core.BlockPos(toX, toY, toZ);
        net.neoforged.neoforge.energy.IEnergyStorage fromStorage =
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, fromPos, null);
        net.neoforged.neoforge.energy.IEnergyStorage toStorage =
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, toPos, null);
        if (fromStorage == null || toStorage == null) return 0;
        int extracted = fromStorage.extractEnergy(maxAmount, true);
        if (extracted <= 0) return 0;
        int accepted = toStorage.receiveEnergy(extracted, simulate);
        if (accepted > 0 && !simulate) {
            fromStorage.extractEnergy(accepted, false);
        }
        return accepted;
    }

    @Override
    public int transferFluid(@NotNull String dimension, int fromX, int fromY, int fromZ,
                             int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null || maxAmount <= 0) return 0;
        net.minecraft.core.BlockPos fromPos = new net.minecraft.core.BlockPos(fromX, fromY, fromZ);
        net.minecraft.core.BlockPos toPos = new net.minecraft.core.BlockPos(toX, toY, toZ);
        net.neoforged.neoforge.fluids.capability.IFluidHandler fromHandler =
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, fromPos, null);
        net.neoforged.neoforge.fluids.capability.IFluidHandler toHandler =
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, toPos, null);
        if (fromHandler == null || toHandler == null) return 0;
        net.neoforged.neoforge.fluids.FluidStack drained =
                fromHandler.drain(maxAmount, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        if (drained.isEmpty()) return 0;
        int filled = toHandler.fill(drained, simulate
                ? net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE
                : net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0 && !simulate) {
            fromHandler.drain(filled, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        return filled;
    }

    @Override
    public int transferItem(@NotNull String dimension, int fromX, int fromY, int fromZ, int fromSlot,
                            int toX, int toY, int toZ, int toSlot, int maxCount, boolean simulate) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null || maxCount <= 0) return 0;
        net.minecraft.core.BlockPos fromPos = new net.minecraft.core.BlockPos(fromX, fromY, fromZ);
        net.minecraft.core.BlockPos toPos = new net.minecraft.core.BlockPos(toX, toY, toZ);
        net.neoforged.neoforge.items.IItemHandler fromHandler =
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, fromPos, null);
        net.neoforged.neoforge.items.IItemHandler toHandler =
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, toPos, null);
        if (fromHandler == null || toHandler == null) return 0;
        if (fromSlot < 0 || fromSlot >= fromHandler.getSlots()) return 0;
        net.minecraft.world.item.ItemStack simulatedExtract = fromHandler.extractItem(fromSlot, maxCount, true);
        if (simulatedExtract.isEmpty()) return 0;
        net.minecraft.world.item.ItemStack remaining;
        if (toSlot >= 0 && toSlot < toHandler.getSlots()) {
            remaining = toHandler.insertItem(toSlot, simulatedExtract, simulate);
        } else {
            remaining = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(toHandler, simulatedExtract, simulate);
        }
        int moved = simulatedExtract.getCount() - remaining.getCount();
        if (moved > 0 && !simulate) {
            fromHandler.extractItem(fromSlot, moved, false);
        }
        return moved;
    }

    private boolean matchesBlockCondition(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            Object condition) {
        if (condition == null) return true;
        if (condition instanceof String s) {
            if (s.equals("*")) return true;
            if (s.startsWith("#")) {
                net.minecraft.resources.ResourceLocation tagRl = net.minecraft.resources.ResourceLocation.tryParse(s.substring(1));
                if (tagRl == null) return false;
                net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tagKey =
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, tagRl);
                return state.is(tagKey);
            }
            String reqId = s.contains(":") ? s : "minecraft:" + s;
            String actualId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            return actualId.equalsIgnoreCase(reqId);
        }
        if (condition instanceof java.util.List<?> list) {
            for (Object item : list) {
                if (matchesBlockCondition(level, pos, state, item)) return true;
            }
            return false;
        }
        if (condition instanceof java.util.Map<?, ?> map) {
            Object blockObj = map.get("block");
            if (blockObj == null) blockObj = map.get("id");
            if (blockObj != null) {
                String reqId = blockObj.toString();
                if (!reqId.contains(":")) reqId = "minecraft:" + reqId;
                String actualId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if (!actualId.equalsIgnoreCase(reqId)) return false;
            }
            Object tagObj = map.get("tag");
            if (tagObj != null) {
                String tagStr = tagObj.toString();
                if (tagStr.startsWith("#")) tagStr = tagStr.substring(1);
                net.minecraft.resources.ResourceLocation tagRl = net.minecraft.resources.ResourceLocation.tryParse(tagStr);
                if (tagRl == null) return false;
                net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tagKey =
                        net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, tagRl);
                if (!state.is(tagKey)) return false;
            }
            Object propsObj = map.get("properties");
            if (propsObj instanceof java.util.Map<?, ?> propsMap) {
                for (java.util.Map.Entry<?, ?> entry : propsMap.entrySet()) {
                    String propName = entry.getKey().toString();
                    String expectedVal = entry.getValue().toString();
                    net.minecraft.world.level.block.state.properties.Property<?> prop =
                            state.getBlock().getStateDefinition().getProperty(propName);
                    if (prop == null) return false;
                    Comparable<?> actualVal = state.getValue(prop);
                    if (!actualVal.toString().equalsIgnoreCase(expectedVal)) return false;
                }
            }
            Object nbtObj = map.get("nbt");
            if (nbtObj instanceof java.util.Map<?, ?> nbtMap) {
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
                if (be == null) return false;
                java.util.Map<String, Object> data = NbtCodec.toMap(be.saveWithFullMetadata(level.registryAccess()));
                for (java.util.Map.Entry<?, ?> entry : nbtMap.entrySet()) {
                    String k = entry.getKey().toString();
                    Object expected = entry.getValue();
                    Object actual = data.get(k);
                    if (actual == null || !actual.toString().equals(expected.toString())) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public @NotNull java.util.Map<String, Object> validateCuboid(
            @NotNull String dimension,
            int x1, int y1, int z1,
            int x2, int y2, int z2,
            @NotNull java.util.Map<String, Object> spec) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) {
            return java.util.Map.of("valid", false, "error", "Dimension '" + dimension + "' is not loaded");
        }
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;

        if (spec.get("minWidth") instanceof Number n && width < n.intValue())
            return java.util.Map.of("valid", false, "error", "Width " + width + " is smaller than minWidth " + n.intValue());
        if (spec.get("maxWidth") instanceof Number n && width > n.intValue())
            return java.util.Map.of("valid", false, "error", "Width " + width + " is larger than maxWidth " + n.intValue());
        if (spec.get("minHeight") instanceof Number n && height < n.intValue())
            return java.util.Map.of("valid", false, "error", "Height " + height + " is smaller than minHeight " + n.intValue());
        if (spec.get("maxHeight") instanceof Number n && height > n.intValue())
            return java.util.Map.of("valid", false, "error", "Height " + height + " is larger than maxHeight " + n.intValue());
        if (spec.get("minDepth") instanceof Number n && depth < n.intValue())
            return java.util.Map.of("valid", false, "error", "Depth " + depth + " is smaller than minDepth " + n.intValue());
        if (spec.get("maxDepth") instanceof Number n && depth > n.intValue())
            return java.util.Map.of("valid", false, "error", "Depth " + depth + " is larger than maxDepth " + n.intValue());

        Object frameRule = spec.get("frame");
        Object wallRule = spec.get("walls");
        Object floorRule = spec.get("floor");
        Object ceilingRule = spec.get("ceiling");
        Object interiorRule = spec.get("interior");
        Object shellRule = spec.get("casing");
        if (shellRule == null) shellRule = spec.get("shell");
        if (shellRule == null) shellRule = spec.get("all");

        Object limitsObj = spec.get("limits");
        java.util.List<java.util.Map<String, Object>> limits = new java.util.ArrayList<>();
        if (limitsObj instanceof java.util.List<?> limitList) {
            for (Object item : limitList) {
                if (item instanceof java.util.Map<?, ?> m) {
                    java.util.Map<String, Object> limitMap = new java.util.HashMap<>();
                    for (var e : m.entrySet()) limitMap.put(e.getKey().toString(), e.getValue());
                    limitMap.put("_count", 0);
                    limits.add(limitMap);
                }
            }
        }

        java.util.Map<String, Integer> blockCounts = new java.util.LinkedHashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isChunkLoaded(level, x, z)) continue;
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    blockCounts.put(blockId, blockCounts.getOrDefault(blockId, 0) + 1);

                    for (java.util.Map<String, Object> limit : limits) {
                        Object matchObj = limit.get("match");
                        if (matchObj == null) matchObj = limit.get("block");
                        if (matchesBlockCondition(level, pos, state, matchObj)) {
                            int cnt = (int) limit.getOrDefault("_count", 0);
                            limit.put("_count", cnt + 1);
                        }
                    }

                    int boundaryCount = (x == minX || x == maxX ? 1 : 0)
                            + (y == minY || y == maxY ? 1 : 0)
                            + (z == minZ || z == maxZ ? 1 : 0);

                    boolean ruleMatched;
                    String ruleCategory;
                    if (boundaryCount >= 2) {
                        ruleCategory = "frame";
                        Object rule = frameRule != null ? frameRule : (wallRule != null ? wallRule : shellRule);
                        ruleMatched = matchesBlockCondition(level, pos, state, rule);
                    } else if (boundaryCount == 1) {
                        if (y == minY) {
                            ruleCategory = "floor";
                            Object rule = floorRule != null ? floorRule : (wallRule != null ? wallRule : shellRule);
                            ruleMatched = matchesBlockCondition(level, pos, state, rule);
                        } else if (y == maxY) {
                            ruleCategory = "ceiling";
                            Object rule = ceilingRule != null ? ceilingRule : (wallRule != null ? wallRule : shellRule);
                            ruleMatched = matchesBlockCondition(level, pos, state, rule);
                        } else {
                            ruleCategory = "wall";
                            Object rule = wallRule != null ? wallRule : shellRule;
                            ruleMatched = matchesBlockCondition(level, pos, state, rule);
                        }
                    } else {
                        ruleCategory = "interior";
                        ruleMatched = matchesBlockCondition(level, pos, state, interiorRule);
                    }

                    if (!ruleMatched) {
                        java.util.Map<String, Object> fail = new java.util.LinkedHashMap<>();
                        fail.put("valid", false);
                        fail.put("error", "Mismatch at " + x + ", " + y + ", " + z + " (" + ruleCategory + "): found " + blockId);
                        fail.put("failedPos", java.util.Map.of("X", x, "Y", y, "Z", z));
                        fail.put("category", ruleCategory);
                        fail.put("actualBlock", blockId);
                        return fail;
                    }
                }
            }
        }

        for (java.util.Map<String, Object> limit : limits) {
            int count = (int) limit.getOrDefault("_count", 0);
            if (limit.get("min") instanceof Number n && count < n.intValue()) {
                return java.util.Map.of("valid", false, "error", "Component " + limit.get("match") + " count " + count + " is less than minimum " + n.intValue());
            }
            if (limit.get("max") instanceof Number n && count > n.intValue()) {
                return java.util.Map.of("valid", false, "error", "Component " + limit.get("match") + " count " + count + " exceeds maximum " + n.intValue());
            }
        }

        java.util.Map<String, Object> success = new java.util.LinkedHashMap<>();
        success.put("valid", true);
        success.put("width", width);
        success.put("height", height);
        success.put("depth", depth);
        success.put("volume", width * height * depth);
        success.put("counts", blockCounts);
        success.put("minPos", java.util.Map.of("X", minX, "Y", minY, "Z", minZ));
        success.put("maxPos", java.util.Map.of("X", maxX, "Y", maxY, "Z", maxZ));
        return success;
    }

    @Override
    public int setBlockStatesInBox(
            @NotNull String dimension,
            int x1, int y1, int z1,
            int x2, int y2, int z2,
            @Nullable String filterBlockId,
            @NotNull java.util.Map<String, Object> properties) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null || properties.isEmpty()) return 0;
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        int updated = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!isChunkLoaded(level, x, z)) continue;
                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (filterBlockId != null && !filterBlockId.isEmpty() && !filterBlockId.equals("*")) {
                        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        String req = filterBlockId.contains(":") ? filterBlockId : "minecraft:" + filterBlockId;
                        if (!id.equalsIgnoreCase(req)) continue;
                    }
                    net.minecraft.world.level.block.state.BlockState newState = state;
                    for (java.util.Map.Entry<String, Object> entry : properties.entrySet()) {
                        net.minecraft.world.level.block.state.properties.Property<?> prop =
                                newState.getBlock().getStateDefinition().getProperty(entry.getKey());
                        if (prop != null) {
                            newState = setProperty(newState, prop, String.valueOf(entry.getValue()));
                        }
                    }
                    if (newState != state) {
                        level.setBlock(pos, newState, 3);
                        updated++;
                    }
                }
            }
        }
        return updated;
    }

    @Override
    public @NotNull java.util.List<java.util.Map<String, Object>> findBlocks(
            @NotNull String dimension,
            int centerX, int centerY, int centerZ,
            int radius,
            @NotNull String filter,
            int maxResults) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null || radius <= 0 || filter.isEmpty()) return java.util.List.of();
        int r = Math.min(radius, 48);
        int limit = maxResults > 0 ? Math.min(maxResults, 256) : 64;
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        double rSq = (double) r * r;

        int minX = centerX - r, maxX = centerX + r;
        int minY = Math.max(level.getMinBuildHeight(), centerY - r);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centerY + r);
        int minZ = centerZ - r, maxZ = centerZ + r;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!isChunkLoaded(level, x, z)) continue;
                for (int y = minY; y <= maxY; y++) {
                    double dx = x - centerX;
                    double dy = y - centerY;
                    double dz = z - centerZ;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > rSq) continue;

                    net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    if (matchesBlockCondition(level, pos, state, filter)) {
                        String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                        map.put("X", x);
                        map.put("Y", y);
                        map.put("Z", z);
                        map.put("Id", blockId);
                        map.put("Distance", Math.round(Math.sqrt(distSq) * 100.0) / 100.0);
                        results.add(map);
                        if (results.size() >= limit) {
                            return results;
                        }
                    }
                }
            }
        }
        return results;
    }

    @Override
    public boolean setEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return false;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof com.luatweaker.platform.container.CustomContainerBlockEntity container) {
            container.setEnergyStored(amount);
            return true;
        }
        return false;
    }

    @Override
    public int produceEnergy(@NotNull String dimension, int x, int y, int z, int amount) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null || amount <= 0) return 0;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof com.luatweaker.platform.container.CustomContainerBlockEntity container) {
            int current = container.getEnergyStored();
            int cap = container.getEnergyCapacity();
            int toAdd = Math.min(amount, Math.max(0, cap - current));
            if (toAdd > 0) {
                container.setEnergyStored(current + toAdd);
            }
            return toAdd;
        }
        return 0;
    }

    @Override
    public boolean setProgress(@NotNull String dimension, int x, int y, int z, float progress) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return false;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof com.luatweaker.platform.container.CustomContainerBlockEntity container) {
            container.setProgress(progress);
            return true;
        }
        return false;
    }

    @Override
    public float getProgress(@NotNull String dimension, int x, int y, int z) {
        net.minecraft.server.level.ServerLevel level = resolveLevel(dimension);
        if (level == null) return 0.0f;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof com.luatweaker.platform.container.CustomContainerBlockEntity container) {
            return container.getProgress();
        }
        return 0.0f;
    }

    @Override
    public boolean triggerGeoAnimation(@NotNull Object target, @NotNull String controllerName, @NotNull String animName, double speed, boolean loop) {
        String ctrl = (controllerName != null && !controllerName.isBlank()) ? controllerName : null;

        if (target instanceof IEntity ie) {
            target = ie.getRawEntity();
        }

        if (target instanceof software.bernie.geckolib.animatable.GeoEntity geoEntity) {
            geoEntity.triggerAnim(ctrl, animName);
            return true;
        }

        if (target instanceof net.minecraft.world.entity.LivingEntity living) {
            net.minecraft.world.item.ItemStack mainHand = living.getMainHandItem();
            if (mainHand.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
                long id = living.level() instanceof net.minecraft.server.level.ServerLevel sl
                        ? software.bernie.geckolib.animatable.GeoItem.getOrAssignId(mainHand, sl)
                        : software.bernie.geckolib.animatable.GeoItem.getId(mainHand);
                geoItem.triggerAnim(living, id, ctrl, animName);
                return true;
            }
            net.minecraft.world.item.ItemStack offHand = living.getOffhandItem();
            if (offHand.getItem() instanceof software.bernie.geckolib.animatable.GeoItem geoItem) {
                long id = living.level() instanceof net.minecraft.server.level.ServerLevel sl
                        ? software.bernie.geckolib.animatable.GeoItem.getOrAssignId(offHand, sl)
                        : software.bernie.geckolib.animatable.GeoItem.getId(offHand);
                geoItem.triggerAnim(living, id, ctrl, animName);
                return true;
            }
        }

        if (target instanceof com.luatweaker.api.interaction.IInteractableBlock block) {
            net.minecraft.server.level.ServerLevel level = resolveLevel(block.getDimension());
            if (level != null) {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(block.getX(), block.getY(), block.getZ());
                net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof software.bernie.geckolib.animatable.GeoBlockEntity geoBe) {
                    geoBe.triggerAnim(ctrl, animName);
                    return true;
                }
            }
        }

        if (target instanceof software.bernie.geckolib.animatable.GeoBlockEntity geoBe) {
            geoBe.triggerAnim(ctrl, animName);
            return true;
        }

        return false;
    }

    private net.minecraft.server.level.ServerLevel resolveLevel(@NotNull String dimension) {
        return ServerLevels.resolve(dimension);
    }
}
