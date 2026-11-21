package com.luatweaker.integration.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoModelSpecTest {

    private static final String MODEL_PATH = "luatweaker:geo/item/staff.geo.json";
    private static final String TEXTURE_PATH = "luatweaker:textures/item/staff.png";
    private static final String ANIMATION_PATH = "luatweaker:animations/item/staff.animation.json";
    private static final String IDLE_ANIMATION = "idle";

    @Test
    void rejectsBlankModelPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeoModelSpec("", "t.png", "a.animation.json", null),
                "a blank model path must be rejected");
    }

    @Test
    void rejectsBlankTexturePath() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeoModelSpec("m.geo.json", "  ", "a.animation.json", null),
                "a blank texture path must be rejected");
    }

    @Test
    void rejectsNullAnimationPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new GeoModelSpec("m.geo.json", "t.png", null, null),
                "a null animation path must be rejected");
    }

    @Test
    void keepsModelPathIntact() {
        GeoModelSpec spec = new GeoModelSpec(MODEL_PATH, TEXTURE_PATH, ANIMATION_PATH, IDLE_ANIMATION);
        assertEquals(MODEL_PATH, spec.modelPath(),
                "model path must round-trip through the spec unchanged");
    }

    @Test
    void keepsDefaultAnimationIntact() {
        GeoModelSpec spec = new GeoModelSpec(MODEL_PATH, TEXTURE_PATH, ANIMATION_PATH, IDLE_ANIMATION);
        assertEquals(IDLE_ANIMATION, spec.defaultAnimation(),
                "default animation must round-trip through the spec unchanged");
    }

    @Test
    void identicalSpecsAreEqual() {
        GeoModelSpec spec = new GeoModelSpec(MODEL_PATH, TEXTURE_PATH, ANIMATION_PATH, IDLE_ANIMATION);
        assertEquals(spec, new GeoModelSpec(MODEL_PATH, TEXTURE_PATH, ANIMATION_PATH, IDLE_ANIMATION),
                "two specs built from the same paths must compare equal");
    }
}
