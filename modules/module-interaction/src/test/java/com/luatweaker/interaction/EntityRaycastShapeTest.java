package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.spatial.RaycastResult;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@code entity:raycast()} and {@code World:Raycast()} must hand scripts the same table. The
 * raw {@link RaycastResult} record would otherwise arrive as opaque userdata whose fields
 * ({@code res.IsHit}) read as nil.
 */
class EntityRaycastShapeTest {

    private static IEntity entityWith(RaycastResult result) {
        return (IEntity) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
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

    /** Boxed zero/false/'\0' for primitives, null for reference types. */
    private static Object defaultValue(Class<?> type) {
        return type.isPrimitive() ? Array.get(Array.newInstance(type, 1), 0) : null;
    }

    @Test
    void entityRaycastReturnsTheSharedResultTable() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        RaycastResult hit = new RaycastResult(
                true, 1.0, 2.0, 3.0, 0.0, 1.0, 0.0, "ENTITY", null, null, 4.5);
        ILuaTable table = LuaBinder.bindTable(engine, entityWith(hit), IEntity.class);
        engine.getGlobalEnvironment().rawset("target", table);

        assertDoesNotThrow(() -> engine.executeStringOrThrow("""
                local r = target:raycast(64, true)
                assert(type(r) == 'table', 'raycast must return a table, got ' .. type(r))
                assert(r.IsHit == true, 'IsHit must be readable')
                assert(r.HitType == 'ENTITY', 'HitType must be readable')
                assert(r.Position.X == 1 and r.Position.Y == 2 and r.Position.Z == 3,
                    'Position must be a vector table')
                assert(r.Distance == 4.5, 'Distance must round-trip')
                assert(r.Entity == nil, 'a blockless entity hit has no Entity payload')
                """, "entity_raycast_shape"),
                "a raycast hit must reach Lua as a table with IsHit/HitType/Position/Distance");
    }

    @Test
    void missReturnsTheSameShapeWithIsHitFalse() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        ILuaTable table = LuaBinder.bindTable(engine,
                entityWith(RaycastResult.miss(7.0, 8.0, 9.0)), IEntity.class);
        engine.getGlobalEnvironment().rawset("target", table);

        assertDoesNotThrow(() -> engine.executeStringOrThrow("""
                local r = target:raycast(64, true)
                assert(r.IsHit == false)
                assert(r.HitType == 'NONE')
                assert(r.Position.Z == 9, 'a miss reports the ray end position')
                """, "entity_raycast_miss"),
                "a missed raycast must report IsHit=false, HitType=NONE and the ray end position");
    }
}
