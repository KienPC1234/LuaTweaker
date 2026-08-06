package com.luatweaker.platform.dimension;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Custom mob spawner for Lua dimensions.
 *
 * <p>Every {@code SPAWN_INTERVAL} server ticks each dimension that has a
 * full-control Lua handler is processed: the handler receives the dimension
 * id and the players inside it, and decides everything itself (which mobs,
 * where, when). The engine never spawns from its own rules - that is the
 * mod author's Lua code. The {@code MobSpawnAttempt} event stays cancellable
 * so other mods can veto a spawn.</p>
 */
public final class DimensionMobSpawner {

    private static final int SPAWN_INTERVAL = 100;

    private static int tickCounter = 0;

    private DimensionMobSpawner() {}

    /** Called from the server tick; cheap no-op outside the interval window. */
    public static void onServerTick() {
        if ((++tickCounter % SPAWN_INTERVAL) != 0) return;
        Object serviceObj = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        if (!(serviceObj instanceof DimensionServiceImpl service)) return;
        com.luatweaker.spawn.SpawnRuleServiceImpl spawnRules = currentSpawnRules();
        if (spawnRules == null) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (String dimensionId : service.getDimensionIds()) {
            if (spawnRules.getHandler(dimensionId) == null) continue;
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(dimensionId)));
            if (level == null) continue;
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
        com.luatweaker.api.vm.ILuaTable playersTable = spawnRules.getEngine().createTable();
        for (int i = 0; i < players.size(); i++) {
            com.luatweaker.platform.entity.NeoForgePlayerWrapper wrapper =
                    new com.luatweaker.platform.entity.NeoForgePlayerWrapper(players.get(i));
            playersTable.rawset(i + 1, com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(
                    spawnRules.getEngine(), wrapper));
        }
        com.luatweaker.api.vm.ILuaValue result = spawnRules.getEngine().callFunction(
                spawnRules.getHandler(dimensionId),
                spawnRules.getEngine().wrapString(dimensionId),
                playersTable);
        if (result == null || result.isNil() || !result.isTable()) return;
        com.luatweaker.api.vm.ILuaTable table = result.asTable();
        for (int i = 1; i <= table.length(); i++) {
            com.luatweaker.api.vm.ILuaValue entry = table.rawget(i);
            if (entry == null || !entry.isTable()) continue;
            com.luatweaker.api.vm.ILuaTable et = entry.asTable();
            com.luatweaker.api.vm.ILuaValue entityVal = et.rawget("entity");
            com.luatweaker.api.vm.ILuaValue xVal = et.rawget("x");
            com.luatweaker.api.vm.ILuaValue yVal = et.rawget("y");
            com.luatweaker.api.vm.ILuaValue zVal = et.rawget("z");
            if (entityVal == null || entityVal.isNil() || xVal == null || zVal == null) continue;
            Optional<EntityType<?>> type = EntityType.byString(entityVal.asString());
            if (type.isEmpty()) continue;
            BlockPos pos = new BlockPos(xVal.asInt(), yVal != null && !yVal.isNil() ? yVal.asInt()
                    : level.getHeight(Heightmap.Types.MOTION_BLOCKING, xVal.asInt(), zVal.asInt()) + 1, zVal.asInt());
            if (!level.isLoaded(pos)) continue;
            if (!fireSpawnAttempt(dimensionId, entityVal.asString(), pos, "handler")) continue;
            Entity entity = type.get().create(level);
            if (entity == null) continue;
            entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(entity);
            fireSpawned(dimensionId, entityVal.asString(), pos);
        }
    }

    /** Fires the cancellable "MobSpawnAttempt" event; false = cancelled. */
    private static boolean fireSpawnAttempt(String dimensionId, String entityId, BlockPos pos, String reason) {
        Object events = com.luatweaker.core.service.LuaServiceRegistry.get("Events");
        if (!(events instanceof com.luatweaker.api.event.IEventService eventService)) return true;
        com.luatweaker.api.vm.ILuaTable payload = eventService.getEngine().createTable();
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
        if (!(events instanceof com.luatweaker.api.event.IEventService eventService)) return;
        com.luatweaker.api.vm.ILuaTable payload = eventService.getEngine().createTable();
        payload.rawset("dimensionId", dimensionId);
        payload.rawset("entityId", entityId);
        payload.rawset("x", pos.getX());
        payload.rawset("y", pos.getY());
        payload.rawset("z", pos.getZ());
        eventService.fireEvent("MobSpawned", payload);
    }
}
