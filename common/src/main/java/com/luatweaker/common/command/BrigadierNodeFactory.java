package com.luatweaker.common.command;

import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.command.LuaCommandWrapper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jetbrains.annotations.NotNull;
import org.luava.runtime.LuaException;

import java.util.Set;

/**
 * Builds the Brigadier nodes for one {@link ILuaTweakerCommand}.
 *
 * <p>Kept separate from {@link LuaTweakerCommandRegistry} so the registry only owns
 * registration bookkeeping while the Brigadier DSL wiring lives here.</p>
 */
final class BrigadierNodeFactory {

    private BrigadierNodeFactory() {
    }

    /** Builds a top-level literal (plus its two execution variants) for a built-in command. */
    static @NotNull LiteralArgumentBuilder<CommandSourceStack> subCommand(@NotNull ILuaTweakerCommand cmd) {
        return Commands.literal(cmd.getName())
                .requires(src -> src.hasPermission(cmd.getPermissionLevel()))
                // Variant with optional trailing arguments
                .then(argsNode(cmd))
                // Variant with no arguments
                .executes(ctx -> cmd.executeRaw(new MinecraftCommandSender(ctx.getSource()), ""));
    }

    /**
     * Builds the greedy-string argument node carrying the raw tail. Lua commands
     * get tab-completion from their optional {@code Suggestions} provider.
     */
    static @NotNull RequiredArgumentBuilder<CommandSourceStack, String> argsNode(@NotNull ILuaTweakerCommand cmd) {
        RequiredArgumentBuilder<CommandSourceStack, String> argsNode =
                Commands.argument("args", StringArgumentType.greedyString());
        if (cmd instanceof LuaCommandWrapper luaCmd) {
            argsNode = argsNode.suggests((ctx, builder) -> {
                for (String suggestion : luaCmd.suggest(
                        new MinecraftCommandSender(ctx.getSource()),
                        builder.getRemaining()
                )) {
                    builder.suggest(suggestion);
                }
                return builder.buildFuture();
            });
        }
        return argsNode.executes(ctx -> {
            String raw = StringArgumentType.getString(ctx, "args");
            return cmd.executeRaw(new MinecraftCommandSender(ctx.getSource()), raw);
        });
    }

    /** Builds nested literals for a path; the deepest literal carries the args node. */
    static @NotNull LiteralArgumentBuilder<CommandSourceStack> luaPathNode(@NotNull ILuaTweakerCommand cmd,
                                                                           @NotNull String[] segments,
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
                    .then(argsNode(cmd))
                    .executes(ctx -> cmd.executeRaw(new MinecraftCommandSender(ctx.getSource()), ""));
        }
        return literal.then(luaPathNode(cmd, segments, index + 1));
    }

    /**
     * Reads the command's CURRENT permission level (Lua definitions resolve
     * live, so reloads apply without re-registering nodes). Never throws:
     * a broken lookup fails closed.
     */
    private static boolean hasLuaPermission(@NotNull CommandSourceStack src, @NotNull ILuaTweakerCommand cmd) {
        try {
            return new MinecraftCommandSender(src).hasPermission(cmd.getPermissionLevel());
        } catch (LuaException | IllegalStateException e) {
            // Fails closed: an unreadable permission level must never expose the command.
            return false;
        }
    }

    /**
     * First prefix of {@code path} that is already taken by another command, or the
     * whole path when nothing collides. Used for the loud collision message.
     */
    static @NotNull String firstOccupiedPrefix(@NotNull String path, @NotNull Set<String> occupied) {
        StringBuilder prefix = new StringBuilder();
        for (String segment : path.split("/")) {
            if (prefix.length() > 0) {
                prefix.append('/');
            }
            prefix.append(segment);
            if (occupied.contains(prefix.toString())) {
                return prefix.toString();
            }
        }
        return path;
    }
}
