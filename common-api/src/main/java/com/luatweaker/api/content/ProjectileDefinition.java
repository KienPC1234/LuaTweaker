package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

/**
 * Definition of a custom projectile registered from Lua via
 * {@code Content.registerProjectile(id, config)}. The firing layer uses
 * {@code explosionPower} and {@code damage} to build the actual projectile.
 */
@LuaDoc(description = "Definition of a custom projectile: damage, explosion power and trail particle.")
public record ProjectileDefinition(
        @LuaDoc(description = "Base damage dealt by the projectile.", returnType = "number") double damage,
        @LuaDoc(description = "Explosion power (0 = no explosion, 1 = small, 2+ = large fireball).", returnType = "number") double explosionPower,
        @LuaDoc(description = "Trail particle id (e.g. 'minecraft:flame').", returnType = "string") String trailParticle
) {
    public ProjectileDefinition {
        if (damage < 0) throw new IllegalArgumentException("damage must be >= 0");
        if (explosionPower < 0) throw new IllegalArgumentException("explosionPower must be >= 0");
    }
}
