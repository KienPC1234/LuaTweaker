package com.luatweaker.content;

import com.luatweaker.api.content.ProjectileDefinition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of custom projectile definitions registered from Lua via
 * {@code Content.registerProjectile(id, config)}. The NeoForge firing layer
 * reads these when launching a projectile so damage/explosionPower actually
 * affect the entity created.
 */
public final class ProjectileRegistry {
    private static final Map<String, ProjectileDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private ProjectileRegistry() {}

    public static void register(String id, ProjectileDefinition definition) {
        if (id == null || id.isBlank() || definition == null) return;
        DEFINITIONS.put(id, definition);
    }

    public static ProjectileDefinition get(String id) {
        return id == null ? null : DEFINITIONS.get(id);
    }

    public static Map<String, ProjectileDefinition> getAll() {
        return Map.copyOf(DEFINITIONS);
    }
}
