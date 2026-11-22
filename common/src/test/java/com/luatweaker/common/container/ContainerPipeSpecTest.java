package com.luatweaker.common.container;

import com.luatweaker.content.BlockBuilderImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pipe block must expose exactly one slot and no drops. */
class ContainerPipeSpecTest {

    private static ContainerSpec pipeSpec() {
        BlockBuilderImpl pipe = new BlockBuilderImpl("mymod:flux_pipe");
        pipe.texture("mymod:block/flux_pipe")
                .connectionState(true)
                .energyStorage(2000, 256, 256)
                .onTick((data, ignored) -> {
                });
        return ContainerSpec.from(pipe);
    }

    @Test
    void pipeHasOneRow() {
        assertEquals(1, pipeSpec().rows(),
                "a pipe must expose exactly one slot row");
    }

    @Test
    void pipeHasOneColumn() {
        assertEquals(1, pipeSpec().cols(),
                "a pipe must expose exactly one slot column");
    }

    @Test
    void pipeDropsNothing() {
        assertEquals("none", pipeSpec().dropMode(),
                "a pipe must not drop container contents");
    }

    @Test
    void pipeKeepsEnergyCapacity() {
        assertEquals(2000, pipeSpec().energyCapacity(),
                "a pipe must keep its configured energy capacity");
    }

    @Test
    void pipeKeepsEnergyReceiveRate() {
        assertEquals(256, pipeSpec().energyMaxReceive(),
                "a pipe must keep its configured energy receive rate");
    }

    @Test
    void pipeKeepsConnections() {
        assertTrue(pipeSpec().connections(),
                "a pipe must keep its connection state");
    }

    @Test
    void pipeIsNotAContainerGrid() {
        assertFalse(pipeSpec().isContainer(),
                "a pipe is not a container grid");
    }

    @Test
    void pipeKeepsTickHandler() {
        assertNotNull(pipeSpec().tickHandler(),
                "a pipe must keep its tick handler");
    }
}
