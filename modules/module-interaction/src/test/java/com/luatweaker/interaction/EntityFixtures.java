package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;

/**
 * Minimal read-only {@link IEntity} double for query tests: only the identity/position accessors
 * return meaningful values, mutations are no-ops.
 */
final class EntityFixtures {

    private EntityFixtures() {
    }

    static IEntity simple(String type, String uuid, double x, double y, double z) {
        return new IEntity() {
            @Override public String getType() { return type; }
            @Override public String getName() { return type; }
            @Override public String getUuid() { return uuid; }
            @Override public float getHealth() { return 20.0f; }
            @Override public void setHealth(float health) { }
            @Override public float getMaxHealth() { return 20.0f; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() { }
            @Override public double getX() { return x; }
            @Override public double getY() { return y; }
            @Override public double getZ() { return z; }
            @Override public Object getRawEntity() { return null; }
        };
    }
}
