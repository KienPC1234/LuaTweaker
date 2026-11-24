package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ILuaTweakerCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies help covers every newly registered core command. */
class HelpCommandCoreCommandsTest {

    private static final List<ILuaTweakerCommand> NEW_COMMANDS = List.of(
            new DimensionCommand(), new TeleportCommand(), new SpawnCommand());

    @Test
    void helpListsAllCoreCommands() {
        MockCommandSender sender = new MockCommandSender();
        new HelpCommand(NEW_COMMANDS).execute(sender, new String[0]);

        assertTrue(sender.messages.stream().anyMatch(line -> line.contains("dimension")),
                "help must list the dimension command, got: " + sender.messages);
    }

    @Test
    void helpListsTeleportShortcut() {
        MockCommandSender sender = new MockCommandSender();
        new HelpCommand(NEW_COMMANDS).execute(sender, new String[0]);

        assertTrue(sender.messages.stream().anyMatch(line -> line.contains("Teleport to a Lua-registered dimension")),
                "help must list the tp shortcut description, got: " + sender.messages);
    }

    @Test
    void helpListsSpawnCommand() {
        MockCommandSender sender = new MockCommandSender();
        new HelpCommand(NEW_COMMANDS).execute(sender, new String[0]);

        assertTrue(sender.messages.stream().anyMatch(line -> line.contains("spawn rules")),
                "help must list the spawn command description, got: " + sender.messages);
    }
}
