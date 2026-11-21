package com.luatweaker.platform.interaction;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Entity-type resolution and world spawning for the Lua entity API. */
final class EntitySpawner {

    /** Squared distance (64 blocks) under which a spawning entity prefers that level. */
    private static final double NEARBY_PLAYER_DISTANCE_SQ = 4096.0;
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final String LUATWEAKER_NAMESPACE = "luatweaker";

    private EntitySpawner() {
    }

    static Optional<net.minecraft.world.entity.EntityType<?>> resolveEntityType(String entityId) {
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

    static Object spawnEntity(@NotNull String entityId, double x, double y, double z) {
        return spawnIn(ServerLifecycleHooks.getCurrentServer(), entityId, x, y, z);
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

