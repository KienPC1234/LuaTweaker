package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The default look-vector implementation is what every entity uses (the platform wrapper relies
 * on it), so it must match Minecraft's {@code Entity#getLookAngle()}: yaw 0 faces +Z, yaw 90
 * faces -X, positive pitch looks down.
 */
class EntityLookVectorTest {

    private static IEntity entityAt(float yaw, float pitch) {
        return new IEntity() {
            @Override public String getType() { return "minecraft:pig"; }
            @Override public String getName() { return "Pig"; }
            @Override public float getHealth() { return 10; }
            @Override public void setHealth(float health) { }
            @Override public float getMaxHealth() { return 10; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() { }
            @Override public Object getRawEntity() { return this; }
            @Override public float getYaw() { return yaw; }
            @Override public float getPitch() { return pitch; }
        };
    }

    private static void assertVector(IEntity entity, double x, double y, double z) {
        assertEquals(x, entity.getLookVectorX(), 1e-6, "look X");
        assertEquals(y, entity.getLookVectorY(), 1e-6, "look Y");
        assertEquals(z, entity.getLookVectorZ(), 1e-6, "look Z");
    }

    @Test
    void yawZeroFacesPositiveZ() {
        assertVector(entityAt(0f, 0f), 0.0, 0.0, 1.0);
    }

    @Test
    void yawNinetyFacesNegativeX() {
        assertVector(entityAt(90f, 0f), -1.0, 0.0, 0.0);
    }

    @Test
    void positivePitchLooksDownAndNegativeLooksUp() {
        assertVector(entityAt(0f, 90f), 0.0, -1.0, 0.0);
        assertVector(entityAt(0f, -90f), 0.0, 1.0, 0.0);
    }

    @Test
    void diagonalLookStaysUnitLength() {
        IEntity entity = entityAt(45f, 30f);
        double length = Math.sqrt(entity.getLookVectorX() * entity.getLookVectorX()
                + entity.getLookVectorY() * entity.getLookVectorY()
                + entity.getLookVectorZ() * entity.getLookVectorZ());
        assertEquals(1.0, length, 1e-6, "a look vector must stay normalized");
    }
}
