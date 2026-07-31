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

    @LuaDoc(description = "Heals the entity by specified amount.", params = {"amount: number"})
    default void heal(float amount) {
        setHealth(Math.min(getMaxHealth(), getHealth() + Math.max(0, amount)));
    }

    @LuaDoc(description = "Kills the entity instantly.")
    default void kill() { remove(); }

    @LuaDoc(description = "Applies a potion status effect to the entity (e.g. 'poison', 'slowness', 'glowing', 'levitation', 'wither', 'speed').", params = {"effectId: string", "[durationTicks: integer]", "[amplifier: integer]"})
    default void addEffect(String effectId, int durationTicks, int amplifier) {}

    @LuaDoc(description = "Removes a specific status effect from the entity.", params = {"effectId: string"})
    default void removeEffect(String effectId) {}

    @LuaDoc(description = "Removes all status potion effects from the entity.")
    default void removeAllEffects() {}

    @LuaDoc(description = "Checks if the entity has a active status effect.", params = {"effectId: string"}, returnType = "boolean")
    default boolean hasEffect(String effectId) { return false; }

    @LuaDoc(description = "Checks if the entity is alive.", returnType = "boolean")
    boolean isAlive();

    @LuaDoc(description = "Sets the entity on fire for specified seconds.", params = {"seconds: integer"})
    default void setIgniteSeconds(int seconds) {}

    @LuaDoc(description = "Extinguishes fire on the entity.")
    default void extinguish() {}

    @LuaDoc(description = "Plays a sound at the entity's position.", params = {"soundId: string", "[volume: number]", "[pitch: number]"})
    default void playSound(String soundId, float volume, float pitch) {}

    @LuaDoc(description = "Spawns particle effect at entity location.", params = {"particleId: string", "count: integer", "speed: number"})
    default void spawnParticle(String particleId, int count, double speed) {}

    @LuaDoc(description = "Teleports the entity to coordinates.", params = {"x: number", "y: number", "z: number"})
    default void teleport(double x, double y, double z) {}

    @LuaDoc(description = "Sets velocity / motion vector of the entity.", params = {"vx: number", "vy: number", "vz: number"})
    default void setMotion(double vx, double vy, double vz) {}

    @LuaDoc(description = "Adds impulse velocity to entity.", params = {"vx: number", "vy: number", "vz: number"})
    default void addVelocity(double vx, double vy, double vz) {}

    default double getX() { return 0; }
    default double getY() { return 0; }
    default double getZ() { return 0; }
    default float getYaw() { return 0; }
    default float getPitch() { return 0; }
    default void setYaw(float yaw) {}
    default void setPitch(float pitch) {}

    default boolean isSneaking() { return false; }
    default void setSneaking(boolean value) {}
    default boolean isSprinting() { return false; }
    default void setSprinting(boolean value) {}
    default boolean isGlowing() { return false; }
    default void setGlowing(boolean value) {}
    default boolean isInvulnerable() { return false; }
    default void setInvulnerable(boolean value) {}
    default boolean isInWater() { return false; }
    default boolean isInLava() { return false; }
    default boolean isOnGround() { return false; }

    default String getCustomName() { return ""; }
    default void setCustomName(String name) {}
    default boolean isCustomNameVisible() { return false; }
    default void setCustomNameVisible(boolean visible) {}

    @LuaDoc(description = "Adds a scoreboard / entity tag.", params = {"tag: string"})
    default void addTag(String tag) {}

    @LuaDoc(description = "Removes a scoreboard / entity tag.", params = {"tag: string"})
    default void removeTag(String tag) {}

    @LuaDoc(description = "Checks if entity has tag.", params = {"tag: string"}, returnType = "boolean")
    default boolean hasTag(String tag) { return false; }

    @LuaDoc(description = "Swings the entity's main arm.")
    default void swingArm() {}

    @LuaDoc(description = "Sets the combat target entity for mob AI.", params = {"targetEntity: IEntity"})
    default void setTarget(IEntity target) {}

    @LuaDoc(description = "Returns current combat target entity for mob AI.", returnType = "IEntity")
    default IEntity getTarget() { return null; }

    default double distanceTo(IEntity other) {
        if (other == null) return Double.MAX_VALUE;
        double dx = getX() - other.getX();
        double dy = getY() - other.getY();
        double dz = getZ() - other.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    default boolean isPlayer() { return false; }
    default boolean isLiving() { return false; }

    @LuaDoc(description = "Removes / kills the entity.")
    void remove();

    Object getRawEntity();
}
