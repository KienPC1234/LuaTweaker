package com.luatweaker.common.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientItemAnimationHandlerTest {

    private static final int TEST_ENTITY_ID = 42;
    private static final int SECOND_ENTITY_ID = 84;
    private static final String ANIM_RECOIL = "recoil";
    private static final String ANIM_SLASH = "slash";
    private static final String ANIM_THRUST = "thrust";
    private static final String ANIM_INSPECT = "inspect";
    private static final String ANIM_SPIN = "spin";
    private static final String ANIM_CAST = "cast";

    @BeforeEach
    @AfterEach
    void cleanState() {
        ClientItemAnimationHandler.clear();
    }

    @Test
    void triggerStoresNormalizedActiveAnimation() {
        ClientItemAnimationHandler.trigger(TEST_ENTITY_ID, "RECOIL", 10, 1.5f);
        ClientItemAnimationHandler.ActiveAnim anim = ClientItemAnimationHandler.getAnimation(TEST_ENTITY_ID);

        assertAll("Triggered animation fields must match input specifications",
                () -> assertNotNull(anim, "Triggering animation must store it in active animations"),
                () -> assertEquals(ANIM_RECOIL, anim.name(), "Animation name must be normalized to lowercase"),
                () -> assertEquals(10, anim.totalTicks(), "Total ticks must match specified duration"),
                () -> assertEquals(10, anim.ticksRemaining(), "Initial remaining ticks must match total ticks"),
                () -> assertEquals(1.5f, anim.speed(), 0.001f, "Speed must match specified multiplier")
        );
    }

    @Test
    void triggerWithNonPositiveValuesAppliesDefaults() {
        ClientItemAnimationHandler.trigger(TEST_ENTITY_ID, ANIM_SLASH, 0, -1.0f);
        ClientItemAnimationHandler.ActiveAnim anim = ClientItemAnimationHandler.getAnimation(TEST_ENTITY_ID);

        assertAll("Non-positive parameters must fallback to default values",
                () -> assertNotNull(anim, "Animation must be registered"),
                () -> assertEquals(20, anim.totalTicks(), "Non-positive duration must fall back to 20 ticks"),
                () -> assertEquals(1.0f, anim.speed(), 0.001f, "Non-positive speed must fall back to 1.0f")
        );
    }

    @Test
    void onClientTickDecrementsRemainingTicks() {
        ClientItemAnimationHandler.trigger(TEST_ENTITY_ID, ANIM_THRUST, 5, 1.0f);

        ClientItemAnimationHandler.onClientTick();
        ClientItemAnimationHandler.ActiveAnim anim = ClientItemAnimationHandler.getAnimation(TEST_ENTITY_ID);
        assertEquals(4, anim != null ? anim.ticksRemaining() : 0,
                "Remaining ticks must decrement by one per tick");
    }

    @Test
    void onClientTickRemovesExpiredAnimation() {
        ClientItemAnimationHandler.trigger(TEST_ENTITY_ID, ANIM_INSPECT, 2, 1.0f);

        ClientItemAnimationHandler.onClientTick();
        ClientItemAnimationHandler.onClientTick();
        assertNull(ClientItemAnimationHandler.getAnimation(TEST_ENTITY_ID),
                "Animation must be removed once remaining ticks reach final tick threshold");
    }

    @Test
    void multipleEntitiesTickIndependently() {
        ClientItemAnimationHandler.trigger(TEST_ENTITY_ID, ANIM_SPIN, 3, 1.0f);
        ClientItemAnimationHandler.trigger(SECOND_ENTITY_ID, ANIM_CAST, 2, 2.0f);

        ClientItemAnimationHandler.onClientTick();
        ClientItemAnimationHandler.onClientTick();
        assertNull(ClientItemAnimationHandler.getAnimation(SECOND_ENTITY_ID),
                "Entity with shorter duration must expire while longer duration entity remains");
    }
}
