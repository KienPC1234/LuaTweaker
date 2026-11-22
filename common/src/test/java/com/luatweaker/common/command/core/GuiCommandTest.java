package com.luatweaker.common.command.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiCommandTest {

    @Test
    void executeRejectsNonPlayers() {
        GuiCommand command = new GuiCommand();

        assertEquals(0, command.execute(new MockCommandSender(), new String[0]),
                "a non-player sender must be rejected with a zero result");
    }

    @Test
    void executeRejectionSendsAnError() {
        GuiCommand command = new GuiCommand();
        MockCommandSender sender = new MockCommandSender();
        command.execute(sender, new String[0]);

        assertTrue(sender.messages.stream().anyMatch(message -> message.startsWith("ERR:")),
                "a rejected sender must receive an error message");
    }

    @Test
    void executeRejectionMentionsInGamePlayers() {
        GuiCommand command = new GuiCommand();
        MockCommandSender sender = new MockCommandSender();
        command.execute(sender, new String[0]);

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("Only in-game players")),
                "the rejection must explain that only in-game players can open the editor");
    }
}
