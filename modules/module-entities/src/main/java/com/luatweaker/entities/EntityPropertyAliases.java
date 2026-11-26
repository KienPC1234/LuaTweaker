package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Shared metatable and direct Java property dispatch for entity/player Lua tables
 * (Roblox-style properties alongside generated prototype methods).
 */
final class EntityPropertyAliases {

    private static final int MIN_EQ_ARGS = 2;
    private static final int MIN_NEWINDEX_ARGS = 3;

    private EntityPropertyAliases() {}

    /** Lazily resolved {@code Vector3.new} constructor cached per engine. */
    private static final class Vector3ConstructorCache {
        private ILuaValue vector3Class;
        private ILuaValue newFn;

        ILuaValue create(ILuaEngine engine, double x, double y, double z) {
            resolveIfNeeded(engine);
            if (newFn != null) {
                return engine.callFunction(newFn, vector3Class,
                        engine.wrapNumber(x), engine.wrapNumber(y), engine.wrapNumber(z));
            }
            ILuaTable fallback = engine.createTable();
            fallback.rawset("X", engine.wrapNumber(x));
            fallback.rawset("Y", engine.wrapNumber(y));
            fallback.rawset("Z", engine.wrapNumber(z));
            return fallback;
        }

        private void resolveIfNeeded(ILuaEngine engine) {
            if (newFn != null) {
                return;
            }
            ILuaValue v3Class = engine.getGlobalEnvironment().rawget("Vector3");
            if (v3Class == null || !v3Class.isTable()) {
                return;
            }
            ILuaValue v3New = v3Class.asTable().rawget("new");
            if (v3New != null && v3New.isFunction()) {
                this.vector3Class = v3Class;
                this.newFn = v3New;
            }
        }
    }

    static ILuaTable createSharedMetatable(@NotNull ILuaEngine engine,
                                           @NotNull ILuaTable prototype,
                                           boolean isPlayer) {
        ILuaTable meta = engine.createTable();
        Vector3ConstructorCache vector3Cache = new Vector3ConstructorCache();

        meta.rawset("__index", args -> {
            if (args.length < MIN_EQ_ARGS || !args[0].isTable()) {
                return engine.nilValue();
            }
            String key = args[1].asString();
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[0]);
            return indexProperty(engine, prototype, vector3Cache, entity, key, isPlayer);
        });

        meta.rawset("__newindex", args -> {
            if (args.length >= MIN_NEWINDEX_ARGS && args[0].isTable()) {
                assignProperty(engine, args[0].asTable(), EntitiesLuaBinding.getEntityFromTable(args[0]),
                        args[1].asString(), args[2], isPlayer);
            }
            return engine.nilValue();
        });

        meta.rawset("__eq", args -> {
            if (args.length < MIN_EQ_ARGS) {
                return engine.wrapBoolean(false);
            }
            IEntity first = EntitiesLuaBinding.getEntityFromTable(args[0]);
            IEntity second = EntitiesLuaBinding.getEntityFromTable(args[1]);
            boolean same = first != null && second != null && first.getUuid() != null
                    && first.getUuid().equals(second.getUuid());
            return engine.wrapBoolean(same);
        });

        return meta;
    }

    private static ILuaValue indexProperty(ILuaEngine engine, ILuaTable prototype,
                                           Vector3ConstructorCache vector3Cache,
                                           @Nullable IEntity entity, String key, boolean isPlayer) {
        ILuaValue protoMember = prototype.rawget(key);
        if (protoMember != null && !protoMember.isNil()) {
            return protoMember;
        }
        if (entity == null) {
            return engine.nilValue();
        }
        BiFunction<ILuaEngine, IEntity, ILuaValue> getter = EntityPropertyTable.ENTITY_GETTERS.get(key);
        if (getter != null) {
            return getter.apply(engine, entity);
        }
        if (isPlayer && entity instanceof IPlayer player) {
            BiFunction<ILuaEngine, IPlayer, ILuaValue> playerGetter = EntityPropertyTable.PLAYER_GETTERS.get(key);
            if (playerGetter != null) {
                return playerGetter.apply(engine, player);
            }
        }
        ILuaValue vector = indexVector(engine, vector3Cache, entity, key);
        if (vector != null) {
            return vector;
        }
        return indexDynamic(engine, entity, key);
    }

    @Nullable
    private static ILuaValue indexVector(ILuaEngine engine, Vector3ConstructorCache vector3Cache,
                                         IEntity entity, String key) {
        return switch (key) {
            case "Position", "position" -> vector3Cache.create(engine, entity.getX(), entity.getY(), entity.getZ());
            case "Velocity", "velocity" ->
                    vector3Cache.create(engine, entity.getMotionX(), entity.getMotionY(), entity.getMotionZ());
            case "LookVector", "lookVector" -> vector3Cache.create(
                    engine, entity.getLookVectorX(), entity.getLookVectorY(), entity.getLookVectorZ());
            default -> null;
        };
    }

    private static ILuaValue indexDynamic(ILuaEngine engine, IEntity entity, String key) {
        Object rawJavaObj = entity.getRawEntity();
        if (rawJavaObj != null) {
            ILuaValue result = EntitiesLuaBinding.readDynamicProperty(engine, rawJavaObj, key);
            if (result != null) {
                return result;
            }
        }
        return engine.nilValue();
    }

    private static void assignProperty(ILuaEngine engine, ILuaTable instanceTable,
                                       @Nullable IEntity entity, String key,
                                       ILuaValue val, boolean isPlayer) {
        if (entity == null) {
            instanceTable.rawset(key, val);
            return;
        }
        BiConsumer<IEntity, ILuaValue> setter = EntityPropertyTable.ENTITY_SETTERS.get(key);
        if (setter != null) {
            setter.accept(entity, val);
            return;
        }
        BiConsumer<IPlayer, ILuaValue> playerSetter = (isPlayer && entity instanceof IPlayer player)
                ? EntityPropertyTable.PLAYER_SETTERS.get(key)
                : null;
        if (playerSetter != null) {
            playerSetter.accept((IPlayer) entity, val);
            return;
        }
        if (assignVectorProperty(entity, key, val)) {
            return;
        }
        Object rawJavaObj = entity.getRawEntity();
        if (rawJavaObj != null && EntitiesLuaBinding.writeDynamicProperty(engine, rawJavaObj, key, val)) {
            return;
        }
        instanceTable.rawset(key, val);
    }

    private static boolean assignVectorProperty(IEntity entity, String key, ILuaValue val) {
        if ("Velocity".equals(key) || "velocity".equals(key)) {
            if (val.isTable()) {
                ILuaTable vec = val.asTable();
                entity.setMotion(vec.rawget("X").asDouble(), vec.rawget("Y").asDouble(), vec.rawget("Z").asDouble());
            }
            return true;
        }
        if ("Position".equals(key) || "position".equals(key)) {
            if (val.isTable()) {
                ILuaTable vec = val.asTable();
                entity.teleport(vec.rawget("X").asDouble(), vec.rawget("Y").asDouble(), vec.rawget("Z").asDouble());
            }
            return true;
        }
        return false;
    }
}
