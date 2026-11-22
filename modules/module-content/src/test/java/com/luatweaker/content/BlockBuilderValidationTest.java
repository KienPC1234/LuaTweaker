package com.luatweaker.content;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Block builder validation: loud failures instead of silent misconfiguration. */
class BlockBuilderValidationTest {

    @Test
    void lowLightClampsToZero() {
        BlockBuilderImpl builder = new BlockBuilderImpl("mymod:dim");
        builder.lightLevel(-5);

        assertEquals(0, builder.getLightLevel(), "negative light must clamp to 0");
    }

    @Test
    void highLightClampsToFifteen() {
        BlockBuilderImpl builder = new BlockBuilderImpl("mymod:bright");
        builder.lightLevel(99);

        assertEquals(15, builder.getLightLevel(), "light above 15 must clamp to 15");
    }

    @Test
    void invertedBoxThrows() {
        BlockBuilderImpl builder = new BlockBuilderImpl("mymod:box");

        assertThrows(IllegalArgumentException.class, () -> builder.box(5, 5, 5, 1, 1, 1),
                "min > max boxes must be rejected loudly");
    }

    @Test
    void nanBoxThrows() {
        BlockBuilderImpl builder = new BlockBuilderImpl("mymod:box");

        assertThrows(IllegalArgumentException.class,
                () -> builder.box(0, 0, 0, Double.NaN, 16, 16),
                "NaN box coordinates must be rejected loudly");
    }

    @Test
    void reservedBooleanStateThrows() {
        BlockBuilderImpl builder = new BlockBuilderImpl("mymod:machine");

        assertThrows(IllegalArgumentException.class,
                () -> builder.booleanState("facing", "a", "b"),
                "reserved property names must be rejected loudly");
    }

    @Test
    void customBooleanStateSticks() {
        BlockBuilderImpl builder = new BlockBuilderImpl("mymod:machine");
        builder.booleanState("running", "off", "on");

        assertEquals("running", builder.getBooleanState().property(),
                "custom boolean state properties must stick");
    }
}
