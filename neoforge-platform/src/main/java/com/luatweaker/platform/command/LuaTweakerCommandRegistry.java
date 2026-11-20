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

    /**
     * Lua paths already live in a dispatcher from an earlier registration cycle.
     * RegisterCommandsEvent fires once at startup; reloads only ADD brand-new
     * paths (changed handlers need no work — wrappers resolve definitions live).
     * Removals are not tracked here: Brigadier cannot unregister nodes, so a
     * deleted command stays until the next datapack reload.
     */
    private final Set<String> registeredLuaPaths = new HashSet<>();

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
     *
     * <p>Idempotent across reloads: paths registered by an earlier cycle are
     * skipped quietly (their wrappers resolve definitions live, so changed
     * handlers already work). Only brand-new paths consume dispatcher nodes.
     * Same-cycle duplicates and vanilla collisions stay loud.
     */
    private void registerLuaCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("CommandServiceImpl");
        if (!(service instanceof CommandServiceImpl commandService)) {
            return;
        }

        Set<String> occupied = new HashSet<>();
        dispatcher.getRoot().getChildren().forEach(node -> occupied.add(node.getName()));
        Set<String> justAdded = new HashSet<>();

        for (CommandDefinition def : commandService.getSnapshot()) {
            for (String path : allPathsOf(def)) {
                registerLuaPath(dispatcher, occupied, justAdded, def, path);
            }
        }
    }

    /**
     * Re-registers only brand-new Lua command paths into a live dispatcher.
     * Called after {@code /lt reload}: {@link RegisterCommandsEvent} fires once
     * at startup, so without this new paths 404 until a datapack reload or
     * restart (changed handlers need nothing — wrappers resolve live).
     *
     * <p>Removals are NOT applied here: Brigadier has no node removal, so a
     * deleted command keeps working until the next datapack reload. This is
     * logged, not silent.
     */
    public void registerNewLuaCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerLuaCommands(dispatcher);
    }

    /**
     * All registrable paths for one definition, primary path first. Pure string
     * logic (no Brigadier/Minecraft touch) so reload diffing stays unit-testable.
     */
    static List<String> allPathsOf(CommandDefinition def) {
        List<String> paths = new ArrayList<>(def.aliases().size() + 1);
        paths.add(def.name());
        paths.addAll(def.aliases());
        return paths;
    }

    /** Outcome of the reload-diff for one candidate path. */
    enum PathDecision {
        /** Fresh path: build Brigadier nodes now. */
        REGISTER,
        /** Live from an earlier cycle: skip quietly, wrapper resolves the new handler. */
        SKIP_ALREADY_LIVE,
        /** Same snapshot registers it twice: loud duplicate, keep the first. */
        REJECT_DUPLICATE_THIS_CYCLE,
        /** A prefix is taken by vanilla/another mod: loud, skip. */
        REJECT_OCCUPIED
    }

    /**
     * Pure reload-diff for one candidate path. Priority: same-cycle duplicates
     * stay loud (a mod typo must not vanish), previous-cycle paths skip quietly,
     * own live roots merge quietly (a new sibling under our own earlier-cycle
     * root extends it instead of colliding), everything else occupied stays loud.
     */
    static PathDecision decidePath(String path, Set<String> occupied, Set<String> justAdded,
                                   Set<String> registeredLuaPaths) {
        if (justAdded.contains(path)) {
            return PathDecision.REJECT_DUPLICATE_THIS_CYCLE;
        }
        if (registeredLuaPaths.contains(path)) {
            return PathDecision.SKIP_ALREADY_LIVE;
        }
        String[] segments = path.split("/");
        StringBuilder prefix = new StringBuilder();
        for (String segment : segments) {
            if (prefix.length() > 0) prefix.append('/');
            prefix.append(segment);
            if (prefix.indexOf("/") < 0 && isOwnLiveRoot(prefix.toString(), registeredLuaPaths)) {
                continue;
            }
            if (occupied.contains(prefix.toString())) {
                return PathDecision.REJECT_OCCUPIED;
            }
        }
        return PathDecision.REGISTER;
    }

    /**
     * Whether a root literal was registered by Lua in an earlier cycle (i.e. it
     * is the first segment of a live Lua path). Merging a new sibling under it
     * is safe; stepping into a vanilla/foreign root is not. Pure string logic
     * so reload diffing stays unit-testable.
     */
    static boolean isOwnLiveRoot(String root, Set<String> registeredLuaPaths) {
        for (String live : registeredLuaPaths) {
            if (live.equals(root) || live.startsWith(root + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers one command path (primary or alias) as nested literals. Every
     * prefix level is checked against occupied paths so two commands sharing a
     * root (e.g. {@code shop/buy} and {@code shop/list}) coexist, while a path
     * stepping into an existing command is rejected loudly.
     */
    private void registerLuaPath(CommandDispatcher<CommandSourceStack> dispatcher,
                                 Set<String> occupied,
                                 Set<String> justAdded,
                                 CommandDefinition def,
                                 String path) {
        switch (decidePath(path, occupied, justAdded, registeredLuaPaths)) {
            case SKIP_ALREADY_LIVE -> {
                return;
            }
            case REJECT_DUPLICATE_THIS_CYCLE -> {
                LuaTweakerLog.get().error(LogStage.COMMAND,
                        "Lua command '/" + path + "' from mod '" + def.modId()
                                + "' NOT registered: registered twice in the same load cycle.");
                return;
            }
            case REJECT_OCCUPIED -> {
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
                return;
            }
            case REGISTER -> {
                // Fall through to node building below.
            }
        }

        LuaCommandWrapper wrapper = new LuaCommandWrapper(def.name());
        dispatcher.register(buildLuaPathNode(wrapper, segmentsOf(path), 0));
        occupied.add(path);
        justAdded.add(path);
        registeredLuaPaths.add(path);
    }

    private static String[] segmentsOf(String path) {
        return path.split("/");
    }

    /** Builds nested literals for a path; the deepest literal carries the args node. */
    private LiteralArgumentBuilder<CommandSourceStack> buildLuaPathNode(ILuaTweakerCommand cmd,
                                                                        String[] segments,
                                                                        int index) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(segments[index])
                // Live permission gate: hidden from tab-complete and rejected at
                // parse time for unauthorized senders (the wrapper re-checks at
                // execution too, so a /lt reload that changes the level applies
                // immediately). Without this every op-4 command name and its
                // suggestions would leak to level-0 players.
                .requires(src -> hasLuaPermission(src, cmd));
        if (index == segments.length - 1) {
            return literal
                    .then(buildArgsNode(cmd))
                    .executes(ctx -> cmd.executeRaw(new NeoForgeCommandSender(ctx.getSource()), ""));
        }
        return literal.then(buildLuaPathNode(cmd, segments, index + 1));
    }

    /**
     * Reads the command's CURRENT permission level (Lua definitions resolve
     * live, so reloads apply without re-registering nodes). Never throws:
     * a broken lookup fails closed.
     */
    private static boolean hasLuaPermission(CommandSourceStack src, ILuaTweakerCommand cmd) {
        try {
            return new NeoForgeCommandSender(src).hasPermission(cmd.getPermissionLevel());
        } catch (Exception e) {
            return false;
        }
    }
}
