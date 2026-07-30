package com.luatweaker.api.entity;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Represents a Minecraft player entity wrapper with messaging, inventory, and stats interaction.")
public interface IPlayer extends IEntity {
    @LuaDoc(description = "Sends a chat message to the player.", params = {"message: string"})
    void sendMessage(String message);

    @LuaDoc(description = "Displays an action bar message (above hotbar) to the player.", params = {"message: string"})
    void sendActionBar(String message);

    @LuaDoc(description = "Returns the player's username.", returnType = "string")
    String getName();

    @LuaDoc(description = "Returns the player's UUID string.", returnType = "string")
    String getUuid();

    @LuaDoc(description = "Checks if the player is currently sneaking (holding Shift).", returnType = "boolean")
    boolean isSneaking();

    @LuaDoc(description = "Checks if the player is in Creative Mode.", returnType = "boolean")
    boolean isCreative();

    @LuaDoc(description = "Gives an item to the player inventory.", params = {"itemId: string", "count: integer"})
    void giveItem(String itemId, int count);

    @LuaDoc(description = "Gives experience points to the player.", params = {"exp: integer"})
    void giveExperience(int exp);

    @LuaDoc(description = "Applies a potion effect to the player.", params = {"effectId: string", "durationTicks: integer", "amplifier: integer"})
    void addEffect(String effectId, int durationTicks, int amplifier);

    @LuaDoc(description = "Plays a sound event at the player's position. e.g. player:playSound('luatweaker:ruby_equip') or player:playSound('minecraft:entity.experience_orb.pickup', 1.0, 1.0)",
            params = {"soundId: string — ResourceLocation of sound event", "volume: number (optional, default 1.0)", "pitch: number (optional, default 1.0)"})
    void playSound(String soundId, float volume, float pitch);
}
