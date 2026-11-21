package com.luatweaker.platform.container;

import com.luatweaker.api.content.BoxSpec;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/** Shared previous/refreshed specs for the runtime-adoption tests. */
final class ContainerAdoptFixture {

    private static final List<BoxSpec> BOXES = List.of(new BoxSpec(0, 0, 0, 16, 16, 16));

    private ContainerAdoptFixture() {
    }

    static ContainerSpec previousSpec(BiConsumer<Object, Object> tickHandler) {
        return new ContainerSpec(2, 3, "packed", "old:texture", 8.0,
                Map.of(), Set.of(), null, 100, 10, 10, 0, List.of(), null,
                false, tickHandler, true, "HORIZONTAL", BOXES, null, false);
    }

    static ContainerSpec refreshedSpec(BiConsumer<Object, Object> tickHandler,
            BiConsumer<Object, Object> renderHandler) {
        return new ContainerSpec(2, 3, "spill", "new:texture", 9.0,
                Map.of(), Set.of(), null, 999, 20, 30, 4000, List.of(), null,
                false, tickHandler, true, "HORIZONTAL", BOXES, renderHandler, false);
    }

    static ContainerSpec adoptedRuntime(AtomicInteger ticks) {
        BiConsumer<Object, Object> oldTick = (data, ignored) -> ticks.addAndGet(1);
        BiConsumer<Object, Object> newTick = (data, ignored) -> ticks.addAndGet(100);
        BiConsumer<Object, Object> newRender = (renderer, data) -> {
        };
        return ContainerSpec.adoptRuntime(previousSpec(oldTick),
                refreshedSpec(newTick, newRender));
    }

    static ContainerSpec previousGridSpec() {
        return new ContainerSpec(2, 3, "packed", null, 8.0,
                Map.of(), Set.of(), null, 0, 0, 0, 0, List.of(), null,
                false, null, false);
    }

    static ContainerSpec widerGridSpec() {
        return new ContainerSpec(6, 9, "packed", null, 8.0,
                Map.of(), Set.of(), null, 0, 0, 0, 0, List.of(), null,
                false, null, false);
    }

    static ContainerSpec adoptedRuntimeWithRender(AtomicInteger ticks,
            BiConsumer<Object, Object> renderHandler) {
        BiConsumer<Object, Object> oldTick = (data, ignored) -> ticks.addAndGet(1);
        BiConsumer<Object, Object> newTick = (data, ignored) -> ticks.addAndGet(100);
        return ContainerSpec.adoptRuntime(previousSpec(oldTick),
                refreshedSpec(newTick, renderHandler));
    }

    static BiConsumer<Object, Object> newRenderHandler() {
        return (renderer, data) -> {
        };
    }
}
