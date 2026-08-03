package com.luatweaker.command;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.entities.EntitiesLuaBinding;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts a Lua-registered command to the platform's {@link ILuaTweakerCommand}
 * tree. The definition is resolved LIVE at execution time from the shared
 * {@link CommandServiceImpl} map, so after {@code /lt reload} the Brigadier node
 * dispatches to the newest engine's handler instead of a dead engine closure.
 */
public final class LuaCommandWrapper implements ILuaTweakerCommand {

    private final String name;

    public LuaCommandWrapper(@NotNull String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        CommandDefinition def = CommandServiceImpl.get(name);
        return def != null ? "[" + def.modId() + "] " + def.description()
                : "Lua command (no mod registered it)";
    }

    @Override
    public int getPermissionLevel() {
        CommandDefinition def = CommandServiceImpl.get(name);
        return def != null ? def.permissionLevel() : 2;
    }

    @Override
    public boolean isConsoleAllowed() {
        CommandDefinition def = CommandServiceImpl.get(name);
        return def == null || def.consoleAllowed();
    }

    @Override
    public int execute(@NotNull ICommandSender sender, @NotNull String[] args) {
        return dispatch(sender, args, String.join(" ", args));
    }

    @Override
    public int executeRaw(@NotNull ICommandSender sender, @NotNull String raw) {
        // trim() first: String.split keeps a leading empty token for leading whitespace.
        String trimmed = raw == null ? "" : raw.trim();
        String[] args = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
        return dispatch(sender, args, raw == null ? "" : raw);
    }

    /**
     * Computes tab-completion suggestions from the command's optional
     * {@code Suggestions} provider (a Lua function or a static table).
     */
    public @NotNull List<String> suggest(@NotNull ICommandSender sender, @NotNull String typedTail) {
        CommandDefinition def = CommandServiceImpl.get(name);
        if (def == null) return List.of();
        ILuaValue provider = def.suggestionProvider();
        if (provider == null || provider.isNil()) return List.of();
        ILuaEngine engine = def.engine();
        try {
            ILuaValue result;
            if (provider.isFunction()) {
                ILuaTable argsTable = buildArgsTable(engine, tokenize(typedTail));
                result = engine.callFunction(provider, buildSenderTable(engine, sender), argsTable);
            } else {
                result = provider;
            }
            return readStringList(engine, result);
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Error computing suggestions for '/" + name + "': " + e.getMessage());
            return List.of();
        }
    }

    private int dispatch(@NotNull ICommandSender sender, @NotNull String[] args, @NotNull String rawTail) {
        CommandDefinition def = CommandServiceImpl.get(name);
        if (def == null) {
            sender.sendError("Lua command '/" + name + "' is not registered by any loaded mod.");
            return 0;
        }

        // Permission and sender checks are re-validated at execution time so a
        // /lt reload that changed the definition is honored immediately.
        if (!sender.hasPermission(def.permissionLevel())) {
            sender.sendError("You need operator level " + def.permissionLevel() + " to use /" + name + ".");
            return 0;
        }
        if (!def.consoleAllowed() && !sender.isPlayer()) {
            sender.sendError("/" + name + " can only be used by players.");
            return 0;
        }

        ILuaEngine engine = def.engine();
        try {
            ILuaValue result = engine.callFunction(
                    def.handler(),
                    buildSenderTable(engine, sender),
                    buildArgsTable(engine, args),
                    engine.wrapString(rawTail)
            );
            if (engine instanceof CobaltLuaEngine cobaltEngine
                    && cobaltEngine.getAndClearLastExecutionError() != null) {
                // The handler raised a Lua error: the engine already logged it loudly.
                return 0;
            }
            // Lua truthiness: only false and nil are failures.
            if (result == null || result.isNil()) return 1;
            if (result.toJavaObject() instanceof Boolean boolResult) return boolResult ? 1 : 0;
            return 1;
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Error executing Lua command '/" + name + "': " + e.getMessage());
            sender.sendError("Lua command '/" + name + "' crashed: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Builds the {@code sender} table passed to the Lua handler. Exposes the
     * sender identity, the full player table (nil for console) and message /
     * permission helpers, both PascalCase and camelCase.
     */
    private static ILuaTable buildSenderTable(@NotNull ILuaEngine engine, @NotNull ICommandSender sender) {
        ILuaTable table = engine.createTable();

        table.rawset("Name", engine.wrapString(sender.getName()));
        table.rawset("IsPlayer", engine.wrapBoolean(sender.isPlayer()));
        table.rawset("isPlayer", engine.wrapBoolean(sender.isPlayer()));
        table.rawset("HeldItemId", engine.wrapString(sender.getHeldItemId()));

        IPlayer player = sender.getPlayer();
        if (player != null) {
            table.rawset("PlayerId", engine.wrapString(player.getUuid()));
            table.rawset("Player", EntitiesLuaBinding.createPlayerLuaTable(engine, player));
        } else {
            table.rawset("PlayerId", engine.wrapString(""));
            table.rawset("Player", engine.nilValue());
        }

        ILuaFunction sendMessage = args -> {
            int off = (args.length > 0 && args[0] != null && args[0].isTable()) ? 1 : 0;
            sender.sendMessage(args[off].asString());
            return engine.nilValue();
        };
        table.rawset("SendMessage", sendMessage);
        table.rawset("sendMessage", sendMessage);

        ILuaFunction sendSuccess = args -> {
            int off = (args.length > 0 && args[0] != null && args[0].isTable()) ? 1 : 0;
            sender.sendSuccess(args[off].asString());
            return engine.nilValue();
        };
        table.rawset("SendSuccess", sendSuccess);
        table.rawset("sendSuccess", sendSuccess);

        ILuaFunction sendError = args -> {
            int off = (args.length > 0 && args[0] != null && args[0].isTable()) ? 1 : 0;
            sender.sendError(args[off].asString());
            return engine.nilValue();
        };
        table.rawset("SendError", sendError);
        table.rawset("sendError", sendError);

        ILuaFunction hasPermission = args -> {
            int off = (args.length > 0 && args[0] != null && args[0].isTable()) ? 1 : 0;
            int level = off < args.length && args[off] != null ? args[off].asInt() : 2;
            return engine.wrapBoolean(sender.hasPermission(level));
        };
        table.rawset("HasPermission", hasPermission);
        table.rawset("hasPermission", hasPermission);

        return table;
    }

    /** Builds the {@code args} table: a pure 1-based string array (no extra keys). */
    private static ILuaTable buildArgsTable(@NotNull ILuaEngine engine, @NotNull String[] args) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < args.length; i++) {
            table.rawset(i + 1, engine.wrapString(args[i]));
        }
        return table;
    }

    private static ILuaTable buildArgsTable(@NotNull ILuaEngine engine, @NotNull List<String> args) {
        return buildArgsTable(engine, args.toArray(new String[0]));
    }

    private static List<String> tokenize(@NotNull String tail) {
        return tail == null || tail.isBlank() ? List.of() : List.of(tail.trim().split("\\s+"));
    }

    /** Reads a Lua table of strings (array part) into a Java list; non-tables yield an empty list. */
    private static List<String> readStringList(@NotNull ILuaEngine engine, @NotNull ILuaValue value) {
        if (value == null || value.isNil() || !value.isTable()) return List.of();
        List<String> result = new ArrayList<>();
        ILuaTable table = value.asTable();
        int count = table.length();
        for (int i = 1; i <= count; i++) {
            result.add(table.rawget(i).asString());
        }
        return List.copyOf(result);
    }
}
