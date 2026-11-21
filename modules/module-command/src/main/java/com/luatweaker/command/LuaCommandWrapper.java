package com.luatweaker.command;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

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
    public int execute(@NotNull ICommandSender sender, @NotNull String... args) {
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
        if (def == null) {
            return List.of();
        }
        ILuaValue provider = def.suggestionProvider();
        if (provider == null || provider.isNil()) {
            return List.of();
        }
        ILuaEngine engine = def.engine();
        try {
            ILuaValue result;
            if (provider.isFunction()) {
                ILuaTable argsTable = CommandLuaTables.buildArgsTable(engine,
                        CommandLuaTables.tokenize(typedTail));
                result = engine.callFunction(provider, CommandLuaTables.buildSenderTable(engine, sender), argsTable);
            } else {
                result = provider;
            }
            return CommandLuaTables.readStringList(result);
        } catch (LuaException | IllegalStateException e) {
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
        if (!checkSenderAccess(sender, def)) {
            return 0;
        }

        ILuaEngine engine = def.engine();
        try {
            ILuaValue result = engine.callFunction(
                    def.handler(),
                    CommandLuaTables.buildSenderTable(engine, sender),
                    CommandLuaTables.buildArgsTable(engine, args),
                    engine.wrapString(rawTail)
            );
            if (engine.getAndClearLastExecutionError() != null) {
                // The handler raised a Lua error: the engine already logged it loudly.
                sender.sendError("Lua command '/" + name + "' crashed. Check the log for details.");
                sendUsageHint(sender, def);
                return 0;
            }
            return handleHandlerResult(sender, def, result);
        } catch (LuaException | IllegalStateException e) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Error executing Lua command '/" + name + "': " + e.getMessage());
            sender.sendError("Lua command '/" + name + "' crashed: " + e.getMessage());
            sendUsageHint(sender, def);
            return 0;
        }
    }

    /**
     * Re-validates permission and sender kind at execution time. Returns false
     * (after messaging the sender) when the command must not run.
     */
    private boolean checkSenderAccess(@NotNull ICommandSender sender, @NotNull CommandDefinition def) {
        if (!sender.hasPermission(def.permissionLevel())) {
            sender.sendError("You need operator level " + def.permissionLevel() + " to use /" + name + ".");
            return false;
        }
        if (!def.consoleAllowed() && !sender.isPlayer()) {
            sender.sendError("/" + name + " can only be used by players.");
            return false;
        }
        return true;
    }

    /**
     * Maps a Lua handler return to a Brigadier code. Lua truthiness: only
     * false and nil are failures (a false handler also gets the usage hint).
     */
    private int handleHandlerResult(@NotNull ICommandSender sender, @NotNull CommandDefinition def,
                                    @Nullable ILuaValue result) {
        if (result == null || result.isNil()) {
            return 1;
        }
        if (result.toJavaObject() instanceof Boolean boolResult) {
            if (!boolResult) {
                sendUsageHint(sender, def);
            }
            return boolResult ? 1 : 0;
        }
        return 1;
    }

    /**
     * Sends the definition's {@code Usage} hint, if the mod declared one. Called
     * on every handler-failure path so the documented "usage hint sent when the
     * handler fails" contract actually holds.
     */
    private static void sendUsageHint(@NotNull ICommandSender sender, @NotNull CommandDefinition def) {
        if (def.usage() != null && !def.usage().isBlank()) {
            sender.sendMessage("Usage: " + def.usage());
        }
    }
}
