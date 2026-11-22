package com.luatweaker.common.command.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The list command must serve files inside the lua folder and refuse escapes. */
class ListCommandContainmentTest {

    @Test
    void listsTheRequestedFolder(@TempDir File root) throws IOException {
        File server = CommandFixtures.prepareServerDir(root);

        ListCommand command = new ListCommand(root);
        MockCommandSender sender = new MockCommandSender();

        assertEquals(1, command.execute(sender, new String[] {CommandFixtures.SERVER_DIR}),
                "listing an existing folder must succeed: " + server);
    }

    @Test
    void rejectsParentTraversal(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        ListCommand command = new ListCommand(root);
        MockCommandSender attacker = new MockCommandSender();

        assertEquals(0, command.execute(attacker, new String[] {".."}),
                "escaping the lua folder must fail the command");
    }

    @Test
    void traversalFailsLoudly(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        ListCommand command = new ListCommand(root);
        MockCommandSender attacker = new MockCommandSender();
        command.execute(attacker, new String[] {".."});

        assertTrue(attacker.failedLoudly(),
                "traversal must fail loudly, got: " + attacker.messages);
    }

    @Test
    void rejectsAbsolutePaths(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        ListCommand command = new ListCommand(root);
        MockCommandSender absolute = new MockCommandSender();

        assertEquals(0, command.execute(absolute, new String[] {root.getAbsolutePath()}),
                "absolute paths must never resolve inside the lua folder");
    }

    @Test
    void absoluteRejectionFailsLoudly(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        ListCommand command = new ListCommand(root);
        MockCommandSender absolute = new MockCommandSender();
        command.execute(absolute, new String[] {root.getAbsolutePath()});

        assertTrue(absolute.failedLoudly(),
                "absolute-path rejection must fail loudly, got: " + absolute.messages);
    }
}
