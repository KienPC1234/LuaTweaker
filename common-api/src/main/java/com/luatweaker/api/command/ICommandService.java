package com.luatweaker.api.command;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side command registration service for Lua mods.
 *
 * <p>Commands registered here become top-level Brigadier commands (no
 * {@code /lt} prefix required): {@code Commands:Register("hello", ...)} creates
 * {@code /hello}. A slash-separated path creates nested literals, e.g.
 * {@code "shop/buy"} creates {@code /shop buy}. Commands are checked against
 * reserved names (core {@code /lt} tree + vanilla conflicts) and against other
 * mods: a name/path already owned by a different mod is rejected loudly.</p>
 *
 * <p>Definition table fields (PascalCase and camelCase aliases accepted):</p>
 * <ul>
 *   <li>{@code Description} (string) - one-line help text.</li>
 *   <li>{@code PermissionLevel} (number 0-4, default 2) - 0 = all, 2 = op, 4 = owner.</li>
 *   <li>{@code ConsoleAllowed} (boolean, default true) - false = players only.</li>
 *   <li>{@code Usage} (string, optional) - usage hint sent when the handler fails.</li>
 *   <li>{@code Aliases} (table of strings, optional) - extra names/paths that
 *       point to the same command.</li>
 *   <li>{@code Suggestions} (function or table of strings, optional) - tab
 *       completion. A function receives {@code (sender, args)} and returns a
 *       table of suggestions; a static table is used as-is.</li>
 *   <li>{@code Handler} (function, required) - {@code function(sender, args, raw)}
 *       where {@code sender} exposes Name, IsPlayer, PlayerId, Player,
 *       SendMessage, SendSuccess, SendError, HasPermission; {@code args} is the
 *       1-based token array; {@code raw} is the full tail exactly as the player
 *       typed it. Return false/nil to report failure, true to report success.</li>
 * </ul>
 */
@LuaDoc(description = "Server-side command registration service for Lua mods. Registered commands become top-level commands without a /lt prefix.")
public interface ICommandService {

    @LuaDoc(
        description = "Registers a top-level server-side command (e.g. /hello, or nested /shop buy). The owning mod id is taken from the mod being loaded. Re-registering the same mod+name replaces the previous definition (reload-safe); another mod claiming an existing name or a reserved/vanilla name is rejected.",
        params = {"name: string - command name or slash-separated path (a-z, 0-9, underscore, colon; segments max 32 chars, max 4 levels)", "definition: table - { Description, PermissionLevel, ConsoleAllowed, Usage, Aliases, Suggestions, Handler }"},
        returnType = "boolean"
    )
    boolean register(@NotNull String name, @NotNull ILuaTable definition);

    @LuaDoc(
        description = "Unregisters a command (and its aliases) owned by this mod. Unregistering another mod's command is rejected.",
        params = {"name: string"},
        returnType = "boolean"
    )
    boolean unregister(@NotNull String name);
}
