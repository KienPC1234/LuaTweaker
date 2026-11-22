package com.luatweaker.common.command.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The syntax command must check files inside the lua folder and refuse escapes. */
class SyntaxCommandContainmentTest {

    @Test
    void checksTheRequestedFolder(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        SyntaxCommand command = new SyntaxCommand(root);
        MockCommandSender ok = new MockCommandSender();

        assertEquals(1, command.execute(ok, new String[] {CommandFixtures.SERVER_DIR}),
                "syntax-checking a valid folder must succeed");
    }

    @Test
    void rejectsParentTraversal(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        SyntaxCommand command = new SyntaxCommand(root);
        MockCommandSender attacker = new MockCommandSender();

        assertEquals(0, command.execute(attacker, new String[] {"../outside.lua"}),
                "escaping the lua folder must fail the syntax check");
    }

    @Test
    void traversalFailsLoudly(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        SyntaxCommand command = new SyntaxCommand(root);
        MockCommandSender attacker = new MockCommandSender();
        command.execute(attacker, new String[] {"../outside.lua"});

        assertTrue(attacker.messages.stream().anyMatch(message -> message.contains("outside the lua/ folder")),
                "traversal must be refused loudly, got: " + attacker.messages);
    }
}
