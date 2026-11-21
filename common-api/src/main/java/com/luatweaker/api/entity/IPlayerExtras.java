package com.luatweaker.api.entity;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import org.jetbrains.annotations.NotNull;

/**
 * Player-only extras kept separate from {@link IEntity}/{@link IPlayer} so the base entity
 * contract stays focused. Lua binding/scans use {@code getMethods()}, so scripts still see
 * every method on the same player table.
 */
@LuaDoc(description = "Player-specific text, latency, kick and game-mode primitives.")
public interface IPlayerExtras {

    @LuaDoc(description = "Sends a rich text component built with the Text service.",
            params = {"text: TextSpec"})
    default void sendText(@NotNull com.luatweaker.api.text.TextSpec text) {}

    @LuaDoc(description = "Displays a rich text component on the action bar.",
            params = {"text: TextSpec"})
    default void sendActionBarText(@NotNull com.luatweaker.api.text.TextSpec text) {}

    @LuaDoc(description = "Displays a rich text title and subtitle on the player's screen.",
            params = {"title: TextSpec", "subtitle: TextSpec", "[fadeInTicks: integer]",
                      "[stayTicks: integer]", "[fadeOutTicks: integer]"})
    default void sendTitleText(@NotNull com.luatweaker.api.text.TextSpec title,
                               @NotNull com.luatweaker.api.text.TextSpec subtitle,
                               @LuaDefault("10") int fadeInTicks,
                               @LuaDefault("70") int stayTicks,
                               @LuaDefault("20") int fadeOutTicks) {}

    @LuaDoc(description = "Returns the player's connection latency in milliseconds, or 0.",
            returnType = LuaDocFragments.Types.INTEGER)
    default int getPing() { return 0; }

    @LuaDoc(description = "Disconnects the player from the server with a reason.",
            params = {"reason: string"}, returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean kick(@NotNull String reason) { return false; }

    @LuaDoc(description = "Returns the player's game mode: SURVIVAL, CREATIVE, ADVENTURE or SPECTATOR.",
            returnType = LuaDocFragments.Types.STRING)
    default String getGameMode() { return "SURVIVAL"; }

    @LuaDoc(description = "Sets player game mode (SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR).", params = {"gameMode: string"})
    default void setGameMode(@NotNull String gameMode) {}

    @LuaDoc(description = "Checks whether player can fly.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean canFly() { return false; }

    @LuaDoc(
        description = "Checks whether the player holds an operator permission level (vanilla op levels 0-4). "
            + "Read server-side from the op list, so a malicious client cannot forge it: "
            + "guard every privileged RemoteEvent/RemoteFunction with this. "
            + "Fail-closed: false outside a dedicated/integrated server.",
        params = {"level: integer"},
        returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean hasPermissionLevel(int level) { return false; }

    @LuaDoc(description = "Sets whether player is allowed to fly.", params = {"canFly: boolean"})
    default void setCanFly(boolean canFly) {}

    @LuaDoc(description = "Sets whether player is actively flying.", params = {"flying: boolean"})
    default void setFlying(boolean flying) {}

    @LuaDoc(description = "Returns player experience level.", returnType = LuaDocFragments.Types.INTEGER)
    default int getLevel() { return 0; }

    @LuaDoc(description = "Sets player experience level.", params = {"level: integer"})
    default void setLevel(int level) {}

    @LuaDoc(description = "Returns current progress to next level (0.0 to 1.0).", returnType = LuaDocFragments.Types.NUMBER)
    default float getExperienceProgress() { return 0.0f; }

    @LuaDoc(description = "Sets current progress to next level (0.0 to 1.0).", params = {"progress: number"})
    default void setExperienceProgress(float progress) {}

    @LuaDoc(description = "Returns total experience points player has earned.", returnType = LuaDocFragments.Types.INTEGER)
    default int getTotalExperience() { return 0; }

    @LuaDoc(description = "Returns currently selected hotbar slot (0-8).", returnType = LuaDocFragments.Types.INTEGER)
    default int getSelectedSlot() { return 0; }

    @LuaDoc(description = "Sets currently selected hotbar slot (0-8).", params = {"slot: integer"})
    default void setSelectedSlot(int slot) {}

    @LuaDoc(description = "Checks whether an item is on cooldown for player.", params = {LuaDocFragments.Content.ITEM_ID}, returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean hasItemCooldown(@NotNull String itemId) { return false; }

    @LuaDoc(description = "Sets cooldown ticks for specified item ID.", params = {LuaDocFragments.Content.ITEM_ID, "ticks: integer"})
    default void setItemCooldown(@NotNull String itemId, int ticks) {}
}
