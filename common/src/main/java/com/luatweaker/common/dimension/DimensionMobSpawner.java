package com.luatweaker.common.dimension;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.dimension.DimensionServiceImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.phys.AABB;
import com.luatweaker.common.CommonServerHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Full-control mob spawner for Lua dimensions.
 *
 * <p>Every {@code SPAWN_INTERVAL} server ticks each dimension with a Lua handler is processed:
 * the handler receives the dimension id and the players inside it, and decides everything
 * itself (which mobs, where, when). Returned entries are validated server-side - the chunk
 * must be loaded, the entity's spawn placement must accept the block, the hitbox must fit, and
 * {@code FinalizeSpawnEvent} can still veto - so a script mistake cannot drop mobs inside stone
 * or through the world floor.</p>
 *
 * <p>Each entry: {@code { entity = 'minecraft:zombie', x = , y = ?, z = , count = ?, spread = ?,
 * maxNearby = ? }}. {@code y} defaults to the entity's valid surface position; {@code count}
 * spawns a group; {@code spread} randomises the horizontal offset; {@code maxNearby} caps how
 * many of that entity may already exist within 16 blocks.</p>
 */
public final class DimensionMobSpawner {

    // Engine-level tuning, not per-mod config: DimensionConfig carries only
    // terrain/visual/spawn data (no spawner interval field), so the tick
    // interval lives here as a named constant instead of a magic number.
    private static final int SPAWN_INTERVAL = 100;
    /** Hard cap so one handler entry cannot stall the tick by requesting thousands of mobs. */
    private static final int MAX_GROUP = 32;
    /** Hard cap for the horizontal jitter of one entry. */
    private static final int MAX_SPREAD = 16;
    /** Radius used by the optional maxNearby cap. */
    private static final int NEARBY_RADIUS = 16;

    private static int tickCounter;

    private DimensionMobSpawner() {}

    /** One validated spawn request returned by a Lua handler. */
    record SpawnRequest(@NotNull String entity, int x, @Nullable Integer y, int z,
                        int count, int spread, int maxNearby) {}

    /** Called from the server tick; cheap no-op outside the interval window. */
    public static void onServerTick() {
        tickCounter++;
        if (tickCounter % SPAWN_INTERVAL != 0) { return; }
        com.luatweaker.spawn.SpawnRuleServiceImpl spawnRules = currentSpawnRules();
        if (spawnRules == null) { return; }
        MinecraftServer server = CommonServerHolder.get();
        if (server == null) { return; }

        Object serviceObj = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        DimensionServiceImpl service = serviceObj instanceof DimensionServiceImpl s ? s : null;

        java.util.Set<String> dimensionsToProcess = new java.util.LinkedHashSet<>(spawnRules.getRegisteredDimensions());
        if (service != null) {
            dimensionsToProcess.addAll(service.getDimensionIds());
        }

        for (String dimensionId : dimensionsToProcess) {
            if (spawnRules.getHandler(dimensionId) == null) { continue; }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(dimensionId)));
            if (level == null) { continue; }
            spawnViaHandler(level, dimensionId, spawnRules);
        }
    }

    private static com.luatweaker.spawn.SpawnRuleServiceImpl currentSpawnRules() {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("SpawnRuleServiceImpl");
        return service instanceof com.luatweaker.spawn.SpawnRuleServiceImpl rules ? rules : null;
    }

    /** Handler-driven spawning: the Lua function receives (dimensionId, players) and returns spawn requests. */
    private static void spawnViaHandler(ServerLevel level, String dimensionId,
                                        com.luatweaker.spawn.SpawnRuleServiceImpl spawnRules) {
        List<ServerPlayer> players = level.getPlayers(player -> true);
        ILuaTable playersTable = spawnRules.getEngine().createTable();
        for (int i = 0; i < players.size(); i++) {
            playersTable.rawset(i + 1, playerLuaTable(spawnRules, players.get(i)));
        }
        ILuaValue result = spawnRules.getEngine().callFunction(
                spawnRules.getHandler(dimensionId),
                spawnRules.getEngine().wrapString(dimensionId),
                playersTable);
        if (result == null || result.isNil() || !result.isTable()) { return; }

        ILuaTable table = result.asTable();
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue entry = table.rawget(i);
            if (entry == null || entry.isNil() || !entry.isTable()) { continue; }
            SpawnRequest request = parseRequest(entry.asTable());
            if (request == null) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Spawn handler for '" + dimensionId
                                + "' returned an invalid entry (needs entity, x, z); ignoring it");
                continue;
            }
            spawnGroup(level, dimensionId, request);
        }
    }

    /** Wraps one player for the handler; extracted so no wrapper is allocated in the loop. */
    private static ILuaTable playerLuaTable(com.luatweaker.spawn.SpawnRuleServiceImpl spawnRules,
                                             ServerPlayer player) {
        com.luatweaker.common.entity.MinecraftPlayerWrapper wrapper =
                new com.luatweaker.common.entity.MinecraftPlayerWrapper(player);
        return com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(
                spawnRules.getEngine(), wrapper);
    }

    /** Parses one handler entry; null when it has no entity or no usable coordinates. */
    static @Nullable SpawnRequest parseRequest(@NotNull ILuaTable entry) {
        ILuaValue entityVal = entry.rawget("entity");
        if (entityVal == null || entityVal.isNil() || !entityVal.isString()) { return null; }
        Integer x = blockCoord(entry.rawget("x"));
        Integer z = blockCoord(entry.rawget("z"));
        if (x == null || z == null) { return null; }
        Integer y = blockCoord(entry.rawget("y"));
        return new SpawnRequest(entityVal.asString(), x, y, z,
                clampedInt(entry.rawget("count"), 1, 1, MAX_GROUP),
                clampedInt(entry.rawget("spread"), 0, 0, MAX_SPREAD),
                clampedInt(entry.rawget("maxNearby"), 0, 0, MAX_GROUP));
    }

    private static void spawnGroup(ServerLevel level, String dimensionId, SpawnRequest request) {
        Optional<EntityType<?>> resolved =
                com.luatweaker.common.interaction.EntitySpawner.resolveEntityType(request.entity());
        if (resolved.isEmpty()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Spawn handler for '" + dimensionId + "' references unknown entity '"
                            + request.entity() + "'");
            return;
        }
        EntityType<?> type = resolved.get();
        int spawned = 0;
        for (int i = 0; i < request.count(); i++) {
            int outcome = spawnGroupEntry(level, dimensionId, request, type);
            if (outcome < 0) { break; }
            if (outcome > 0) { spawned++; }
        }
        if (spawned < request.count() && request.count() > 1) {
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "Spawn handler for '" + dimensionId + "' spawned " + spawned + "/"
                            + request.count() + " of " + request.entity()
                            + " (the rest failed ground/collision/spawn-placement checks)");
        }
    }

    /**
     * One attempt of a handler group. Returns {@code -1} when the nearby cap says stop,
     * {@code 1} when the entity spawned and {@code 0} when the position was rejected.
     */
    private static int spawnGroupEntry(ServerLevel level, String dimensionId,
                                       SpawnRequest request, EntityType<?> type) {
        int x = request.x() + jitter(level, request.spread());
        int z = request.z() + jitter(level, request.spread());
        int y = resolveY(level, type, request.y(), x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (request.maxNearby() > 0 && countNearby(level, type, pos) >= request.maxNearby()) {
            return -1;
        }
        return spawnOne(level, dimensionId, request.entity(), type, pos, "handler") ? 1 : 0;
    }

    /**
     * Force-spawns one entity at a position through the same validation the handler uses.
     * Shared by handlers, {@code /lt spawn} and tools. Returns false when the position or the
     * event hooks rejected the spawn.
     */
    public static boolean spawnEntity(@NotNull ServerLevel level, @NotNull String dimensionId,
                                      @NotNull String entityId, @NotNull BlockPos pos) {
        Optional<EntityType<?>> resolved =
                com.luatweaker.common.interaction.EntitySpawner.resolveEntityType(entityId);
        if (resolved.isEmpty()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Cannot spawn unknown entity '" + entityId + "' in '" + dimensionId + "'");
            return false;
        }
        EntityType<?> type = resolved.get();
        int y = resolveY(level, type, pos.getY(), pos.getX(), pos.getZ());
        return spawnOne(level, dimensionId, entityId, type, new BlockPos(pos.getX(), y, pos.getZ()), "command");
    }

    private static boolean spawnOne(ServerLevel level, String dimensionId, String entityId,
                                    EntityType<?> type, BlockPos pos, String reason) {
        if (!canSpawnAt(level, type, pos)) { return false; }
        if (!fireSpawnAttempt(dimensionId, entityId, pos, reason)) { return false; }
        Entity entity = type.create(level);
        if (entity == null) { return false; }
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                level.random.nextFloat() * 360.0F, 0.0F);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
        }
        if (!level.addFreshEntity(entity)) { return false; }
        fireSpawned(dimensionId, entityId, pos);
        return true;
    }

    private static int jitter(ServerLevel level, int spread) {
        return spread <= 0 ? 0 : level.random.nextInt(spread * 2 + 1) - spread;
    }

    /** Resolves the spawn Y: the requested one, or the entity's valid placement near the surface. */
    private static int resolveY(ServerLevel level, EntityType<?> type, @Nullable Integer requested, int x, int z) {
        if (requested != null) { return requested; }
        BlockPos surface = new BlockPos(x,
                level.getHeight(SpawnPlacements.getHeightmapType(type), x, z), z);
        return SpawnPlacements.getPlacementType(type).adjustSpawnPosition(level, surface).getY();
    }

    /**
     * Server-side ground and collision validation. A requested {@code y} is respected, so a
     * handler can spawn in caves; only positions that cannot hold the entity are rejected.
     */
    private static boolean canSpawnAt(ServerLevel level, EntityType<?> type, BlockPos pos) {
        if (!level.isLoaded(pos)) { return false; }
        if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) { return false; }
        AABB box = type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return level.noCollision(box);
    }

    private static int countNearby(ServerLevel level, EntityType<?> type, BlockPos pos) {
        AABB area = new AABB(pos).inflate(NEARBY_RADIUS, NEARBY_RADIUS / 2.0, NEARBY_RADIUS);
        return level.getEntitiesOfClass(Entity.class, area, entity -> java.util.Objects.equals(entity.getType(), type)).size();
    }

    /**
     * Converts a Lua coordinate to a block coordinate by flooring it. Lua numbers are doubles:
     * {@code ILuaValue#asInt()} throws "number has no integer representation" for values such as
     * {@code player.X + math.random(-8, 8)}, which used to crash the server tick.
     *
     * @return the block coordinate, or {@code null} when the value is not a finite world coordinate
     */
    static @Nullable Integer blockCoord(ILuaValue value) {
        if (value == null || value.isNil() || !value.isNumber()) { return null; }
        double raw = value.asDouble();
        if (!Double.isFinite(raw) || raw < -30_000_000.0 || raw > 30_000_000.0) { return null; }
        return (int) Math.floor(raw);
    }

    /** Reads an optional count-like field and clamps it into [min, max]. */
    static int clampedInt(@Nullable ILuaValue value, int fallback, int min, int max) {
        if (value == null || value.isNil() || !value.isNumber()) { return fallback; }
        double raw = value.asDouble();
        if (!Double.isFinite(raw)) { return fallback; }
        int parsed = (int) Math.floor(raw);
        return Math.clamp(parsed, min, max);
    }

    /** Fires the cancellable "MobSpawnAttempt" event; false = cancelled. */
    private static boolean fireSpawnAttempt(String dimensionId, String entityId, BlockPos pos, String reason) {
        Object events = com.luatweaker.core.service.LuaServiceRegistry.get("Events");
        if (!(events instanceof com.luatweaker.api.event.IEventService eventService)) { return true; }
        ILuaTable payload = eventService.getEngine().createTable();
        payload.rawset("dimensionId", dimensionId);
        payload.rawset("entityId", entityId);
        payload.rawset("x", pos.getX());
        payload.rawset("y", pos.getY());
        payload.rawset("z", pos.getZ());
        payload.rawset("reason", reason);
        return eventService.fireCancellable("MobSpawnAttempt", payload);
    }

    /** Fires the "MobSpawned" event after a successful spawn. */
    private static void fireSpawned(String dimensionId, String entityId, BlockPos pos) {
        Object events = com.luatweaker.core.service.LuaServiceRegistry.get("Events");
        if (!(events instanceof com.luatweaker.api.event.IEventService eventService)) { return; }
        ILuaTable payload = eventService.getEngine().createTable();
        payload.rawset("dimensionId", dimensionId);
        payload.rawset("entityId", entityId);
        payload.rawset("x", pos.getX());
        payload.rawset("y", pos.getY());
        payload.rawset("z", pos.getZ());
        eventService.fireEvent("MobSpawned", payload);
    }
}
