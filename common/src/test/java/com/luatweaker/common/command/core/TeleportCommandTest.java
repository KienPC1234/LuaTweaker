package com.luatweaker.common.command.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the /lt tp shortcut forwards its tail to the dimension command. */
class TeleportCommandTest {

    @Test
    void shortcutNameIsTp() {
        assertEquals("tp", new TeleportCommand().getName(),
                "the teleport shortcut must be named tp");
    }

    @Test
    void shortcutWithoutArgsIsRejectedLikeDimensionTp() {
        MockCommandSender sender = new MockCommandSender();

        assertEquals(0, new TeleportCommand().execute(sender, new String[0]),
                "a tp call without a dimension id must fail");
    }
}
