package com.luatweaker.platform.interaction;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Entity-type resolution and world spawning for the Lua entity API. */
public final class EntitySpawner {

    /** Squared distance (64 blocks) under which a spawning entity prefers that level. */
    private static final double NEARBY_PLAYER_DISTANCE_SQ = 4096.0;
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String LUATWEAKER_NAMESPACE = "luatweaker";

    private EntitySpawner() {
    }

    public static Optional<net.minecraft.world.entity.EntityType<?>> resolveEntityType(String entityId) {
        if (entityId == null || entityId.isBlank()) {
            return Optional.empty();
        }
        if (entityId.contains(":")) {
            ResourceLocation id = ResourceLocation.tryParse(entityId);
            return id != null ? BuiltInRegistries.ENTITY_TYPE.getOptional(id) : Optional.empty();
        }
        ResourceLocation mcId = ResourceLocation.fromNamespaceAndPath(MINECRAFT_NAMESPACE, entityId);
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(mcId)) {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(mcId);
        }
        ResourceLocation ltId = ResourceLocation.fromNamespaceAndPath(LUATWEAKER_NAMESPACE, entityId);
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(ltId)) {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(ltId);
        }
        return Optional.empty();
    }

    @Nullable
    static com.luatweaker.api.interaction.IInteractableEntity findInteractable(@Nullable MinecraftServer server,
                                                                              @NotNull java.util.UUID id) {
        if (server == null) {
            return null;
        }
        java.util.Iterator<ServerLevel> levels = server.getAllLevels().iterator();
        while (levels.hasNext()) {
            Entity entity = levels.next().getEntity(id);
            if (entity != null) {
                return new NeoForgeInteractableEntity(entity);
            }
        }
        return null;
    }

    public static Object spawnEntity(@NotNull String entityId, double x, double y, double z) {
        return spawnIn(ServerLifecycleHooks.getCurrentServer(), entityId, x, y, z);
    }

    /**
     * Restores an entity from a full NBT snapshot. {@code EntityType.create} applies the tag
     * (inventory, effects, attributes, owner, trades), then the entity is moved to the requested
     * position so a snapshot taken at death can be re-spawned anywhere.
     */
    @Nullable
    public static Object spawnFromNbt(@NotNull String dimension, double x, double y, double z,
                                      @NotNull String nbtJson) {        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        ServerLevel level = resolveLevel(server, dimension);
        if (level == null) {
            log(LogStage.SYSTEM, "SpawnFromNbt ignored: unknown dimension '" + dimension + "'");
            return null;
        }
        try {
            CompoundTag tag = TagParser.parseTag(nbtJson);
            Entity entity = net.minecraft.world.entity.EntityType.create(tag, level).orElse(null);
            if (entity == null) {
                log(LogStage.SYSTEM, "SpawnFromNbt ignored: snapshot has no valid entity id");
                return null;
            }
            entity.moveTo(x, y, z, entity.getYRot(), entity.getXRot());
            level.addFreshEntity(entity);
            return entity;
        } catch (CommandSyntaxException e) {
            log(LogStage.SYSTEM, "SpawnFromNbt rejected malformed NBT: " + e.getMessage());
            return null;
        }
    }

    private static @Nullable ServerLevel resolveLevel(@NotNull MinecraftServer server, @NotNull String dimension) {
        if (dimension.isBlank()) {
            return server.overworld();
        }
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static void log(LogStage stage, @NotNull String message) {
        LuaTweakerLog.get().warn(stage, message);
    }

    @Nullable
    private static Object spawnIn(@Nullable MinecraftServer server, String entityId,
                                  double x, double y, double z) {
        if (server == null) {
            return null;
        }
        Optional<net.minecraft.world.entity.EntityType<?>> type = resolveEntityType(entityId);
        if (type.isEmpty()) {
            return null;
        }
        Entity entity = type.get().create(pickSpawnLevel(server, x, y, z));
        if (entity == null) {
            return null;
        }
        entity.moveTo(x, y, z, 0.0f, 0.0f);
        entity.level().addFreshEntity(entity);
        return entity;
    }

    private static ServerLevel pickSpawnLevel(MinecraftServer server, double x, double y, double z) {
        return java.util.stream.StreamSupport.stream(server.getAllLevels().spliterator(), false)
                .filter(level -> !level.getPlayers(p -> p.distanceToSqr(x, y, z) < NEARBY_PLAYER_DISTANCE_SQ).isEmpty())
                .findFirst()
                .orElse(server.overworld());
    }
}

