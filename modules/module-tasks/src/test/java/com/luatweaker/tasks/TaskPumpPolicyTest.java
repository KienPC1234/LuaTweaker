package com.luatweaker.tasks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pump must run once per tick on every side, never twice. */
class TaskPumpPolicyTest {

    @Test
    void integratedOrDedicatedServerOwnsThePump() {
        assertFalse(TaskPumpPolicy.pumpFromClientTick(true),
                "the server tick already pumps this engine; a second pump would double the rate");
    }

    @Test
    void remoteClientPumpsFromItsOwnTick() {
        assertTrue(TaskPumpPolicy.pumpFromClientTick(false),
                "a remote client has no server tick, so its client tick must pump the queues");
    }
}
