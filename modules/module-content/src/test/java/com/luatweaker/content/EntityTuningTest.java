package com.luatweaker.content;

import com.luatweaker.api.content.IEntityBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Render scale, tick rate and experience flags validate their ranges. */
class EntityTuningTest extends ContentFixture {

    private static final String TUNING = "test:tuning";

    @Test
    void defaultRenderScaleIsOne() {
        contentService.createEntity(TUNING, builder -> { });
        IEntityBuilder builder = entity(TUNING);

        assertEquals(1.0, builder.getRenderScale(), "the default render scale must be one");
    }

    @Test
    void defaultTickRateIsFive() {
        contentService.createEntity(TUNING, builder -> { });
        IEntityBuilder builder = entity(TUNING);

        assertEquals(5, builder.getTickRate(), "the default tick rate must be five");
    }

    @Test
    void rejectsZeroRenderScale() {
        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                "startup:createEntity('test:scale_bad', function(e) e:renderScale(0) end)\n", "ScaleBadTest"),
                "a zero render scale must be rejected");
    }

    @Test
    void rejectsZeroTickRate() {
        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                "startup:createEntity('test:tick_bad', function(e) e:tickRate(0) end)\n", "TickBadTest"),
                "a zero tick rate must be rejected");
    }

    @Test
    void defaultExperienceNotExplicit() {
        contentService.createEntity("test:xp_default", builder -> { });
        IEntityBuilder builder = entity("test:xp_default");

        assertFalse(builder.hasExplicitExperience(), "default experience must not override vanilla XP");
    }

    @Test
    void explicitExperienceFlagged() {
        engine.executeStringOrThrow(
                "startup:createEntity('test:xp_set', function(e) e:experience(200) end)\n", "XpSetTest");

        assertTrue(entity("test:xp_set").hasExplicitExperience(),
                "set experience must be flagged explicit");
    }

    @Test
    void explicitExperienceValue() {
        engine.executeStringOrThrow(
                "startup:createEntity('test:xp_set', function(e) e:experience(200) end)\n", "XpSetTest");

        assertEquals(200, entity("test:xp_set").getExperience(),
                "the explicit experience value must be kept");
    }
}
