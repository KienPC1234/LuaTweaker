package com.luatweaker.api.entity;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Represents a generic Minecraft entity wrapper for manipulating health, effects, position, damage, and sounds.")
public interface IEntity {
    @LuaDoc(description = "Returns the entity type ID (e.g. 'minecraft:zombie').", returnType = "string")
    String getType();

    @LuaDoc(description = "Returns the display name of the entity.", returnType = "string")
    String getName();

    @LuaDoc(description = "Returns current health of the entity.", returnType = "number")
    float getHealth();

    @LuaDoc(description = "Sets health of the entity.", params = {"health: number"})
    void setHealth(float health);

    @LuaDoc(description = "Returns maximum health of the entity.", returnType = "number")
    float getMaxHealth();

    @LuaDoc(description = "Deals generic damage to the entity.", params = {"amount: number"})
    default void damage(float amount) {}

    @LuaDoc(description = "Deals generic damage to the entity.", params = {"amount: number"})
    default void hurt(float amount) { damage(amount); }

    @LuaDoc(description = "Applies a potion status effect to the entity (e.g. 'poison', 'slowness', 'glowing', 'levitation', 'wither', 'speed').", params = {"effectId: string", "[durationTicks: integer]", "[amplifier: integer]"})
    default void addEffect(String effectId, int durationTicks, int amplifier) {}

    @LuaDoc(description = "Removes all status potion effects from the entity.")
    default void removeAllEffects() {}

    @LuaDoc(description = "Checks if the entity is alive.", returnType = "boolean")
    boolean isAlive();

    @LuaDoc(description = "Sets the entity on fire for specified seconds.", params = {"seconds: integer"})
    default void setIgniteSeconds(int seconds) {}

    @LuaDoc(description = "Plays a sound at the entity's position.", params = {"soundId: string", "[volume: number]", "[pitch: number]"})
    default void playSound(String soundId, float volume, float pitch) {}

    @LuaDoc(description = "Teleports the entity to coordinates.", params = {"x: number", "y: number", "z: number"})
    default void teleport(double x, double y, double z) {}

    @LuaDoc(description = "Sets velocity / motion vector of the entity.", params = {"vx: number", "vy: number", "vz: number"})
    default void setMotion(double vx, double vy, double vz) {}

    default double getX() { return 0; }
    default double getY() { return 0; }
    default double getZ() { return 0; }

    default boolean isPlayer() { return false; }
    default boolean isLiving() { return false; }

    @LuaDoc(description = "Removes / kills the entity.")
    void remove();

    Object getRawEntity();
}
