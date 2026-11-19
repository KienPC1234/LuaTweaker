package com.luatweaker.api.entity;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.annotation.LuaDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@LuaDoc(description = "Represents a generic Minecraft entity wrapper for manipulating health, effects, position, damage, and sounds.")
public interface IEntity {
    @LuaDoc(description = "Returns the entity type ID (e.g. 'minecraft:zombie').", returnType = "string")
    String getType();

    @LuaDoc(description = "Returns the display name of the entity.", returnType = "string")
    String getName();

    @LuaDoc(description = "Returns the entity's UUID string.", returnType = "string")
    default String getUuid() { return ""; }

    @LuaDoc(description = "Sends a chat message if this entity is a player; a no-op otherwise.", params = {"message: string"})
    default void sendMessage(String message) {}

    @LuaDoc(description = "Returns current health of the entity.", returnType = "number")
    float getHealth();

    @LuaDoc(description = "Sets health of the entity.", params = {"health: number"})
    void setHealth(float health);

    @LuaDoc(description = "Returns maximum health of the entity.", returnType = "number")
    float getMaxHealth();

    @LuaDoc(description = "Deals damage with specific Minecraft 1.21 DamageType and optional attacker.", params = {"amount: number", "[damageTypeId: string]", "[attacker: IEntity]"})
    default void applyDamage(float amount, @LuaDefault("minecraft:generic") String damageTypeId, @Nullable IEntity attacker) {
        damage(amount);
    }

    @LuaDoc(description = "Adds impulse force vector to entity velocity.", params = {"vx: number", "vy: number", "vz: number"})
    default void applyImpulse(double vx, double vy, double vz) {
        addVelocity(vx, vy, vz);
    }

    @LuaDoc(description = "Sets armor or hand equipment (mainhand, offhand, head, chest, legs, feet).", params = {"slot: string", "itemId: string", "[count: integer]"})
    default void setEquipment(String slot, String itemId, @LuaDefault("1") int count) {}

    @LuaDoc(description = "Gets equipment item ID in slot (mainhand, offhand, head, chest, legs, feet).", params = {"slot: string"}, returnType = "string")
    default String getEquipment(String slot) { return "minecraft:air"; }

    @LuaDoc(
        description = "Returns the full stack in an equipment slot as {Id, Count, Name?, Damage?}, or nil when the slot is empty.",
        params = {"slot: string"},
        returnType = "table | nil"
    )
    default @Nullable java.util.Map<String, Object> getEquipmentData(String slot) { return null; }

    /**
     * Look direction components, matching Minecraft's {@code Entity#getLookAngle()}:
     * yaw 0 faces +Z, yaw 90 faces -X, positive pitch looks down.
     */
    @LuaDoc(description = "Returns the look direction X component (same as Minecraft's look angle).", returnType = "number")
    default double getLookVectorX() {
        return -Math.sin(Math.toRadians(getYaw())) * Math.cos(Math.toRadians(getPitch()));
    }

    @LuaDoc(description = "Returns the look direction Y component: negative when looking down, positive when looking up.", returnType = "number")
    default double getLookVectorY() {
        return -Math.sin(Math.toRadians(getPitch()));
    }

    @LuaDoc(description = "Returns the look direction Z component (same as Minecraft's look angle).", returnType = "number")
    default double getLookVectorZ() {
        return Math.cos(Math.toRadians(getYaw())) * Math.cos(Math.toRadians(getPitch()));
    }

    @LuaDoc(description = "Raycasts from eye position forward along look vector.", params = {"[maxDistance: number]", "[ignoreLiquids: boolean]"}, returnType = "table")
    default com.luatweaker.api.spatial.RaycastResult raycast(@LuaDefault("64.0") double maxDistance, @LuaDefault("true") boolean ignoreLiquids) {
        return com.luatweaker.api.spatial.RaycastResult.miss(getX(), getY(), getZ());
    }

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
    default void addEffect(String effectId, @LuaDefault("200") int durationTicks, @LuaDefault("0") int amplifier) {}

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
    default void playSound(String soundId, @LuaDefault("1.0") float volume, @LuaDefault("1.0") float pitch) {}

    @LuaDoc(
        description = "Plays a sound at the entity's position in a specific Minecraft sound category "
                    + "(MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE), "
                    + "so it follows that category's volume slider. Unregistered ids are reported and skipped.",
        params = {"soundId: string", "volume: number", "pitch: number", "source: string"},
        returnType = "void"
    )
    default void playSoundFrom(String soundId, @LuaDefault("1.0") float volume,
                               @LuaDefault("1.0") float pitch, @LuaDefault("NEUTRAL") String source) {
        playSound(soundId, volume, pitch);
    }

    @LuaDoc(description = "Spawns particle effect at entity location.", params = {"particleId: string", "count: integer", "speed: number"})
    default void spawnParticle(String particleId, @LuaDefault("1") int count, @LuaDefault("0.0") double speed) {}

    @LuaDoc(description = "Spawns an entity relative to this entity's position.", params = {"entityId: string", "offsetX: number", "offsetY: number", "offsetZ: number"}, returnType = "table")
    default IEntity spawnEntity(String entityId, @LuaDefault("0.0") double offsetX, @LuaDefault("0.0") double offsetY, @LuaDefault("0.0") double offsetZ) { return null; }

    @LuaDoc(description = "Fires a projectile from this entity's eye position toward where it is looking.", params = {"projectileType: string (e.g. 'luatweaker:ruby_orb', 'minecraft:small_fireball')", "[speed: number]", "[inaccuracy: number]"})
    default void shootProjectile(String projectileType, @LuaDefault("1.5") double speed, @LuaDefault("0.0") double inaccuracy) {}

    @LuaDoc(description = "Fires a projectile from this entity toward a target entity. Returns the spawned projectile so scripts can steer it.", params = {"projectileType: string", "target: table (entity)", "[speed: number]"}, returnType = "table | nil")
    default IEntity shootProjectileAt(String projectileType, IEntity target, @LuaDefault("1.5") double speed) { return null; }

    @LuaDoc(description = "Plays a named animation on the entity (stored for client-side rendering).", params = {"animName: string", "[speed: number]", "[transition: number]"})
    default void playAnimation(String animName, @LuaDefault("1.0") double speed, @LuaDefault("0.2") double transition) {}

    /**
     * Legacy string view over the entity's persistent data. Shares the same key namespace as
     * {@link #setCustomData}, so the two APIs can read each other's values.
     */
    @LuaDoc(description = "Stores a string in the entity's persistent data (same key space as SetCustomData; null removes it).", params = {"key: string", "value: string"})
    default void setAttribute(String key, String value) {}

    @LuaDoc(description = "Reads a persistent value as a string (same key space as GetCustomData).", params = {"key: string"}, returnType = "string | nil")
    default String getAttribute(String key) { return null; }

    @LuaDoc(description = "Reads a typed persistent value (string, number or boolean).", params = {"key: string"}, returnType = "any")
    default @Nullable Object getCustomData(@NotNull String key) { return null; }

    /**
     * Stores a typed persistent value.
     *
     * @throws IllegalArgumentException for tables, functions and other values that cannot be
     *                                  represented as a single persistent primitive
     */
    @LuaDoc(description = "Stores a typed persistent value (string, number or boolean; nil removes it). Other Lua types are rejected.", params = {"key: string", "value: any"})
    default void setCustomData(@NotNull String key, @Nullable Object value) {}

    @LuaDoc(description = "Sets the maximum health attribute of the entity.", params = {"maxHealth: number"})
    default void setMaxHealth(float maxHealth) {}

    @LuaDoc(
        description = "Returns a Minecraft attribute value after modifiers (e.g. 'minecraft:generic.movement_speed', 'minecraft:generic.attack_damage'), or nil when the entity lacks it.",
        params = {"attributeId: string"},
        returnType = "number | nil"
    )
    default @Nullable Double getAttributeValue(String attributeId) { return null; }

    @LuaDoc(
        description = "Returns a Minecraft attribute base value (before modifiers), or nil when the entity lacks it.",
        params = {"attributeId: string"},
        returnType = "number | nil"
    )
    default @Nullable Double getAttributeBaseValue(String attributeId) { return null; }

    @LuaDoc(
        description = "Sets a Minecraft attribute base value. Returns false when the entity has no such attribute.",
        params = {"attributeId: string", "value: number"},
        returnType = "boolean"
    )
    default boolean setAttributeBaseValue(String attributeId, double value) { return false; }

    @LuaDoc(
        description = "Adds or replaces a named modifier on a Minecraft attribute. operation: 'ADD_VALUE' (default), 'ADD_MULTIPLIED_BASE' or 'ADD_MULTIPLIED_TOTAL'.",
        params = {"attributeId: string", "modifierId: string", "amount: number", "[operation: string]"},
        returnType = "boolean"
    )
    default boolean addAttributeModifier(String attributeId, String modifierId, double amount,
                                         @LuaDefault("ADD_VALUE") String operation) {
        return false;
    }

    @LuaDoc(
        description = "Removes a named modifier from a Minecraft attribute. Returns whether it existed.",
        params = {"attributeId: string", "modifierId: string"},
        returnType = "boolean"
    )
    default boolean removeAttributeModifier(String attributeId, String modifierId) { return false; }

    @LuaDoc(description = "Returns the current velocity (motion) of the entity.", returnType = "number")
    default double getMotionX() { return 0; }
    default double getMotionY() { return 0; }
    default double getMotionZ() { return 0; }

    @LuaDoc(description = "Teleports the entity to coordinates.", params = {"x: number", "y: number", "z: number"})
    default void teleport(double x, double y, double z) {}

    @LuaDoc(description = "Sets velocity / motion vector of the entity.", params = {"vx: number", "vy: number", "vz: number"})
    default void setMotion(double vx, double vy, double vz) {}

    @LuaDoc(description = "Starts pathfinding toward a position (mobs only). Returns whether navigation started.", params = {"x: number", "y: number", "z: number", "speed: number"}, returnType = "boolean")
    default boolean moveTo(double x, double y, double z, double speed) { return false; }

    @LuaDoc(description = "Adds impulse velocity to entity.", params = {"vx: number", "vy: number", "vz: number"})
    default void addVelocity(double vx, double vy, double vz) {}

    @LuaDoc(description = "Returns the entity's X coordinate.", returnType = "number")
    default double getX() { return 0; }

    @LuaDoc(description = "Returns the entity's Y coordinate.", returnType = "number")
    default double getY() { return 0; }

    @LuaDoc(description = "Returns the entity's Z coordinate.", returnType = "number")
    default double getZ() { return 0; }

    @LuaDoc(description = "Returns the entity's yaw (horizontal rotation, degrees).", returnType = "number")
    default float getYaw() { return 0; }

    @LuaDoc(description = "Returns the entity's pitch (vertical rotation, degrees; positive looks down).", returnType = "number")
    default float getPitch() { return 0; }

    @LuaDoc(description = "Sets the entity's yaw (horizontal rotation, degrees).", params = {"yaw: number"})
    default void setYaw(float yaw) {}

    @LuaDoc(description = "Sets the entity's pitch (vertical rotation, degrees; positive looks down).", params = {"pitch: number"})
    default void setPitch(float pitch) {}

    @LuaDoc(description = "Checks whether the entity is sneaking.", returnType = "boolean")
    default boolean isSneaking() { return false; }

    @LuaDoc(description = "Sets whether the entity is sneaking.", params = {"value: boolean"})
    default void setSneaking(boolean value) {}

    @LuaDoc(description = "Checks whether the entity is sprinting.", returnType = "boolean")
    default boolean isSprinting() { return false; }

    @LuaDoc(description = "Sets whether the entity is sprinting.", params = {"value: boolean"})
    default void setSprinting(boolean value) {}

    @LuaDoc(description = "Checks whether the entity has the glowing outline.", returnType = "boolean")
    default boolean isGlowing() { return false; }

    @LuaDoc(description = "Sets the glowing outline on or off.", params = {"value: boolean"})
    default void setGlowing(boolean value) {}

    @LuaDoc(description = "Checks whether the entity ignores damage.", returnType = "boolean")
    default boolean isInvulnerable() { return false; }

    @LuaDoc(description = "Sets whether the entity ignores damage.", params = {"value: boolean"})
    default void setInvulnerable(boolean value) {}

    @LuaDoc(description = "Checks whether the entity is in water.", returnType = "boolean")
    default boolean isInWater() { return false; }

    @LuaDoc(description = "Checks whether the entity is in lava.", returnType = "boolean")
    default boolean isInLava() { return false; }

    @LuaDoc(description = "Checks whether the entity is standing on the ground.", returnType = "boolean")
    default boolean isOnGround() { return false; }

    @LuaDoc(description = "Returns the entity's custom display name, or an empty string.", returnType = "string")
    default String getCustomName() { return ""; }

    @LuaDoc(description = "Sets the entity's custom display name (empty clears it).", params = {"name: string"})
    default void setCustomName(String name) {}

    @LuaDoc(description = "Checks whether the custom name is always rendered.", returnType = "boolean")
    default boolean isCustomNameVisible() { return false; }

    @LuaDoc(description = "Sets whether the custom name is always rendered.", params = {"visible: boolean"})
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
