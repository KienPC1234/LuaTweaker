package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.Map;

/**
 * Roblox-style property access (entity.Health, entity.MaxHealth, entity.Type,
 * entity.CustomName, entity.Velocity, entity.Position) on top
 * of the generated method-style API, so both syntaxes work on the same table.
 */
final class EntityPropertyAliases {
    private static final String CAN_FLY = "canFly";

    /** Property name (both casings) to the backing getter bound on the table. */
    private static final Map<String, String> DIRECT_GETTERS = Map.ofEntries(
            Map.entry("Type", "getType"), Map.entry("type", "getType"),
            Map.entry("Name", "getName"), Map.entry("name", "getName"),
            Map.entry("Health", "getHealth"), Map.entry("health", "getHealth"),
            Map.entry("MaxHealth", "getMaxHealth"), Map.entry("maxHealth", "getMaxHealth"),
            Map.entry("CustomName", "getCustomName"), Map.entry("customName", "getCustomName"),
            Map.entry("Dimension", "getDimension"), Map.entry("dimension", "getDimension"),
            Map.entry("Width", "getWidth"), Map.entry("width", "getWidth"),
            Map.entry("Height", "getHeight"), Map.entry("height", "getHeight"),
            Map.entry("EyeHeight", "getEyeHeight"), Map.entry("eyeHeight", "getEyeHeight"),
            Map.entry("NoGravity", "isNoGravity"), Map.entry("noGravity", "isNoGravity"),
            Map.entry("Silent", "isSilent"), Map.entry("silent", "isSilent"),
            Map.entry("AirSupply", "getAirSupply"), Map.entry("airSupply", "getAirSupply"),
            Map.entry("MaxAirSupply", "getMaxAirSupply"), Map.entry("maxAirSupply", "getMaxAirSupply"),
            Map.entry("FireTicks", "getRemainingFireTicks"), Map.entry("fireTicks", "getRemainingFireTicks"),
            Map.entry("OnFire", "isOnFire"), Map.entry("onFire", "isOnFire"),
            Map.entry("Absorption", "getAbsorption"), Map.entry("absorption", "getAbsorption"),
            Map.entry("FallDistance", "getFallDistance"), Map.entry("fallDistance", "getFallDistance"),
            Map.entry("Vehicle", "getVehicle"), Map.entry("vehicle", "getVehicle"),
            Map.entry("IsPassenger", "isPassenger"), Map.entry("isPassenger", "isPassenger"),
            Map.entry("HasPassengers", "hasPassengers"), Map.entry("hasPassengers", "hasPassengers"),
            Map.entry("GameMode", "getGameMode"), Map.entry("gameMode", "getGameMode"),
            Map.entry("Level", "getLevel"), Map.entry("level", "getLevel"),
            Map.entry("TotalExperience", "getTotalExperience"), Map.entry("totalExperience", "getTotalExperience"),
            Map.entry("ExperienceProgress", "getExperienceProgress"), Map.entry("experienceProgress", "getExperienceProgress"),
            Map.entry("SelectedSlot", "getSelectedSlot"), Map.entry("selectedSlot", "getSelectedSlot"),
            Map.entry("CanFly", CAN_FLY), Map.entry(CAN_FLY, CAN_FLY),
            Map.entry("Flying", "isFlying"), Map.entry("flying", "isFlying")
    );

    /** Property name (both casings) to the backing setter bound on the table. */
    private static final Map<String, String> DIRECT_SETTERS = Map.ofEntries(
            Map.entry("Health", "setHealth"), Map.entry("health", "setHealth"),
            Map.entry("MaxHealth", "setMaxHealth"), Map.entry("maxHealth", "setMaxHealth"),
            Map.entry("CustomName", "setCustomName"), Map.entry("customName", "setCustomName"),
            Map.entry("NoGravity", "setNoGravity"), Map.entry("noGravity", "setNoGravity"),
            Map.entry("Silent", "setSilent"), Map.entry("silent", "setSilent"),
            Map.entry("AirSupply", "setAirSupply"), Map.entry("airSupply", "setAirSupply"),
            Map.entry("Absorption", "setAbsorption"), Map.entry("absorption", "setAbsorption"),
            Map.entry("FallDistance", "setFallDistance"), Map.entry("fallDistance", "setFallDistance"),
            Map.entry("GameMode", "setGameMode"), Map.entry("gameMode", "setGameMode"),
            Map.entry("Level", "setLevel"), Map.entry("level", "setLevel"),
            Map.entry("ExperienceProgress", "setExperienceProgress"), Map.entry("experienceProgress", "setExperienceProgress"),
            Map.entry("SelectedSlot", "setSelectedSlot"), Map.entry("selectedSlot", "setSelectedSlot"),
            Map.entry("CanFly", "setCanFly"), Map.entry("canFly", "setCanFly"),
            Map.entry("Flying", "setFlying"), Map.entry("flying", "setFlying")
    );

    /** Property name (both casings) to the vector-accepting method bound on the table. */
    private static final Map<String, String> VECTOR_SETTERS = Map.of(
            "Velocity", "setMotion", "velocity", "setMotion",
            "Position", "teleport", "position", "teleport");

    private EntityPropertyAliases() {}

    static void addPropertyAliases(ILuaEngine engine, ILuaTable table, IEntity entity) {
        ILuaTable meta = engine.createTable();

        meta.rawset("__index", args -> indexProperty(engine, table, entity, args[1].asString()));

        meta.rawset("__newindex", args -> {
            assignProperty(engine, table, entity, args[1].asString(), args[2]);
            return engine.nilValue();
        });

        meta.rawset("__eq", args -> {
            IEntity first = EntitiesLuaBinding.getEntityFromTable(args[1]);
            IEntity second = EntitiesLuaBinding.getEntityFromTable(args[2]);
            if (first != null && second != null) {
                return engine.wrapBoolean(first.getUuid().equals(second.getUuid()));
            }
            return engine.wrapBoolean(false);
        });

        table.setMetatable(meta);
    }

    private static ILuaValue indexProperty(ILuaEngine engine, ILuaTable table, IEntity entity, String key) {
        String getter = DIRECT_GETTERS.get(key);
        if (getter != null) {
            return engine.callFunction(table.rawget(getter), table);
        }
        ILuaValue vector = indexVector(engine, entity, key);
        if (vector != null) {
            return vector;
        }
        return indexUnmapped(engine, table, entity, key);
    }

    private static ILuaValue indexVector(ILuaEngine engine, IEntity entity, String key) {
        switch (key) {
            case "Position", "position":
                return buildVector3(engine, entity.getX(), entity.getY(), entity.getZ());
            case "Velocity", "velocity":
                return buildVector3(engine, entity.getMotionX(), entity.getMotionY(), entity.getMotionZ());
            case "LookVector", "lookVector":
                return buildVector3(engine, entity.getLookVectorX(), entity.getLookVectorY(),
                        entity.getLookVectorZ());
            default:
                return null;
        }
    }

    private static ILuaValue indexUnmapped(ILuaEngine engine, ILuaTable table, IEntity entity, String key) {
        ILuaValue existing = table.rawget(key);
        if (existing != null && !existing.isNil()) {
            return existing;
        }

        // Fallback to the engine's dynamic Java bridge for unknown properties
        // (e.g. modded fields). Lua indexing triggers the proxy __index metamethod,
        // which translates the property name into a Java getter call.
        Object rawJavaObj = entity.getRawEntity();
        if (rawJavaObj != null) {
            ILuaValue result = EntitiesLuaBinding.readDynamicProperty(engine, rawJavaObj, key);
            if (result != null) {
                return result;
            }
        }

        return engine.nilValue();
    }

    private static void assignProperty(ILuaEngine engine, ILuaTable table, IEntity entity,
                                       String key, ILuaValue val) {
        String setter = DIRECT_SETTERS.get(key);
        if (setter != null) {
            engine.callFunction(table.rawget(setter), table, val);
            return;
        }
        String vectorMethod = VECTOR_SETTERS.get(key);
        if (vectorMethod != null) {
            setVectorProperty(engine, table, val, vectorMethod);
            return;
        }
        assignUnmapped(engine, table, entity, key, val);
    }

    private static void setVectorProperty(ILuaEngine engine, ILuaTable table, ILuaValue val, String method) {
        if (val.isTable()) {
            ILuaTable vec = val.asTable();
            engine.callFunction(table.rawget(method), table,
                    vec.rawget("X"), vec.rawget("Y"), vec.rawget("Z"));
        }
    }

    private static void assignUnmapped(ILuaEngine engine, ILuaTable table, IEntity entity,
                                       String key, ILuaValue val) {
        // 1. Check if the table itself has it
        if (!table.rawget(key).isNil()) {
            table.rawset(key, val);
            return;
        }

        // 2. Fallback to the dynamic Java bridge setter. Lua assignment triggers
        // the proxy __newindex metamethod, which invokes the Java setter.
        Object rawJavaObj = entity.getRawEntity();
        if (rawJavaObj != null && EntitiesLuaBinding.writeDynamicProperty(engine, rawJavaObj, key, val)) {
            return;
        }

        // 3. Just set it on the table
        table.rawset(key, val);
    }

    private static ILuaValue buildVector3(ILuaEngine engine, double x, double y, double z) {
        ILuaValue v3Class = engine.getGlobalEnvironment().rawget("Vector3");
        if (v3Class != null && !v3Class.isNil()) {
            ILuaValue v3New = v3Class.asTable().rawget("new");
            if (v3New != null && !v3New.isNil()) {
                return engine.callFunction(v3New, v3Class,
                        engine.wrapNumber(x), engine.wrapNumber(y), engine.wrapNumber(z));
            }
        }
        ILuaTable fallback = engine.createTable();
        fallback.rawset("X", engine.wrapNumber(x));
        fallback.rawset("Y", engine.wrapNumber(y));
        fallback.rawset("Z", engine.wrapNumber(z));
        return fallback;
    }
}
