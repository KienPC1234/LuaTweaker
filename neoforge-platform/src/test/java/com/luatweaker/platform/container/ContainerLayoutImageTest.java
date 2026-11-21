package com.luatweaker.platform.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The container image height must follow the vanilla chest formula. */
class ContainerLayoutImageTest {

    @Test
    void threeRowsGive168Pixels() {
        assertEquals(168, ContainerLayout.imageHeight(3),
                "a 3-row container must be 168px tall like a vanilla chest");
    }

    @Test
    void fourRowsGive186Pixels() {
        assertEquals(186, ContainerLayout.imageHeight(4),
                "a 4-row container must grow by one slot row");
    }

    @Test
    void sixRowsGive222Pixels() {
        assertEquals(222, ContainerLayout.imageHeight(6),
                "a 6-row container must fit a double chest plus the player inventory");
    }
}
