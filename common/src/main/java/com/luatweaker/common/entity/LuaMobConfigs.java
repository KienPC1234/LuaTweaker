package com.luatweaker.common.entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime snapshots of custom-mob builder config, keyed by entity type id.
 * Populated once at {@code RegisterEvent(ENTITY_TYPE)} time; read by
 * {@link LuaCustomMob} instances (sounds, default AI) and renderers without
 * touching the content service on hot paths.
 */
public final class LuaMobConfigs {
    private LuaMobConfigs() {}

    public record Snapshot(
            String rig,
            String defaultGoals,
            String ambientSound,
            String hurtSound,
            String deathSound) {}

    private static final Map<String, Snapshot> BY_TYPE_ID = new ConcurrentHashMap<>();

    public static void put(String typeId, Snapshot snapshot) {
        if (typeId != null && snapshot != null) {
            BY_TYPE_ID.put(typeId, snapshot);
        }
    }

    public static Snapshot get(String typeId) {
        return typeId == null ? null : BY_TYPE_ID.get(typeId);
    }

    public static void clear() {
        BY_TYPE_ID.clear();
    }
}
