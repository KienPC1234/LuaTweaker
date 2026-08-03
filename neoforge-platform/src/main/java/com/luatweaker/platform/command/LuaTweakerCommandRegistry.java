package com.luatweaker.platform.command;

import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.command.CommandDefinition;
import com.luatweaker.command.CommandServiceImpl;
import com.luatweaker.command.LuaCommandWrapper;
import com.luatweaker.platform.command.core.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Central Brigadier command registry for LuaTweaker.
 *
 * <p>Built-in commands live under {@code /luatweaker} (alias: {@code /lt}).
 * Commands registered by Lua mods (via {@code Commands:Register}) become
 * TOP-LEVEL Brigadier commands with no prefix, e.g. {@code /hello} or the
 * nested {@code /shop buy}. They are checked against already-occupied names
 * (vanilla commands, other mods) at build time and rejected loudly on
 * collision.</p>
 */
public class LuaTweakerCommandRegistry {

    /** All built-in sub-commands. Read-only after {@link #build(RegisterCommandsEvent)}. */
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
        register(new DoctorCommand());
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
     * tree and all Lua-registered top-level commands into Brigadier. Call from a
     * {@link RegisterCommandsEvent} handler.
     *
     * <p>Lua command wrappers resolve their definitions live at execution time,
     * so a later {@code /lt reload} that replaces handlers is honored without
     * re-registering Brigadier nodes.</p>
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

        registerLuaCommands(dispatcher);
    }

    /** Builds a Brigadier sub-command node for the given {@link ILuaTweakerCommand}. */
    private LiteralArgumentBuilder<CommandSourceStack> buildSubCommand(ILuaTweakerCommand cmd) {
        return Commands.literal(cmd.getName())
                .requires(src -> src.hasPermission(cmd.getPermissionLevel()))
                // Variant with optional trailing arguments
                .then(buildArgsNode(cmd))
                // Variant with no arguments
                .executes(ctx -> cmd.executeRaw(new NeoForgeCommandSender(ctx.getSource()), ""));
    }

    /**
     * Builds the greedy-string argument node carrying the raw tail. Lua commands
     * get tab-completion from their optional {@code Suggestions} provider.
     */
    private ArgumentBuilder<CommandSourceStack, ?> buildArgsNode(ILuaTweakerCommand cmd) {
        RequiredArgumentBuilder<CommandSourceStack, String> argsNode =
                Commands.argument("args", StringArgumentType.greedyString());
        if (cmd instanceof LuaCommandWrapper luaCmd) {
            argsNode = argsNode.suggests((ctx, builder) -> {
                for (String suggestion : luaCmd.suggest(
                        new NeoForgeCommandSender(ctx.getSource()),
                        builder.getRemaining()
                )) {
                    builder.suggest(suggestion);
                }
                return builder.buildFuture();
            });
        }
        return argsNode.executes(ctx -> {
            String raw = StringArgumentType.getString(ctx, "args");
            return cmd.executeRaw(new NeoForgeCommandSender(ctx.getSource()), raw);
        });
    }

    // -------------------------------------------------------------------------
    // Lua-registered top-level commands
    // -------------------------------------------------------------------------

    /**
     * Registers every Lua command (primary paths + aliases) at the dispatcher
     * root. Names already occupied by vanilla or other mods are skipped with a
     * loud error instead of silently overwriting them.
     */
    private void registerLuaCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("CommandServiceImpl");
        if (!(service instanceof CommandServiceImpl commandService)) {
            return;
        }

        Set<String> occupied = new HashSet<>();
        dispatcher.getRoot().getChildren().forEach(node -> occupied.add(node.getName()));

        for (CommandDefinition def : commandService.getSnapshot()) {
            registerLuaPath(dispatcher, occupied, def, def.name());
            for (String aliasPath : def.aliases()) {
                registerLuaPath(dispatcher, occupied, def, aliasPath);
            }
        }
    }

    /**
     * Registers one command path (primary or alias) as nested literals. Every
     * prefix level is checked against occupied paths so two commands sharing a
     * root (e.g. {@code shop/buy} and {@code shop/list}) coexist, while a path
     * stepping into an existing command is rejected loudly.
     */
    private void registerLuaPath(CommandDispatcher<CommandSourceStack> dispatcher,
                                 Set<String> occupied,
                                 CommandDefinition def,
                                 String path) {
        String[] segments = path.split("/");
        StringBuilder prefix = new StringBuilder();
        for (String segment : segments) {
            if (prefix.length() > 0) prefix.append('/');
            prefix.append(segment);
            if (occupied.contains(prefix.toString())) {
                LuaTweakerLog.get().error(LogStage.COMMAND,
                        "Lua command '/" + path + "' from mod '" + def.modId()
                                + "' NOT registered: '/" + prefix
                                + "' is already taken by another command. Rename it to avoid conflicts.");
                return;
            }
        }

        LuaCommandWrapper wrapper = new LuaCommandWrapper(def.name());
        dispatcher.register(buildLuaPathNode(wrapper, segments, 0));
        occupied.add(path);
    }

    /** Builds nested literals for a path; the deepest literal carries the args node. */
    private LiteralArgumentBuilder<CommandSourceStack> buildLuaPathNode(ILuaTweakerCommand cmd,
                                                                        String[] segments,
                                                                        int index) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(segments[index]);
        if (index == segments.length - 1) {
            return literal
                    .then(buildArgsNode(cmd))
                    .executes(ctx -> cmd.executeRaw(new NeoForgeCommandSender(ctx.getSource()), ""));
        }
        return literal.then(buildLuaPathNode(cmd, segments, index + 1));
    }
}
