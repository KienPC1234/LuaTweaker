package com.luatweaker.common.container;

import com.luatweaker.api.content.BoxSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reload rebinding: the registry is frozen, so a live block keeps its geometry
 * and block-state layout while adopting the freshly loaded values that are
 * allowed to change.
 */
class ContainerAdoptFrozenTest {

    @Test
    void adoptedKeepsRowCount() {
        assertEquals(2, ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).rows(),
                "geometry is frozen while the game runs");
    }

    @Test
    void adoptedKeepsColumnCount() {
        assertEquals(3, ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).cols(),
                "geometry is frozen while the game runs");
    }

    @Test
    void adoptedKeepsFacing() {
        assertEquals("HORIZONTAL", ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).facing(),
                "the facing axis is frozen while the game runs");
    }

    @Test
    void adoptedKeepsBoxes() {
        assertEquals(List.of(new BoxSpec(0, 0, 0, 16, 16, 16)),
                ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).boxes(),
                "the collision boxes are frozen while the game runs");
    }

    @Test
    void identicalSpecsHaveNoFrozenDifference() {
        AtomicInteger ticks = new AtomicInteger();

        assertFalse(ContainerAdoptFixture.previousSpec((data, ignored) -> ticks.addAndGet(1))
                .hasFrozenDifference(ContainerAdoptFixture.refreshedSpec(
                        (data, ignored) -> ticks.addAndGet(100),
                        ContainerAdoptFixture.newRenderHandler())),
                "only geometry/state/shape differences need a restart");
    }

    @Test
    void widerGridHasFrozenDifference() {
        assertTrue(ContainerAdoptFixture.previousGridSpec()
                        .hasFrozenDifference(ContainerAdoptFixture.widerGridSpec()),
                "a geometry change must be reported as a frozen difference");
    }

    @Test
    void adoptedGridKeepsFrozenRows() {
        assertEquals(2, ContainerSpec.adoptRuntime(
                        ContainerAdoptFixture.previousGridSpec(),
                        ContainerAdoptFixture.widerGridSpec()).rows(),
                "the frozen value wins");
    }

    @Test
    void adoptedGridKeepsFrozenColumns() {
        assertEquals(3, ContainerSpec.adoptRuntime(
                        ContainerAdoptFixture.previousGridSpec(),
                        ContainerAdoptFixture.widerGridSpec()).cols(),
                "the frozen value wins");
    }
}
