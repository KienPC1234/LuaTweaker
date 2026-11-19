package com.luatweaker.platform.container;

import com.luatweaker.api.content.BoxSpec;
import com.luatweaker.content.BlockBuilderImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reload rebinding: the registry is frozen, so a live block keeps its geometry and
 * block-state layout but must adopt the freshly loaded Lua callbacks. These tests pin
 * both halves of that contract.
 */
public class ContainerSpecTest {

    @Test
    public void fromGivesPipesOneSlotAndNoDrops() {
        BlockBuilderImpl pipe = new BlockBuilderImpl("mymod:flux_pipe");
        pipe.texture("mymod:block/flux_pipe")
                .connectionState(true)
                .energyStorage(2000, 256, 256)
                .onTick((data, ignored) -> {
                });

        ContainerSpec spec = ContainerSpec.from(pipe);

        assertEquals(1, spec.rows());
        assertEquals(1, spec.cols());
        assertEquals("none", spec.dropMode());
        assertEquals(2000, spec.energyCapacity());
        assertEquals(256, spec.energyMaxReceive());
        assertTrue(spec.connections());
        assertFalse(spec.isContainer(), "a pipe is not a container grid");
        assertNotNull(spec.tickHandler());
    }

    @Test
    public void fromKeepsContainerGeometryAndStorage() {
        BlockBuilderImpl crate = new BlockBuilderImpl("mymod:crate");
        crate.container(3, 9, "spill")
                .energyStorage(1000, 10, 20)
                .fluidStorage(4000);

        ContainerSpec spec = ContainerSpec.from(crate);

        assertEquals(3, spec.rows());
        assertEquals(9, spec.cols());
        assertEquals("spill", spec.dropMode());
        assertTrue(spec.isContainer());
        assertEquals(1000, spec.energyCapacity());
        assertEquals(10, spec.energyMaxReceive());
        assertEquals(20, spec.energyMaxExtract());
        assertEquals(4000, spec.fluidCapacity());
    }

    @Test
    public void adoptRuntimeKeepsFrozenFieldsAndSwapsLiveBehaviour() {
        AtomicInteger ticks = new AtomicInteger();
        BiConsumer<Object, Object> oldTick = (data, ignored) -> ticks.addAndGet(1);
        BiConsumer<Object, Object> newTick = (data, ignored) -> ticks.addAndGet(100);
        BiConsumer<Object, Object> newRender = (renderer, data) -> {
        };
        List<BoxSpec> boxes = List.of(new BoxSpec(0, 0, 0, 16, 16, 16));

        ContainerSpec previous = new ContainerSpec(2, 3, "packed", "old:texture", 8.0,
                Map.of(), Set.of(), null, 100, 10, 10, 0, List.of(), null,
                false, oldTick, true, "HORIZONTAL", boxes, null);
        ContainerSpec refreshed = new ContainerSpec(2, 3, "spill", "new:texture", 9.0,
                Map.of(), Set.of(), null, 999, 20, 30, 4000, List.of(), null,
                false, newTick, true, "HORIZONTAL", boxes, newRender);

        ContainerSpec adopted = ContainerSpec.adoptRuntime(previous, refreshed);

        assertEquals(2, adopted.rows(), "geometry is frozen while the game runs");
        assertEquals(3, adopted.cols());
        assertEquals("HORIZONTAL", adopted.facing());
        assertEquals(boxes, adopted.boxes());
        // Runtime values come from the refreshed builder.
        assertEquals(999, adopted.energyCapacity());
        assertEquals(30, adopted.energyMaxExtract());
        assertEquals(4000, adopted.fluidCapacity());
        assertEquals("spill", adopted.dropMode());
        assertEquals("new:texture", adopted.texture());
        assertEquals(9.0, adopted.useDistance(), 1e-9);
        assertSame(newRender, adopted.renderHandler());

        adopted.tickHandler().accept(null, null);
        assertEquals(100, ticks.get(), "the new Lua closure must be the one that runs");

        assertFalse(previous.hasFrozenDifference(refreshed),
                "only geometry/state/shape differences need a restart");
    }

    @Test
    public void frozenDifferenceIsReported() {
        ContainerSpec previous = new ContainerSpec(2, 3, "packed", null, 8.0,
                Map.of(), Set.of(), null, 0, 0, 0, 0, List.of(), null,
                false, null);
        ContainerSpec wider = new ContainerSpec(6, 9, "packed", null, 8.0,
                Map.of(), Set.of(), null, 0, 0, 0, 0, List.of(), null,
                false, null);

        ContainerSpec adopted = ContainerSpec.adoptRuntime(previous, wider);

        assertTrue(previous.hasFrozenDifference(wider));
        assertEquals(2, adopted.rows(), "the frozen value wins");
        assertEquals(3, adopted.cols());
    }
}
