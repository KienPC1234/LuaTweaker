package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.Nullable;

@LuaDoc(description = "Builder interface for defining custom Entity types, attributes, models, spawn eggs, sounds, drops and lifecycle hooks.")
public interface IEntityBuilder {
    @LuaDoc(description = "Sets entity classification category ('CREATURE', 'MONSTER', 'AMBIENT', 'WATER_CREATURE', 'MISC').", params = {"category: string"}, returnType = "IEntityBuilder")
    IEntityBuilder category(String category);

    @LuaDoc(description = "Sets the hitbox bounding box size (width and height in blocks).", params = {"width: number", "height: number"}, returnType = "IEntityBuilder")
    IEntityBuilder dimensions(float width, float height);

    @LuaDoc(description = "Sets maximum health attribute value.", params = {"health: number"}, returnType = "IEntityBuilder")
    IEntityBuilder maxHealth(double health);

    @LuaDoc(description = "Sets movement speed attribute value (e.g. 0.25).", params = {"speed: number"}, returnType = "IEntityBuilder")
    IEntityBuilder movementSpeed(double speed);

    @LuaDoc(description = "Sets base attack damage attribute value.", params = {"damage: number"}, returnType = "IEntityBuilder")
    IEntityBuilder attackDamage(double damage);

    @LuaDoc(description = "Sets AI target follow range attribute value.", params = {"range: number"}, returnType = "IEntityBuilder")
    IEntityBuilder followRange(double range);

    @LuaDoc(description = "Sets base armor attribute value.", params = {"armor: number"}, returnType = "IEntityBuilder")
    IEntityBuilder armor(double armor);

    @LuaDoc(description = "Sets knockback resistance attribute value (0.0 to 1.0).", params = {"resistance: number"}, returnType = "IEntityBuilder")
    IEntityBuilder knockbackResistance(double resistance);

    @LuaDoc(description = "Configures a Spawn Egg item with primary and secondary color hex codes (e.g. 0x00FF00, 0x0000FF).", params = {"primaryColor: integer", "secondaryColor: integer"}, returnType = "IEntityBuilder")
    IEntityBuilder spawnEgg(int primaryColor, int secondaryColor);

    @LuaDoc(
        description = "UNSUPPORTED: custom geometry files were never loaded by the engine. "
                    + "Throws. Use rig('humanoid' | 'quadruped') + texture(path) instead.",
        params = {"modelPath: string"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder model(String modelPath);

    @LuaDoc(description = "Sets custom texture path (e.g. 'luatweaker:textures/entity/custom_golem.png'). Required for the humanoid/quadruped rigs.", params = {"texturePath: string"}, returnType = "IEntityBuilder")
    IEntityBuilder texture(String texturePath);

    @LuaDoc(
        description = "UNSUPPORTED: Blockbench files were never loaded by the engine. "
                    + "Throws. Use rig('humanoid' | 'quadruped') + texture(path) instead.",
        params = {"bbmodelPath: string"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder bbmodel(String bbmodelPath);

    @LuaDoc(
        description = "Sets the body rig. 'parent' (default) keeps the vanilla parent class and renderer; "
                    + "'humanoid' / 'quadruped' use the generic LuaTweaker mob with a texture-driven renderer, "
                    + "custom sounds and the defaultGoals preset. Unknown values throw.",
        params = {"rig: string ('parent' | 'humanoid' | 'quadruped')"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder rig(String rig);

    @LuaDoc(
        description = "Selects the built-in AI preset applied to the humanoid/quadruped rigs "
                    + "('monster': hostile melee + wander; 'animal': panic + wander; 'none': motionless until goals are added; "
                    + "null/blank: monster for MONSTER, animal for CREATURE, none otherwise). "
                    + "Legacy 'parent' rigs always keep their vanilla AI; this setting is ignored there.",
        params = {"preset: string ('monster' | 'animal' | 'none')"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder defaultGoals(String preset);

    @LuaDoc(description = "Render scale multiplier applied to the entity model (1.0 = true to hitbox size).", params = {"scale: number"}, returnType = "IEntityBuilder")
    IEntityBuilder renderScale(double scale);

    @LuaDoc(description = "Makes the entity immune to fire/lava damage.", params = {"immune: boolean"}, returnType = "IEntityBuilder")
    IEntityBuilder fireImmune(boolean immune);

    @LuaDoc(
        description = "How often the onTick hook fires, in server ticks (default 5 = 4x/second; 1 = every tick). "
                    + "Per-tick Lua callbacks are expensive: keep this as high as the behavior allows.",
        params = {"intervalTicks: integer"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder tickRate(int intervalTicks);

    @LuaDoc(description = "Sets ambient sound event ID (humanoid/quadruped rigs only; legacy parent rigs keep vanilla sounds).", params = {"soundId: string"}, returnType = "IEntityBuilder")
    IEntityBuilder ambientSound(String soundId);

    @LuaDoc(description = "Sets hurt sound event ID (humanoid/quadruped rigs only).", params = {"soundId: string"}, returnType = "IEntityBuilder")
    IEntityBuilder hurtSound(String soundId);

    @LuaDoc(description = "Sets death sound event ID (humanoid/quadruped rigs only).", params = {"soundId: string"}, returnType = "IEntityBuilder")
    IEntityBuilder deathSound(String soundId);

    @LuaDoc(
        description = "Appends an item drop (each :drop call adds one entry; all entries roll independently). "
                    + "Replaces vanilla drops for this entity type.",
        params = {"itemId: string", "minCount: integer", "maxCount: integer"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder drop(String itemId, int minCount, int maxCount);

    @LuaDoc(description = "Configures experience dropped on death (only applied when set explicitly).", params = {"exp: integer"}, returnType = "IEntityBuilder")
    IEntityBuilder experience(int exp);

    @LuaDoc(description = "Sets creative tab for the entity spawn egg.", params = {"tabId: string"}, returnType = "IEntityBuilder")
    IEntityBuilder creativeTab(String tabId);

    @LuaDoc(description = "Sets the GeckoLib skeletal model and animation specs for this entity.", params = {"modelPath: string", "texturePath: string", "animationPath: string", "defaultAnimation: string?"}, returnType = "IEntityBuilder")
    IEntityBuilder geoModel(String modelPath, String texturePath, String animationPath, @org.jetbrains.annotations.Nullable String defaultAnimation);

    default IEntityBuilder geoModel(String modelPath, String texturePath, String animationPath) {
        return geoModel(modelPath, texturePath, animationPath, null);
    }

    @LuaDoc(
        description = "Selects the vanilla mob class to clone behavior, attributes and renderer from "
                    + "('zombie', 'skeleton', 'creeper', 'iron_golem', 'spider', 'pig', 'cow', 'villager', 'slime', 'witch', 'blaze', 'enderman', 'wolf', ...). "
                    + "Only used by the legacy 'parent' rig; the humanoid/quadruped rigs read attribute defaults from it.",
        params = {"parentMob: string"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder parent(String parentMob);

    @LuaDoc(description = "Alias for parent(parentMob).", params = {"parentMob: string"}, returnType = "IEntityBuilder")
    IEntityBuilder parentMob(String parentMob);

    String getParentMob();

    @LuaDoc(description = "Sets a custom item texture path for the entity spawn egg.", params = {"texturePath: string"}, returnType = "IEntityBuilder")
    IEntityBuilder spawnEggTexture(String texturePath);

    @LuaDoc(description = "Configures a Boss Health Bar for this entity.", params = {"title: string", "[color: string]", "[overlay: string]"}, returnType = "IEntityBuilder")
    IEntityBuilder bossBar(String title, String color, String overlay);

    @LuaDoc(
        description = "Server-side per-tick hook for this entity type. "
                    + "fn(entity). Combine with tickRate() to control cost.",
        params = {"fn: function"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder onTick(EntityTickHandler handler);

    @LuaDoc(
        description = "Server-side hurt hook. fn(entity, amount, attackerOrNil) may return a number "
                    + "to replace the damage, or nil to keep it.",
        params = {"fn: function"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder onHurt(EntityHurtHandler handler);

    @LuaDoc(
        description = "Server-side death hook. fn(entity, killerOrNil) returning true cancels the death.",
        params = {"fn: function"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder onDeath(EntityDeathHandler handler);

    @LuaDoc(
        description = "Server-side right-click hook. fn(entity, player) returning true consumes the interaction.",
        params = {"fn: function"},
        returnType = "IEntityBuilder"
    )
    IEntityBuilder onInteract(EntityInteractHandler handler);

    String getId();
    String getCategory();
    String getRig();
    String getDefaultGoals();
    double getRenderScale();
    boolean isFireImmune();
    int getTickRate();
    float getWidth();
    float getHeight();
    double getMaxHealth();
    double getMovementSpeed();
    double getAttackDamage();
    double getFollowRange();
    double getArmor();
    double getKnockbackResistance();
    boolean hasSpawnEgg();
    int getPrimaryColor();
    int getSecondaryColor();
    String getSpawnEggTexture();
    boolean hasBossBar();
    String getBossBarTitle();
    String getBossBarColor();
    String getBossBarOverlay();
    String getModel();
    String getTexture();
    String getBbmodel();
    String getAmbientSound();
    String getHurtSound();
    String getDeathSound();
    java.util.List<EntityDrop> getDrops();
    int getExperience();
    boolean hasExplicitExperience();
    @Nullable EntityTickHandler getOnTickHandler();
    @Nullable EntityHurtHandler getOnHurtHandler();
    @Nullable EntityDeathHandler getOnDeathHandler();
    @Nullable EntityInteractHandler getOnInteractHandler();
    String getCreativeTab();
    @org.jetbrains.annotations.Nullable
    GeoModelSpec getGeoModel();
}
