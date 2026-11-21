package com.luatweaker.api.entity;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import com.luatweaker.api.annotation.LuaDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@LuaDoc(description = "Represents a generic Minecraft entity wrapper for manipulating health, effects, position, damage, and sounds.")
public interface IEntity {
    @LuaDoc(description = "Returns the entity type ID (e.g. 'minecraft:zombie').", returnType = LuaDocFragments.Types.STRING)
    String getType();

    @LuaDoc(description = "Returns the display name of the entity.", returnType = LuaDocFragments.Types.STRING)
    String getName();

    @LuaDoc(description = "Returns the entity's UUID string.", returnType = LuaDocFragments.Types.STRING)
    default String getUuid() { return ""; }

    @LuaDoc(description = "Sends a chat message if this entity is a player; a no-op otherwise.", params = {"message: string"})
    default void sendMessage(String message) {}

    @LuaDoc(description = "Returns current health of the entity.", returnType = LuaDocFragments.Types.NUMBER)
    float getHealth();

    @LuaDoc(description = "Sets health of the entity.", params = {"health: number"})
    void setHealth(float health);

    @LuaDoc(description = "Returns maximum health of the entity.", returnType = LuaDocFragments.Types.NUMBER)
    float getMaxHealth();

    @LuaDoc(description = "Deals damage with specific Minecraft 1.21 DamageType and optional attacker.", params = {LuaDocFragments.Content.AMOUNT_NUMBER, "[damageTypeId: string]", "[attacker: IEntity]"})
    default void applyDamage(float amount, @LuaDefault("minecraft:generic") String damageTypeId, @Nullable IEntity attacker) {
        damage(amount);
    }

    @LuaDoc(description = "Adds impulse force vector to entity velocity.", params = {"vx: number", "vy: number", "vz: number"})
    default void applyImpulse(double vx, double vy, double vz) {
        addVelocity(vx, vy, vz);
    }

    @LuaDoc(description = "Sets armor or hand equipment (mainhand, offhand, head, chest, legs, feet).", params = {"slot: string", "itemId: string", "[count: integer]"})
    default void setEquipment(String slot, String itemId, @LuaDefault("1") int count) {}

    @LuaDoc(description = "Gets equipment item ID in slot (mainhand, offhand, head, chest, legs, feet).", params = {"slot: string"}, returnType = LuaDocFragments.Types.STRING)
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
    @LuaDoc(description = "Returns the look direction X component (same as Minecraft's look angle).", returnType = LuaDocFragments.Types.NUMBER)
    default double getLookVectorX() {
        return -Math.sin(Math.toRadians(getYaw())) * Math.cos(Math.toRadians(getPitch()));
    }

    @LuaDoc(description = "Returns the look direction Y component: negative when looking down, positive when looking up.", returnType = LuaDocFragments.Types.NUMBER)
    default double getLookVectorY() {
        return -Math.sin(Math.toRadians(getPitch()));
    }

    @LuaDoc(description = "Returns the look direction Z component (same as Minecraft's look angle).", returnType = LuaDocFragments.Types.NUMBER)
    default double getLookVectorZ() {
        return Math.cos(Math.toRadians(getYaw())) * Math.cos(Math.toRadians(getPitch()));
    }

    @LuaDoc(description = "Raycasts from eye position forward along look vector.", params = {"[maxDistance: number]", "[ignoreLiquids: boolean]"}, returnType = LuaDocFragments.Types.TABLE)
    default com.luatweaker.api.spatial.RaycastResult raycast(@LuaDefault("64.0") double maxDistance, @LuaDefault("true") boolean ignoreLiquids) {
        return com.luatweaker.api.spatial.RaycastResult.miss(getX(), getY(), getZ());
    }

    @LuaDoc(description = "Deals generic damage to the entity.", params = {LuaDocFragments.Content.AMOUNT_NUMBER})
    default void damage(float amount) {}

    @LuaDoc(
        description = "Triggers a GeckoLib skeletal animation on this entity.",
        params = {LuaDocFragments.Content.ANIM_NAME, "[controllerName: string]"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    default boolean triggerAnimation(String animName, @LuaDefault("controller") String controllerName) {
        return com.luatweaker.api.pal.Platform.getInteraction().triggerGeoAnimation(this, controllerName, animName, 1.0, false);
    }

    @LuaDoc(description = "Deals generic damage to the entity.", params = {LuaDocFragments.Content.AMOUNT_NUMBER})
    default void hurt(float amount) { damage(amount); }

    @LuaDoc(description = "Heals the entity by specified amount.", params = {LuaDocFragments.Content.AMOUNT_NUMBER})
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

    @LuaDoc(description = "Checks if the entity has a active status effect.", params = {"effectId: string"}, returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean hasEffect(String effectId) { return false; }

    @LuaDoc(description = "Checks if the entity is alive.", returnType = LuaDocFragments.Types.BOOLEAN)
    boolean isAlive();

    @LuaDoc(description = "Sets the entity on fire for specified seconds.", params = {"seconds: integer"})
    default void setIgniteSeconds(int seconds) {}

    @LuaDoc(description = "Extinguishes fire on the entity.")
    default void extinguish() {}

    @LuaDoc(description = "Plays a sound at the entity's position.", params = {"soundId: string", "[volume: number]", "[pitch: number]"})
    default void playSound(String soundId, @LuaDefault(LuaDocFragments.Defaults.ONE) float volume, @LuaDefault(LuaDocFragments.Defaults.ONE) float pitch) {}

    @LuaDoc(
        description = "Plays a sound at the entity's position in a specific Minecraft sound category "
                    + "(MASTER, MUSIC, RECORDS, WEATHER, BLOCKS, HOSTILE, NEUTRAL, PLAYERS, AMBIENT, VOICE), "
                    + "so it follows that category's volume slider. Unregistered ids are reported and skipped.",
        params = {"soundId: string", "volume: number", "pitch: number", "source: string"}
    )
    default void playSoundFrom(String soundId, @LuaDefault(LuaDocFragments.Defaults.ONE) float volume,
                               @LuaDefault(LuaDocFragments.Defaults.ONE) float pitch, @LuaDefault("NEUTRAL") String source) {
        playSound(soundId, volume, pitch);
    }

    @LuaDoc(description = "Spawns particle effect at entity location.", params = {"particleId: string", "count: integer", "speed: number"})
    default void spawnParticle(String particleId, @LuaDefault("1") int count, @LuaDefault(LuaDocFragments.Defaults.ZERO) double speed) {}

    @LuaDoc(description = "Spawns an entity relative to this entity's position.", params = {"entityId: string", "offsetX: number", "offsetY: number", "offsetZ: number"}, returnType = LuaDocFragments.Types.TABLE)
    default IEntity spawnEntity(String entityId, @LuaDefault(LuaDocFragments.Defaults.ZERO) double offsetX, @LuaDefault(LuaDocFragments.Defaults.ZERO) double offsetY, @LuaDefault(LuaDocFragments.Defaults.ZERO) double offsetZ) { return null; }

    @LuaDoc(description = "Fires a projectile from this entity's eye position toward where it is looking.", params = {"projectileType: string (e.g. 'luatweaker:ruby_orb', 'minecraft:small_fireball')", LuaDocFragments.Common.SPEED_OPTIONAL, "[inaccuracy: number]"})
    default void shootProjectile(String projectileType, @LuaDefault("1.5") double speed, @LuaDefault(LuaDocFragments.Defaults.ZERO) double inaccuracy) {}

    @LuaDoc(description = "Fires a projectile from this entity toward a target entity. Returns the spawned projectile so scripts can steer it.", params = {"projectileType: string", "target: table (entity)", LuaDocFragments.Common.SPEED_OPTIONAL}, returnType = "table | nil")
    default IEntity shootProjectileAt(String projectileType, IEntity target, @LuaDefault("1.5") double speed) { return null; }

    @LuaDoc(description = "Plays a named animation on the entity (stored for client-side rendering).", params = {LuaDocFragments.Content.ANIM_NAME, LuaDocFragments.Common.SPEED_OPTIONAL, "[transition: number]"})
    default void playAnimation(String animName, @LuaDefault(LuaDocFragments.Defaults.ONE) double speed, @LuaDefault("0.2") double transition) {}

    @LuaDoc(description = "Plays a custom item animation on this entity (e.g. 'recoil', 'thrust', 'cast', 'spin', 'inspect').",
            params = {LuaDocFragments.Content.ANIM_NAME, "[durationTicks: integer]", LuaDocFragments.Common.SPEED_OPTIONAL})
    default void playItemAnimation(String animName, @LuaDefault("20") int durationTicks, @LuaDefault(LuaDocFragments.Defaults.ONE) double speed) {
        playAnimation(animName, speed, durationTicks);
    }

    /**
     * Legacy string view over the entity's persistent data. Shares the same key namespace as
     * {@link #setCustomData}, so the two APIs can read each other's values.
     */
    @LuaDoc(description = "Stores a string in the entity's persistent data (same key space as SetCustomData; null removes it).", params = {LuaDocFragments.Common.KEY_STRING, "value: string"})
    default void setAttribute(String key, String value) {}

    @LuaDoc(description = "Reads a persistent value as a string (same key space as GetCustomData).", params = {LuaDocFragments.Common.KEY_STRING}, returnType = "string | nil")
    default String getAttribute(String key) { return null; }

    @LuaDoc(description = "Reads a typed persistent value (string, number or boolean).", params = {LuaDocFragments.Common.KEY_STRING}, returnType = "any")
    default @Nullable Object getCustomData(@NotNull String key) { return null; }

    /**
     * Stores a typed persistent value.
     *
     * @throws IllegalArgumentException for tables, functions and other values that cannot be
     *                                  represented as a single persistent primitive
     */
    @LuaDoc(description = "Stores a typed persistent value (string, number or boolean; nil removes it). Other Lua types are rejected.", params = {LuaDocFragments.Common.KEY_STRING, "value: any"})
    default void setCustomData(@NotNull String key, @Nullable Object value) {}

    @LuaDoc(description = "Sets the maximum health attribute of the entity.", params = {"maxHealth: number"})
    default void setMaxHealth(float maxHealth) {}

    @LuaDoc(
        description = "Returns a Minecraft attribute value after modifiers (e.g. 'minecraft:generic.movement_speed', 'minecraft:generic.attack_damage'), or nil when the entity lacks it.",
        params = {LuaDocFragments.Content.ATTRIBUTE_ID},
        returnType = "number | nil"
    )
    default @Nullable Double getAttributeValue(String attributeId) { return null; }

    @LuaDoc(
        description = "Returns a Minecraft attribute base value (before modifiers), or nil when the entity lacks it.",
        params = {LuaDocFragments.Content.ATTRIBUTE_ID},
        returnType = "number | nil"
    )
    default @Nullable Double getAttributeBaseValue(String attributeId) { return null; }

    @LuaDoc(
        description = "Sets a Minecraft attribute base value. Returns false when the entity has no such attribute.",
        params = {LuaDocFragments.Content.ATTRIBUTE_ID, "value: number"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    default boolean setAttributeBaseValue(String attributeId, double value) { return false; }

    @LuaDoc(
        description = "Adds or replaces a named modifier on a Minecraft attribute. operation: 'ADD_VALUE' (default), 'ADD_MULTIPLIED_BASE' or 'ADD_MULTIPLIED_TOTAL'.",
        params = {LuaDocFragments.Content.ATTRIBUTE_ID, "modifierId: string", LuaDocFragments.Content.AMOUNT_NUMBER, "[operation: string]"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    default boolean addAttributeModifier(String attributeId, String modifierId, double amount,
                                         @LuaDefault("ADD_VALUE") String operation) {
        return false;
    }

    @LuaDoc(
        description = "Removes a named modifier from a Minecraft attribute. Returns whether it existed.",
        params = {LuaDocFragments.Content.ATTRIBUTE_ID, "modifierId: string"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    default boolean removeAttributeModifier(String attributeId, String modifierId) { return false; }

    @LuaDoc(description = "Returns the current velocity (motion) of the entity.", returnType = LuaDocFragments.Types.NUMBER)
    default double getMotionX() { return 0; }
    default double getMotionY() { return 0; }
    default double getMotionZ() { return 0; }

    @LuaDoc(description = "Actual X displacement since the previous tick (X - xOld).",
            returnType = LuaDocFragments.Types.NUMBER)
    default double getDeltaX() { return 0; }

    @LuaDoc(description = "Actual Y displacement since the previous tick (Y - yOld).",
            returnType = LuaDocFragments.Types.NUMBER)
    default double getDeltaY() { return 0; }

    @LuaDoc(description = "Actual Z displacement since the previous tick (Z - zOld).",
            returnType = LuaDocFragments.Types.NUMBER)
    default double getDeltaZ() { return 0; }

    @LuaDoc(description = "Horizontal distance moved this tick (sqrt(deltaX^2 + deltaZ^2)).",
            returnType = LuaDocFragments.Types.NUMBER)
    default double getHorizontalSpeed() { return 0; }

    @LuaDoc(description = "Vertical distance moved this tick (deltaY).",
            returnType = LuaDocFragments.Types.NUMBER)
    default double getVerticalSpeed() { return 0; }

    @LuaDoc(description = "Total distance moved this tick.",
            returnType = LuaDocFragments.Types.NUMBER)
    default double getSpeed() { return 0; }

    @LuaDoc(description = "Distance fallen since the entity last touched the ground.",
            returnType = LuaDocFragments.Types.NUMBER)
    default float getFallDistance() { return 0; }

    @LuaDoc(description = "Whether a player is in flight mode (abilities.flying).",
            returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isFlying() { return false; }

    @LuaDoc(description = "Whether the entity is elytra gliding.",
            returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isFallFlying() { return false; }

    @LuaDoc(description = "Eye height above the entity's feet.",
            returnType = LuaDocFragments.Types.NUMBER)
    default float getEyeHeight() { return 0; }

    @LuaDoc(description = "Game tick when the entity was last hurt by a mob (0 when never).",
            returnType = LuaDocFragments.Types.INTEGER)
    default int getLastHurtByMobTimestamp() { return 0; }

    @LuaDoc(description = "Game tick when the entity last hurt a mob (0 when never).",
            returnType = LuaDocFragments.Types.INTEGER)
    default int getLastHurtMobTimestamp() { return 0; }

    @LuaDoc(description = "Message id of the last damage source (e.g. 'player_attack'), or an empty string.",
            returnType = LuaDocFragments.Types.STRING)
    default String getLastDamageType() { return ""; }

    @LuaDoc(description = "Whether the entity is currently using an item.",
            returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isUsingItem() { return false; }

    @LuaDoc(description = "Whether a living entity is swinging its arm this tick.",
            returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isSwinging() { return false; }

    @LuaDoc(description = "Swing animation timer of a living entity.",
            returnType = LuaDocFragments.Types.INTEGER)
    default int getSwingTime() { return 0; }

    @LuaDoc(description = "Entity interaction range for a player (reach), or 0 for non-players.",
            returnType = LuaDocFragments.Types.NUMBER)
    default double getEntityInteractionRange() { return 0; }

    @LuaDoc(description = "Block interaction range for a player (reach), or 0 for non-players.",
            returnType = LuaDocFragments.Types.NUMBER)
    default double getBlockInteractionRange() { return 0; }

    @LuaDoc(description = "Whether the entity is swimming.",
            returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isSwimming() { return false; }

    @LuaDoc(description = "Yaw the entity had at the end of the previous tick.",
            returnType = LuaDocFragments.Types.NUMBER)
    default float getPreviousYaw() { return 0; }

    @LuaDoc(description = "Pitch the entity had at the end of the previous tick.",
            returnType = LuaDocFragments.Types.NUMBER)
    default float getPreviousPitch() { return 0; }

    @LuaDoc(description = "Yaw change since the previous tick, wrapped to -180..180 degrees.",
            returnType = LuaDocFragments.Types.NUMBER)
    default float getYawDelta() { return 0; }

    @LuaDoc(description = "Pitch change since the previous tick.",
            returnType = LuaDocFragments.Types.NUMBER)
    default float getPitchDelta() { return 0; }

    @LuaDoc(description = "Head yaw (separate from body yaw), or the entity yaw for non-living entities.",
            returnType = LuaDocFragments.Types.NUMBER)
    default float getHeadYaw() { return 0; }

    @LuaDoc(description = "Remaining use ticks for the active item (0 when not using one).",
            returnType = LuaDocFragments.Types.INTEGER)
    default int getUseItemRemainingTicks() { return 0; }

    @LuaDoc(description = "Armor value of a living entity.",
            returnType = LuaDocFragments.Types.INTEGER)
    default int getArmorValue() { return 0; }

    @LuaDoc(description = "Attack cooldown scale (0..1) for a player at the given partial tick.",
            params = {"[partialTick: number]"}, returnType = LuaDocFragments.Types.NUMBER)
    default float getAttackStrengthScale(@LuaDefault("1.0") float partialTick) { return 1.0F; }

    @LuaDoc(
        description = "One-call movement/state snapshot for server-side checks. Keys: X, Y, Z, "
                    + "DeltaX, DeltaY, DeltaZ, HorizontalSpeed, VerticalSpeed, Speed, Yaw, Pitch, "
                    + "PreviousYaw, PreviousPitch, YawDelta, PitchDelta, HeadYaw, "
                    + "OnGround, InWater, InLava, FallFlying, Flying, FallDistance, EyeHeight, "
                    + "Sprinting, Sneaking, UsingItem, UseItemTicks, Armor, AttackStrength, "
                    + "Swinging, SwingTime, Swimming, Pose, EntityReach, BlockReach, "
                    + "Invulnerable, NoAi, Invisible, NoGravity, "
                    + "LastHurtByMobAt, LastHurtMobAt, LastDamageType; players also get GameMode and Ping.",
        returnType = LuaDocFragments.Types.TABLE
    )
    default java.util.Map<String, Object> getMovementData() { return java.util.Map.of(); }

    @LuaDoc(description = "Teleports the entity to coordinates.", params = {"x: number", "y: number", "z: number"})
    default void teleport(double x, double y, double z) {}

    @LuaDoc(description = "Sets velocity / motion vector of the entity.", params = {"vx: number", "vy: number", "vz: number"})
    default void setMotion(double vx, double vy, double vz) {}

    @LuaDoc(description = "Starts pathfinding toward a position (mobs only). Returns whether navigation started.", params = {"x: number", "y: number", "z: number", "speed: number"}, returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean moveTo(double x, double y, double z, double speed) { return false; }

    @LuaDoc(description = "Adds impulse velocity to entity.", params = {"vx: number", "vy: number", "vz: number"})
    default void addVelocity(double vx, double vy, double vz) {}

    @LuaDoc(description = "Returns the entity's X coordinate.", returnType = LuaDocFragments.Types.NUMBER)
    default double getX() { return 0; }

    @LuaDoc(description = "Returns the entity's Y coordinate.", returnType = LuaDocFragments.Types.NUMBER)
    default double getY() { return 0; }

    @LuaDoc(description = "Returns the entity's Z coordinate.", returnType = LuaDocFragments.Types.NUMBER)
    default double getZ() { return 0; }

    @LuaDoc(description = "Returns the entity's yaw (horizontal rotation, degrees).", returnType = LuaDocFragments.Types.NUMBER)
    default float getYaw() { return 0; }

    @LuaDoc(description = "Returns the entity's pitch (vertical rotation, degrees; positive looks down).", returnType = LuaDocFragments.Types.NUMBER)
    default float getPitch() { return 0; }

    @LuaDoc(description = "Sets the entity's yaw (horizontal rotation, degrees).", params = {"yaw: number"})
    default void setYaw(float yaw) {}

    @LuaDoc(description = "Sets the entity's pitch (vertical rotation, degrees; positive looks down).", params = {"pitch: number"})
    default void setPitch(float pitch) {}

    @LuaDoc(description = "Checks whether the entity is sneaking.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isSneaking() { return false; }

    @LuaDoc(description = "Sets whether the entity is sneaking.", params = {LuaDocFragments.Common.VALUE_BOOLEAN})
    default void setSneaking(boolean value) {}

    @LuaDoc(description = "Checks whether the entity is sprinting.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isSprinting() { return false; }

    @LuaDoc(description = "Sets whether the entity is sprinting.", params = {LuaDocFragments.Common.VALUE_BOOLEAN})
    default void setSprinting(boolean value) {}

    @LuaDoc(description = "Checks whether the entity has the glowing outline.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isGlowing() { return false; }

    @LuaDoc(description = "Sets the glowing outline on or off.", params = {LuaDocFragments.Common.VALUE_BOOLEAN})
    default void setGlowing(boolean value) {}

    @LuaDoc(description = "Checks whether the entity ignores damage.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isInvulnerable() { return false; }

    @LuaDoc(description = "Sets whether the entity ignores damage.", params = {LuaDocFragments.Common.VALUE_BOOLEAN})
    default void setInvulnerable(boolean value) {}

    @LuaDoc(description = "Checks whether the entity is in water.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isInWater() { return false; }

    @LuaDoc(description = "Checks whether the entity is in lava.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isInLava() { return false; }

    @LuaDoc(description = "Checks whether the entity is standing on the ground.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isOnGround() { return false; }

    @LuaDoc(description = "Returns the entity's custom display name, or an empty string.", returnType = LuaDocFragments.Types.STRING)
    default String getCustomName() { return ""; }

    @LuaDoc(description = "Sets the entity's custom display name (empty clears it).", params = {"name: string"})
    default void setCustomName(String name) {}

    @LuaDoc(description = "Checks whether the custom name is always rendered.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isCustomNameVisible() { return false; }

    @LuaDoc(description = "Sets whether the custom name is always rendered.", params = {"visible: boolean"})
    default void setCustomNameVisible(boolean visible) {}

    @LuaDoc(description = "Adds a scoreboard / entity tag.", params = {"tag: string"})
    default void addTag(String tag) {}

    @LuaDoc(description = "Removes a scoreboard / entity tag.", params = {"tag: string"})
    default void removeTag(String tag) {}

    @LuaDoc(description = "Checks if entity has tag.", params = {"tag: string"}, returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean hasTag(String tag) { return false; }

    @LuaDoc(description = "Swings the entity's main arm.")
    default void swingArm() {}

    @LuaDoc(
        description = "Sets the entity pose (STANDING, CROUCHING, SWIMMING, SLEEPING, SITTING, ...). Unknown values throw.",
        params = {"pose: string"}
    )
    default void setPose(String pose) {}

    @LuaDoc(description = "Returns the entity pose name (e.g. 'STANDING').", returnType = LuaDocFragments.Types.STRING)
    default String getPose() { return "STANDING"; }

    @LuaDoc(
        description = "Whether this entity has an unobstructed line of sight to the target (mob senses).",
        params = {"target: table (entity)"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    default boolean canSee(IEntity target) { return false; }

    @LuaDoc(description = "Stops the current pathfinding move (mobs only).")
    default void stopMoving() {}

    @LuaDoc(description = "Rotates the entity to face a position (mobs steer the look controller).", params = {"x: number", "y: number", "z: number"})
    default void lookAt(double x, double y, double z) {}

    @LuaDoc(description = "Rotates the entity to face another entity (aims at eye height for mobs).", params = {"target: table (entity)"})
    default void lookAtEntity(@NotNull IEntity target) {}

    @LuaDoc(description = "Makes the entity jump (mobs only; no-op for other entities). Check isOnGround() first for grounded hops.")
    default void jump() {}

    @LuaDoc(
        description = "Server game ticks of the entity's level (monotonic tick clock). "
                    + "Use this for AI cooldowns and timeouts, NOT the wall-clock Task.GetTimeClock "
                    + "(wall time jumps on NTP corrections and pauses).",
        returnType = LuaDocFragments.Types.NUMBER
    )
    default long getGameTime() { return 0; }

    @LuaDoc(description = "Whether the entity is currently pathfinding toward a target.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isNavigating() { return false; }

    @LuaDoc(description = "Sets the combat target entity for mob AI.", params = {"targetEntity: IEntity"})
    default void setTarget(IEntity target) {}

    @LuaDoc(description = "Returns current combat target entity for mob AI.", returnType = "IEntity")
    default IEntity getTarget() { return null; }

    default double distanceTo(IEntity other) {
        if (other == null) { return Double.MAX_VALUE; }
        double dx = getX() - other.getX();
        double dy = getY() - other.getY();
        double dz = getZ() - other.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    default boolean isPlayer() { return false; }
    default boolean isLiving() { return false; }

    @LuaDoc(description = "Removes / kills the entity.")
    void remove();

    @LuaDoc(
        description = "Triggers a GeckoLib skeletal animation on this entity.",
        params = {LuaDocFragments.Content.ANIM_NAME, "[controllerName: string]"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    default boolean playAnimation(@NotNull String animName, @LuaDefault("controller") String controllerName) {
        return com.luatweaker.api.pal.Platform.getInteraction().triggerGeoAnimation(this, controllerName, animName, 1.0, false);
    }

    @LuaDoc(
        description = "Stops a triggered GeckoLib animation on this entity.",
        params = {LuaDocFragments.Content.ANIM_NAME, "[controllerName: string]"},
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    default boolean stopAnimation(@NotNull String animName, @LuaDefault("controller") String controllerName) {
        return com.luatweaker.api.pal.Platform.getInteraction().stopGeoAnimation(this, controllerName, animName);
    }

    @LuaDoc(description = "Returns whether the entity has gravity disabled.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isNoGravity() { return false; }

    @LuaDoc(description = "Sets whether the entity has gravity disabled.", params = {"noGravity: boolean"})
    default void setNoGravity(boolean noGravity) {}

    @LuaDoc(
        description = "Whether a mob's AI is disabled (true = it keeps its goals but does not tick them).",
        returnType = LuaDocFragments.Types.BOOLEAN
    )
    default boolean isNoAi() { return false; }

    @LuaDoc(
        description = "Disables or re-enables a mob's AI. Combined with SetMotion(0,0,0) this freezes the mob "
                    + "in place; non-mob entities ignore it.",
        params = {"noAi: boolean"}
    )
    default void setNoAi(boolean noAi) {}

    @LuaDoc(description = "Whether the entity is rendered invisible.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isInvisible() { return false; }

    @LuaDoc(description = "Sets the entity invisible (it still exists, collides and can be damaged).",
            params = {"invisible: boolean"})
    default void setInvisible(boolean invisible) {}

    @LuaDoc(description = "Returns whether the entity is silent (produces no sound).", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isSilent() { return false; }

    @LuaDoc(description = "Sets whether the entity is silent.", params = {"silent: boolean"})
    default void setSilent(boolean silent) {}

    @LuaDoc(description = "Returns the entity's remaining air supply in ticks.", returnType = LuaDocFragments.Types.INTEGER)
    default int getAirSupply() { return 300; }

    @LuaDoc(description = "Sets the entity's air supply in ticks.", params = {"air: integer"})
    default void setAirSupply(int air) {}

    @LuaDoc(description = "Returns the maximum air supply of the entity in ticks.", returnType = LuaDocFragments.Types.INTEGER)
    default int getMaxAirSupply() { return 300; }

    @LuaDoc(description = "Returns remaining ticks before the fire is extinguished.", returnType = LuaDocFragments.Types.INTEGER)
    default int getRemainingFireTicks() { return 0; }

    @LuaDoc(description = "Returns whether the entity is currently burning.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isOnFire() { return false; }

    @LuaDoc(description = "Sets distance fallen since entity last touched ground.", params = {"distance: number"})
    default void setFallDistance(float distance) {}

    @LuaDoc(description = "Returns bounding box width of entity.", returnType = LuaDocFragments.Types.NUMBER)
    default float getWidth() { return 0.6f; }

    @LuaDoc(description = "Returns bounding box height of entity.", returnType = LuaDocFragments.Types.NUMBER)
    default float getHeight() { return 1.8f; }

    @LuaDoc(description = "Mounts another entity as vehicle.", params = {"vehicle: table (entity)"}, returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean mount(@NotNull IEntity vehicle) { return false; }

    @LuaDoc(description = "Dismounts current vehicle if riding one.")
    default void dismount() {}

    @LuaDoc(description = "Returns current vehicle entity, or nil.", returnType = "table | nil")
    default @Nullable IEntity getVehicle() { return null; }

    @LuaDoc(description = "Returns list of passenger entities.", returnType = LuaDocFragments.Types.TABLE)
    default @NotNull java.util.List<IEntity> getPassengers() { return java.util.List.of(); }

    @LuaDoc(description = "Checks whether entity is currently riding a vehicle.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean isPassenger() { return false; }

    @LuaDoc(description = "Checks whether entity has passengers.", returnType = LuaDocFragments.Types.BOOLEAN)
    default boolean hasPassengers() { return false; }

    @LuaDoc(description = "Returns absorption shield health.", returnType = LuaDocFragments.Types.NUMBER)
    default float getAbsorption() { return 0.0f; }

    @LuaDoc(description = "Sets absorption shield health.", params = {"amount: number"})
    default void setAbsorption(float amount) {}

    @LuaDoc(description = "Returns dimension ID string the entity is in.", returnType = LuaDocFragments.Types.STRING)
    default @NotNull String getDimension() { return "minecraft:overworld"; }

    @LuaDoc(description = "Strikes lightning at the entity's position.")
    default void strikeLightning() {}

    @LuaDoc(description = "Creates an explosion centered on the entity.", params = {"[power: number]", "[causesFire: boolean]", "[breakBlocks: boolean]"})
    default void createExplosion(@LuaDefault("3.0") float power, @LuaDefault("false") boolean causesFire, @LuaDefault("true") boolean breakBlocks) {}

    @LuaDoc(
        description = "Full snapshot of this entity as NBT-JSON: type id, position, rotation, health, "
                    + "inventory/equipment (including item NBT), active effects, attributes, custom name, "
                    + "owner/trade data. Capture it before the entity dies, then restore it with "
                    + "Entities:SpawnFromNbt or Entity:SetNbt.",
        returnType = LuaDocFragments.Types.STRING
    )
    default String getNbt() { return "{}"; }

    @LuaDoc(
        description = "Restores this entity's state from a GetNbt() snapshot. Returns false when the JSON "
                    + "is malformed, the entity is gone, or it is a player (players are restored by the server).",
        params = {"nbtJson: string"}, returnType = LuaDocFragments.Types.BOOLEAN
    )
    default boolean setNbt(@NotNull String nbtJson) { return false; }

    Object getRawEntity();
}
