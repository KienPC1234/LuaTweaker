package com.luatweaker.api.command;

import com.luatweaker.api.entity.IPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Platform-agnostic representation of a command sender (player or console).
 * Concrete implementations live in each platform module.
 */
public interface ICommandSender {
    /** Send a plain text message to this sender. */
    void sendMessage(String message);

    /** Send a success (green) feedback message. */
    void sendSuccess(String message);

    /** Send an error (red) feedback message. */
    void sendError(String message);

    /** @return true if the sender has operator-level permission. */
    boolean hasPermission(int level);

    /** @return true if the sender is a player (false for the server console). */
    boolean isPlayer();

    /**
     * @return the player entity when the sender is a player, null for the console.
     * Used by Lua command handlers via the {@code sender.Player} table.
     */
    @Nullable
    IPlayer getPlayer();

    /** @return the display name of the sender. */
    String getName();

    /**
     * If the sender is a player holding an item, returns its registry name
     * (e.g. {@code "minecraft:diamond_sword"}). Returns empty string otherwise.
     */
    String getHeldItemId();
}
