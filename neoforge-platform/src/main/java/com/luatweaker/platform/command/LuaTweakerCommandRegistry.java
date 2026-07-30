package com.luatweaker.platform.command;

import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.platform.command.core.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central Brigadier command registry for LuaTweaker.
 *
 * <h3>How to add a new command</h3>
 * <pre>
 *   // 1. Implement ILuaTweakerCommand in neoforge-platform/command/core/
 *   // 2. Register it here:
 *   registry.register(new MyNewCommand(...));
 *   // That's it — Brigadier node + help entry are automatic.
 * </pre>
 *
 * Root literal: {@code /luatweaker} (alias: {@code /lt})
 */
public class LuaTweakerCommandRegistry {

    /** All registered sub-commands. Read-only after {@link #build(RegisterCommandsEvent)}. */
    private final List<ILuaTweakerCommand> commands = new ArrayList<>();

    private final File luaRoot;

    public LuaTweakerCommandRegistry(File luaRoot) {
        this.luaRoot = luaRoot;
        registerCoreCommands();
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    private void registerCoreCommands() {
        // HelpCommand is registered last so it can reference the full list
        register(new ReloadCommand());
        register(new HandCommand());
        register(new SyntaxCommand(luaRoot));
        register(new ListCommand(luaRoot));
        register(new DebugCommand());
        // Help must come last so the list snapshot it captures is complete
        register(new HelpCommand(Collections.unmodifiableList(commands)));
    }

    /**
     * Register an additional command.
     * Call this before {@link #build(RegisterCommandsEvent)} is invoked.
     */
    public void register(ILuaTweakerCommand command) {
        commands.add(command);
    }

    // -------------------------------------------------------------------------
    // Brigadier wiring
    // -------------------------------------------------------------------------

    /**
     * Builds and registers the full {@code /luatweaker} (+ {@code /lt}) command
     * tree into Brigadier. Call from a {@link RegisterCommandsEvent} handler.
     */
    public void build(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal("luatweaker");

        for (ILuaTweakerCommand cmd : commands) {
            root.then(buildSubCommand(cmd));
        }

        dispatcher.register(root);

        // Alias: /lt
        LiteralArgumentBuilder<CommandSourceStack> alias =
                Commands.literal("lt");
        for (ILuaTweakerCommand cmd : commands) {
            alias.then(buildSubCommand(cmd));
        }
        dispatcher.register(alias);
    }

    /** Builds a Brigadier sub-command node for the given {@link ILuaTweakerCommand}. */
    private LiteralArgumentBuilder<CommandSourceStack> buildSubCommand(ILuaTweakerCommand cmd) {
        return Commands.literal(cmd.getName())
                .requires(src -> src.hasPermission(cmd.getPermissionLevel()))
                // Variant with optional trailing arguments
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "args");
                            String[] args = raw.isBlank() ? new String[0] : raw.split("\\s+");
                            return cmd.execute(new NeoForgeCommandSender(ctx.getSource()), args);
                        }))
                // Variant with no arguments
                .executes(ctx -> cmd.execute(new NeoForgeCommandSender(ctx.getSource()), new String[0]));
    }
}
