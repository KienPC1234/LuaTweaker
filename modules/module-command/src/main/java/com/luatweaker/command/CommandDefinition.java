package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable definition of a command registered from a Lua mod.
 *
 * @param modId             the manifest id of the owning Lua mod
 * @param name              the primary command path (e.g. {@code "hello"} or {@code "shop/buy"})
 * @param description       one-line help text
 * @param permissionLevel   required op level (0 = all, 2 = op, 4 = owner)
 * @param consoleAllowed    whether the server console may run it
 * @param usage             usage hint shown on handler failure
 * @param aliases           extra paths pointing to the same command (already validated, no self-duplicates)
 * @param suggestionProvider Lua function {@code (sender, args) -> table of strings},
 *                          or a static Lua table of strings, or null when no tab completion
 * @param engine            the Lua engine the handler belongs to (reloads replace it)
 * @param handler           the Lua handler {@code function(sender, args)}
 */
public record CommandDefinition(
        @NotNull String modId,
        @NotNull String name,
        @NotNull String description,
        int permissionLevel,
        boolean consoleAllowed,
        @NotNull String usage,
        @NotNull List<String> aliases,
        @Nullable ILuaValue suggestionProvider,
        @NotNull ILuaEngine engine,
        @NotNull ILuaValue handler
) {
}
