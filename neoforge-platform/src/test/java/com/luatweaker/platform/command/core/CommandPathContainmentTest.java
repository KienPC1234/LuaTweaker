package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.entity.IPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CommandPathContainmentTest {

    static class MockSender implements ICommandSender {
        final List<String> messages = new ArrayList<>();

        @Override public void sendMessage(String message) { messages.add("MSG:" + message); }
        @Override public void sendSuccess(String message) { messages.add("OK:" + message); }
        @Override public void sendError(String message) { messages.add("ERR:" + message); }
        @Override public boolean hasPermission(int level) { return true; }
        @Override public boolean isPlayer() { return false; }
        @Override public IPlayer getPlayer() { return null; }
        @Override public String getName() { return "Console"; }
        @Override public String getHeldItemId() { return ""; }
    }

    @Test
    void listCommand_RejectsTraversalOutsideRoot(@TempDir File root) throws Exception {
        File server = new File(root, "server");
        assertTrue(server.mkdir());
        Files.writeString(new File(server, "a.lua").toPath(), "return 1");
        File outside = new File(root, "outside.lua");
        Files.writeString(outside.toPath(), "return 1");

        ListCommand command = new ListCommand(root);
        MockSender sender = new MockSender();

        assertEquals(1, command.execute(sender, new String[] { "server" }));

        MockSender attacker = new MockSender();
        assertEquals(0, command.execute(attacker, new String[] { ".." }));
        assertTrue(attacker.messages.stream().anyMatch(m -> m.startsWith("ERR:")),
                "traversal must fail loudly, got: " + attacker.messages);

        MockSender absolute = new MockSender();
        assertEquals(0, command.execute(absolute, new String[] { root.getAbsolutePath() }));
        assertTrue(absolute.messages.stream().anyMatch(m -> m.startsWith("ERR:")));
    }

    @Test
    void syntaxCommand_RejectsTraversalOutsideRoot(@TempDir File root) throws Exception {
        File server = new File(root, "server");
        assertTrue(server.mkdir());
        Files.writeString(new File(server, "ok.lua").toPath(), "local x = 1\nreturn x\n");

        SyntaxCommand command = new SyntaxCommand(root);

        MockSender ok = new MockSender();
        assertEquals(1, command.execute(ok, new String[] { "server" }));

        MockSender attacker = new MockSender();
        assertEquals(0, command.execute(attacker, new String[] { "../outside.lua" }));
        assertTrue(attacker.messages.stream().anyMatch(m -> m.contains("outside the lua/ folder")),
                "traversal must be refused loudly, got: " + attacker.messages);
    }

    @Test
    void resolveInsideRoot_AllowsNestedButNotEscapes(@TempDir File root) {
        File nested = new File(root, "server");
        assertTrue(nested.mkdir());

        assertNotNull(ListCommand.resolveInsideRoot(root, "server"));
        assertNotNull(ListCommand.resolveInsideRoot(root, "server/a.lua"));
        assertNull(ListCommand.resolveInsideRoot(root, ".."));
        assertNull(ListCommand.resolveInsideRoot(root, "../secret"));
        assertNull(ListCommand.resolveInsideRoot(root, root.getAbsolutePath()));
    }
}
