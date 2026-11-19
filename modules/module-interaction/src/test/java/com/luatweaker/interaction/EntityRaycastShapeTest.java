package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.spatial.RaycastResult;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

/**
 * {@code entity:raycast()} and {@code World:Raycast()} must hand scripts the same table. The
 * raw {@link RaycastResult} record would otherwise arrive as opaque userdata whose fields
 * ({@code res.IsHit}) read as nil.
 */
class EntityRaycastShapeTest {

    private static IEntity entityWith(RaycastResult result) {
        return (IEntity) Proxy.newProxyInstance(
                EntityRaycastShapeTest.class.getClassLoader(),
                new Class<?>[]{ IEntity.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "raycast" -> result;
                    case "getType" -> "minecraft:pig";
                    case "getName" -> "Pig";
                    case "isAlive", "isPlayer", "isLiving" -> true;
                    case "getHealth", "getMaxHealth" -> 10.0f;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }

    @Test
    void entityRaycastReturnsTheSharedResultTable() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        RaycastResult hit = new RaycastResult(
                true, 1.0, 2.0, 3.0, 0.0, 1.0, 0.0, "ENTITY", null, null, 4.5);
        ILuaTable table = LuaBinder.bindTable(engine, entityWith(hit), IEntity.class);
        engine.getGlobalEnvironment().rawset("target", table);

        engine.executeStringOrThrow(
                "local r = target:raycast(64, true)\n" +
                "assert(type(r) == 'table', 'raycast must return a table, got ' .. type(r))\n" +
                "assert(r.IsHit == true, 'IsHit must be readable')\n" +
                "assert(r.HitType == 'ENTITY', 'HitType must be readable')\n" +
                "assert(r.Position.X == 1 and r.Position.Y == 2 and r.Position.Z == 3,\n" +
                "    'Position must be a vector table')\n" +
                "assert(r.Distance == 4.5, 'Distance must round-trip')\n" +
                "assert(r.Entity == nil, 'a blockless entity hit has no Entity payload')",
                "entity_raycast_shape");
    }

    @Test
    void missReturnsTheSameShapeWithIsHitFalse() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        ILuaTable table = LuaBinder.bindTable(engine,
                entityWith(RaycastResult.miss(7.0, 8.0, 9.0)), IEntity.class);
        engine.getGlobalEnvironment().rawset("target", table);

        engine.executeStringOrThrow(
                "local r = target:raycast(64, true)\n" +
                "assert(r.IsHit == false)\n" +
                "assert(r.HitType == 'NONE')\n" +
                "assert(r.Position.Z == 9, 'a miss reports the ray end position')",
                "entity_raycast_miss");
    }
}
