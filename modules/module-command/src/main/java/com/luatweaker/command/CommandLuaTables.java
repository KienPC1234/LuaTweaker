package com.luatweaker.command;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.entities.EntitiesLuaBinding;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Lua tables handed to command handlers ({@code sender}, {@code args}).
 * Split from {@link LuaCommandWrapper} so the wrapper stays focused on dispatch.
 */
final class CommandLuaTables {

    private CommandLuaTables() {}

    /**
     * Builds the {@code sender} table passed to the Lua handler. Exposes the
     * sender identity, the full player table (nil for console) and message /
     * permission helpers, both PascalCase and camelCase.
     */
    static @NotNull ILuaTable buildSenderTable(@NotNull ILuaEngine engine, @NotNull ICommandSender sender) {
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
            int off = LuaBinder.getOffset(args);
            sender.sendMessage(args[off].asString());
            return engine.nilValue();
        };
        table.rawset("SendMessage", sendMessage);
        table.rawset("sendMessage", sendMessage);

        ILuaFunction sendSuccess = args -> {
            int off = LuaBinder.getOffset(args);
            sender.sendSuccess(args[off].asString());
            return engine.nilValue();
        };
        table.rawset("SendSuccess", sendSuccess);
        table.rawset("sendSuccess", sendSuccess);

        ILuaFunction sendError = args -> {
            int off = LuaBinder.getOffset(args);
            sender.sendError(args[off].asString());
            return engine.nilValue();
        };
        table.rawset("SendError", sendError);
        table.rawset("sendError", sendError);

        ILuaFunction permissionCheck = args -> {
            int off = LuaBinder.getOffset(args);
            int level = off < args.length && args[off] != null ? args[off].asInt() : 2;
            return engine.wrapBoolean(sender.hasPermission(level));
        };
        table.rawset("HasPermission", permissionCheck);
        table.rawset("hasPermission", permissionCheck);

        return table;
    }

    /** Builds the {@code args} table: a pure 1-based string array (no extra keys). */
    static @NotNull ILuaTable buildArgsTable(@NotNull ILuaEngine engine, @NotNull String... args) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < args.length; i++) {
            table.rawset(i + 1, engine.wrapString(args[i]));
        }
        return table;
    }

    static @NotNull ILuaTable buildArgsTable(@NotNull ILuaEngine engine, @NotNull List<String> args) {
        return buildArgsTable(engine, args.toArray(new String[0]));
    }

    static @NotNull List<String> tokenize(@NotNull String tail) {
        return tail == null || tail.isBlank() ? List.of() : List.of(tail.trim().split("\\s+"));
    }

    /** Reads a Lua table of strings (array part) into a Java list; non-tables yield an empty list. */
    static @NotNull List<String> readStringList(@NotNull ILuaValue value) {
        if (value == null || value.isNil() || !value.isTable()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        ILuaTable table = value.asTable();
        int count = table.length();
        for (int i = 1; i <= count; i++) {
            result.add(table.rawget(i).asString());
        }
        return List.copyOf(result);
    }
}
