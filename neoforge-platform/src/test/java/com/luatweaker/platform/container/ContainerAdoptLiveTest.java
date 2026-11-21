package com.luatweaker.platform.container;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The adopted spec takes runtime values from the refreshed builder. */
class ContainerAdoptLiveTest {

    @Test
    void adoptedTakesEnergyCapacity() {
        assertEquals(999, ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).energyCapacity(),
                "the adopted spec must take the refreshed energy capacity");
    }

    @Test
    void adoptedTakesEnergyExtractRate() {
        assertEquals(30, ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).energyMaxExtract(),
                "the adopted spec must take the refreshed energy extract rate");
    }

    @Test
    void adoptedTakesFluidCapacity() {
        assertEquals(4000, ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).fluidCapacity(),
                "the adopted spec must take the refreshed fluid capacity");
    }

    @Test
    void adoptedTakesDropMode() {
        assertEquals("spill", ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).dropMode(),
                "the adopted spec must take the refreshed drop mode");
    }

    @Test
    void adoptedTakesTexture() {
        assertEquals("new:texture", ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).texture(),
                "the adopted spec must take the refreshed texture");
    }

    @Test
    void adoptedTakesUseDistance() {
        assertEquals(9.0, ContainerAdoptFixture.adoptedRuntime(new AtomicInteger()).useDistance(), 1e-9,
                "the adopted spec must take the refreshed use distance");
    }

    @Test
    void adoptedTakesRenderHandler() {
        BiConsumer<Object, Object> renderHandler = ContainerAdoptFixture.newRenderHandler();
        ContainerSpec adopted = ContainerAdoptFixture.adoptedRuntimeWithRender(
                new AtomicInteger(), renderHandler);

        assertSame(renderHandler, adopted.renderHandler(),
                "the adopted spec must take the refreshed render handler");
    }

    @Test
    void adoptedRunsTheNewTickClosure() {
        AtomicInteger ticks = new AtomicInteger();
        ContainerAdoptFixture.adoptedRuntime(ticks).tickHandler().accept(null, null);

        assertEquals(100, ticks.get(),
                "the new Lua closure must be the one that runs");
    }
}
