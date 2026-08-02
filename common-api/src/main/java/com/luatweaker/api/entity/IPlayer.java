package com.luatweaker.api.entity;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.annotation.LuaDefault;

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
    void giveItem(String itemId, @LuaDefault("1") int count);

    @LuaDoc(description = "Gives experience points to the player.", params = {"exp: integer"})
    void giveExperience(int exp);

    @LuaDoc(description = "Applies a potion effect to the player.", params = {"effectId: string", "durationTicks: integer", "amplifier: integer"})
    void addEffect(String effectId, int durationTicks, int amplifier);

    @LuaDoc(description = "Plays a sound event at the player's position. e.g. player:playSound('luatweaker:ruby_equip') or player:playSound('minecraft:entity.experience_orb.pickup', 1.0, 1.0)",
            params = {"soundId: string — ResourceLocation of sound event", "volume: number (optional, default 1.0)", "pitch: number (optional, default 1.0)"})
    void playSound(String soundId, float volume, float pitch);

    @LuaDoc(description = "Returns item ID held in main hand.", returnType = "string")
    default String getMainHandItem() { return "minecraft:air"; }

    @LuaDoc(description = "Returns item ID held in off hand.", returnType = "string")
    default String getOffHandItem() { return "minecraft:air"; }

    @LuaDoc(description = "Sets item held in main hand.", params = {"itemId: string", "count: integer"})
    default void setMainHandItem(String itemId, @LuaDefault("1") int count) {}

    @LuaDoc(description = "Clears all items from the player's inventory.")
    default void clearInventory() {}

    @LuaDoc(description = "Drops specified item from the player.", params = {"itemId: string", "count: integer"})
    default void dropItem(String itemId, @LuaDefault("1") int count) {}

    @LuaDoc(description = "Returns current hunger / food level.", returnType = "integer")
    default int getFoodLevel() { return 20; }

    @LuaDoc(description = "Sets hunger / food level.", params = {"level: integer"})
    default void setFoodLevel(int level) {}

    @LuaDoc(description = "Displays title and subtitle on player screen.", params = {"title: string", "subtitle: string", "fadeIn: integer", "stay: integer", "fadeOut: integer"})
    default void sendTitle(String title, String subtitle, @LuaDefault("10") int fadeInTicks, @LuaDefault("70") int stayTicks, @LuaDefault("20") int fadeOutTicks) {}
    default void sendTitle(String title, String subtitle) { sendTitle(title, subtitle, 10, 70, 20); }

    @LuaDoc(description = "Heals the player by specified health amount.", params = {"amount: number"})
    default void heal(float amount) { setHealth(Math.min(getMaxHealth(), getHealth() + Math.max(0, amount))); }

    @LuaDoc(description = "Feeds the player by adding food and saturation points.", params = {"foodAmount: integer", "saturation: number"})
    default void feed(int foodAmount, @LuaDefault("1.0") float saturation) {}

    @LuaDoc(description = "Teleports player to target coordinates.", params = {"x: number", "y: number", "z: number"})
    default void teleport(double x, double y, double z) {}
}
