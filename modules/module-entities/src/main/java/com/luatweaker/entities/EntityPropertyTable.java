package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Direct Java property dispatch maps (both PascalCase and camelCase) backing
 * {@link EntityPropertyAliases} without reflection or Lua round-trips.
 */
final class EntityPropertyTable {

    static final Map<String, BiFunction<ILuaEngine, IEntity, ILuaValue>> ENTITY_GETTERS = buildEntityGetters();
    static final Map<String, BiFunction<ILuaEngine, IPlayer, ILuaValue>> PLAYER_GETTERS = buildPlayerGetters();
    static final Map<String, BiConsumer<IEntity, ILuaValue>> ENTITY_SETTERS = buildEntitySetters();
    static final Map<String, BiConsumer<IPlayer, ILuaValue>> PLAYER_SETTERS = buildPlayerSetters();

    private EntityPropertyTable() {}

    private static <V> void putAlias(Map<String, V> map, String pascal, V handler) {
        map.put(pascal, handler);
        map.put(Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1), handler);
    }

    private static ILuaValue wrapStringOrNil(ILuaEngine engine, @Nullable String value) {
        return value == null ? engine.nilValue() : engine.wrapString(value);
    }

    private static Map<String, BiFunction<ILuaEngine, IEntity, ILuaValue>> buildEntityGetters() {
        Map<String, BiFunction<ILuaEngine, IEntity, ILuaValue>> map = new ConcurrentHashMap<>();
        putAlias(map, "Type", (e, ent) -> wrapStringOrNil(e, ent.getType()));
        putAlias(map, "Name", (e, ent) -> wrapStringOrNil(e, ent.getName()));
        putAlias(map, "Health", (e, ent) -> e.wrapNumber(ent.getHealth()));
        putAlias(map, "MaxHealth", (e, ent) -> e.wrapNumber(ent.getMaxHealth()));
        putAlias(map, "CustomName", (e, ent) -> wrapStringOrNil(e, ent.getCustomName()));
        putAlias(map, "Dimension", (e, ent) -> wrapStringOrNil(e, ent.getDimension()));
        putAlias(map, "Width", (e, ent) -> e.wrapNumber(ent.getWidth()));
        putAlias(map, "Height", (e, ent) -> e.wrapNumber(ent.getHeight()));
        putAlias(map, "EyeHeight", (e, ent) -> e.wrapNumber(ent.getEyeHeight()));
        putAlias(map, "NoGravity", (e, ent) -> e.wrapBoolean(ent.isNoGravity()));
        putAlias(map, "Silent", (e, ent) -> e.wrapBoolean(ent.isSilent()));
        putAlias(map, "AirSupply", (e, ent) -> e.wrapNumber(ent.getAirSupply()));
        putAlias(map, "MaxAirSupply", (e, ent) -> e.wrapNumber(ent.getMaxAirSupply()));
        putAlias(map, "FireTicks", (e, ent) -> e.wrapNumber(ent.getRemainingFireTicks()));
        putAlias(map, "OnFire", (e, ent) -> e.wrapBoolean(ent.isOnFire()));
        putAlias(map, "Absorption", (e, ent) -> e.wrapNumber(ent.getAbsorption()));
        putAlias(map, "FallDistance", (e, ent) -> e.wrapNumber(ent.getFallDistance()));
        putAlias(map, "Vehicle", (e, ent) -> {
            IEntity vehicle = ent.getVehicle();
            return vehicle == null ? e.nilValue() : EntitiesLuaBinding.createEntityLuaTable(e, vehicle);
        });
        putAlias(map, "IsPassenger", (e, ent) -> e.wrapBoolean(ent.isPassenger()));
        putAlias(map, "HasPassengers", (e, ent) -> e.wrapBoolean(ent.hasPassengers()));
        putAlias(map, "Flying", (e, ent) -> e.wrapBoolean(ent.isFlying()));
        return Map.copyOf(map);
    }

    private static Map<String, BiFunction<ILuaEngine, IPlayer, ILuaValue>> buildPlayerGetters() {
        Map<String, BiFunction<ILuaEngine, IPlayer, ILuaValue>> map = new ConcurrentHashMap<>();
        putAlias(map, "GameMode", (e, p) -> wrapStringOrNil(e, p.getGameMode()));
        putAlias(map, "Level", (e, p) -> e.wrapNumber(p.getLevel()));
        putAlias(map, "TotalExperience", (e, p) -> e.wrapNumber(p.getTotalExperience()));
        putAlias(map, "ExperienceProgress", (e, p) -> e.wrapNumber(p.getExperienceProgress()));
        putAlias(map, "SelectedSlot", (e, p) -> e.wrapNumber(p.getSelectedSlot()));
        putAlias(map, "CanFly", (e, p) -> e.wrapBoolean(p.canFly()));
        return Map.copyOf(map);
    }

    private static Map<String, BiConsumer<IEntity, ILuaValue>> buildEntitySetters() {
        Map<String, BiConsumer<IEntity, ILuaValue>> map = new ConcurrentHashMap<>();
        putAlias(map, "Health", (ent, v) -> ent.setHealth((float) v.asDouble()));
        putAlias(map, "MaxHealth", (ent, v) -> ent.setMaxHealth((float) v.asDouble()));
        putAlias(map, "CustomName", (ent, v) -> ent.setCustomName(v.isNil() ? "" : v.asString()));
        putAlias(map, "NoGravity", (ent, v) -> ent.setNoGravity(v.asBoolean()));
        putAlias(map, "Silent", (ent, v) -> ent.setSilent(v.asBoolean()));
        putAlias(map, "AirSupply", (ent, v) -> ent.setAirSupply(v.asInt()));
        putAlias(map, "Absorption", (ent, v) -> ent.setAbsorption((float) v.asDouble()));
        putAlias(map, "FallDistance", (ent, v) -> ent.setFallDistance((float) v.asDouble()));
        return Map.copyOf(map);
    }

    private static Map<String, BiConsumer<IPlayer, ILuaValue>> buildPlayerSetters() {
        Map<String, BiConsumer<IPlayer, ILuaValue>> map = new ConcurrentHashMap<>();
        putAlias(map, "GameMode", (p, v) -> p.setGameMode(v.asString()));
        putAlias(map, "Level", (p, v) -> p.setLevel(v.asInt()));
        putAlias(map, "ExperienceProgress", (p, v) -> p.setExperienceProgress((float) v.asDouble()));
        putAlias(map, "SelectedSlot", (p, v) -> p.setSelectedSlot(v.asInt()));
        putAlias(map, "CanFly", (p, v) -> p.setCanFly(v.asBoolean()));
        putAlias(map, "Flying", (p, v) -> p.setFlying(v.asBoolean()));
        return Map.copyOf(map);
    }
}
