package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Builder interface for defining custom Entity types, attributes, models, spawn eggs, and sounds.")
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

    @LuaDoc(description = "Sets custom model location (e.g. 'luatweaker:entity/custom_zombie').", params = {"modelPath: string"}, returnType = "IEntityBuilder")
    IEntityBuilder model(String modelPath);

    @LuaDoc(description = "Sets custom texture path.", params = {"texturePath: string"}, returnType = "IEntityBuilder")
    IEntityBuilder texture(String texturePath);

    @LuaDoc(description = "Sets BBModel file path or ID for custom Blockbench entity model.", params = {"bbmodelPath: string"}, returnType = "IEntityBuilder")
    IEntityBuilder bbmodel(String bbmodelPath);

    @LuaDoc(description = "Sets ambient sound event ID.", params = {"soundId: string"}, returnType = "IEntityBuilder")
    IEntityBuilder ambientSound(String soundId);

    @LuaDoc(description = "Sets hurt sound event ID.", params = {"soundId: string"}, returnType = "IEntityBuilder")
    IEntityBuilder hurtSound(String soundId);

    @LuaDoc(description = "Sets death sound event ID.", params = {"soundId: string"}, returnType = "IEntityBuilder")
    IEntityBuilder deathSound(String soundId);

    @LuaDoc(description = "Configures item dropped on death.", params = {"itemId: string", "minCount: integer", "maxCount: integer"}, returnType = "IEntityBuilder")
    IEntityBuilder drop(String itemId, int minCount, int maxCount);

    @LuaDoc(description = "Configures experience dropped on death.", params = {"exp: integer"}, returnType = "IEntityBuilder")
    IEntityBuilder experience(int exp);

    @LuaDoc(description = "Sets creative tab for the entity spawn egg.", params = {"tabId: string"}, returnType = "IEntityBuilder")
    IEntityBuilder creativeTab(String tabId);

    @LuaDoc(description = "Sets explicit vanilla parent mob class to inherit behavior, renderer, and AI from ('zombie', 'skeleton', 'creeper', 'iron_golem', 'spider', 'pig', 'cow', 'villager', 'slime', 'witch', 'blaze', 'enderman', 'wolf').", params = {"parentMob: string"}, returnType = "IEntityBuilder")
    IEntityBuilder parent(String parentMob);

    @LuaDoc(description = "Alias for parent(parentMob).", params = {"parentMob: string"}, returnType = "IEntityBuilder")
    IEntityBuilder parentMob(String parentMob);

    String getParentMob();

    @LuaDoc(description = "Sets a custom item texture path for the entity spawn egg.", params = {"texturePath: string"}, returnType = "IEntityBuilder")
    IEntityBuilder spawnEggTexture(String texturePath);

    @LuaDoc(description = "Configures a Boss Health Bar for this entity.", params = {"title: string", "[color: string]", "[overlay: string]"}, returnType = "IEntityBuilder")
    IEntityBuilder bossBar(String title, String color, String overlay);

    String getId();
    String getCategory();
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
    String getDropItemId();
    int getMinDropCount();
    int getMaxDropCount();
    int getExperience();
    String getCreativeTab();
}
