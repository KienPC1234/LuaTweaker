package com.luatweaker.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies every core command literal has a no-argument execution variant. */
class CoreCommandTreeTest {

    private static final String[] CORE_NAMES = {
            "reload", "hand", "syntax", "list", "debug", "doctor", "help", "gui",
            "dimension", "tp", "spawn"
    };

    private static CommandNode<CommandSourceStack> root() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        new LuaTweakerCommandRegistry(new File("build/test-lua")).build(dispatcher);
        return Objects.requireNonNull(dispatcher.getRoot().getChild("lt"), "the /lt alias must be registered");
    }

    @Test
    void doctorHasNoArgExecutor() {
        CommandNode<CommandSourceStack> doctor = Objects.requireNonNull(root().getChild("doctor"),
                "doctor must be registered under /lt");

        assertTrue(doctor.getCommand() != null,
                "doctor must have a no-argument executor so /lt doctor parses");
    }

    @Test
    void everyCoreCommandHasNoArgExecutor() {
        CommandNode<CommandSourceStack> lt = root();

        assertTrue(java.util.Arrays.stream(CORE_NAMES)
                        .allMatch(name -> lt.getChild(name) != null && lt.getChild(name).getCommand() != null),
                "all core commands must expose a no-argument execution variant, got: "
                        + lt.getChildren().stream().map(CommandNode::getName).toList());
    }
}
