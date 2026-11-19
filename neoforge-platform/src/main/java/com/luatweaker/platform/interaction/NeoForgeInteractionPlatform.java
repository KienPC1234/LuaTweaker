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

    private net.minecraft.server.level.ServerLevel resolveLevel(@NotNull String dimension) {
        return ServerLevels.resolve(dimension);
    }
}
