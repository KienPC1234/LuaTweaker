package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** onTick handlers are stored per block; blocks without one stay null. */
class TickHandlerTest extends ContentFixture {

    @Test
    void machineHasNoTickHandler() throws IOException {
        runMachineScript();

        assertNull(block("test_machine").getTickHandler(),
                "no onTick registered for the plain machine");
    }

    @Test
    void pipeKeepsTickHandler() throws IOException {
        runMachineScript();

        assertNotNull(block("test_pipe").getTickHandler(),
                "pipe transport must be Lua-defined via onTick");
    }

    @Test
    void tickMachineKeepsTickHandler() throws IOException {
        runMachineScript();

        assertNotNull(block("tick_machine").getTickHandler(), "onTick handler must be stored");
    }
}
