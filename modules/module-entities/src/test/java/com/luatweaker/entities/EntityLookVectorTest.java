package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The default look-vector implementation is what every entity uses (the platform wrapper relies
 * on it), so it must match Minecraft's {@code Entity#getLookAngle()}: yaw 0 faces +Z, yaw 90
 * faces -X, positive pitch looks down.
 */
class EntityLookVectorTest {

    private static final double EPSILON = 1e-6;

    @Test
    void yawZeroFacesPositiveZ() {
        assertArrayEquals(new double[] {0.0, 0.0, 1.0}, lookVector(0f, 0f), EPSILON,
                "yaw 0 must look towards positive Z");
    }

    @Test
    void yawNinetyFacesNegativeX() {
        assertArrayEquals(new double[] {-1.0, 0.0, 0.0}, lookVector(90f, 0f), EPSILON,
                "yaw 90 must look towards negative X");
    }

    @Test
    void positivePitchLooksDown() {
        assertArrayEquals(new double[] {0.0, -1.0, 0.0}, lookVector(0f, 90f), EPSILON,
                "pitch 90 must look straight down");
    }

    @Test
    void negativePitchLooksUp() {
        assertArrayEquals(new double[] {0.0, 1.0, 0.0}, lookVector(0f, -90f), EPSILON,
                "pitch -90 must look straight up");
    }

    @Test
    void diagonalLookStaysUnitLength() {
        double[] vector = lookVector(45f, 30f);
        double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);

        assertEquals(1.0, length, EPSILON, "a look vector must stay normalized");
    }

    private static double[] lookVector(float yaw, float pitch) {
        IEntity entity = new IEntity() {
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
        return new double[] {
            entity.getLookVectorX(),
            entity.getLookVectorY(),
            entity.getLookVectorZ(),
        };
    }
}
