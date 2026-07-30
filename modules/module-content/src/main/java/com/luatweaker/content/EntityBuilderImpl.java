package com.luatweaker.content;

import com.luatweaker.api.content.IEntityBuilder;

public class EntityBuilderImpl implements IEntityBuilder {
    private final String id;
    private String category = "MONSTER";
    private float width = 0.6f;
    private float height = 1.8f;
    private double maxHealth = 20.0;
    private double movementSpeed = 0.25;
    private double attackDamage = 3.0;
    private double followRange = 32.0;
    private double armor = 0.0;
    private double knockbackResistance = 0.0;
    private boolean hasSpawnEgg = false;
    private int primaryColor = 0x00FF00;
    private int secondaryColor = 0x0000FF;
    private String model;
    private String texture;
    private String bbmodel;
    private String ambientSound;
    private String hurtSound;
    private String deathSound;
    private String dropItemId;
    private int minDropCount = 1;
    private int maxDropCount = 1;
    private int experience = 5;
    private String creativeTab;

    public EntityBuilderImpl(String id) {
        this.id = id;
    }

    @Override
    public IEntityBuilder category(String category) {
        if (category != null && !category.isBlank()) {
            this.category = category.toUpperCase();
        }
        return this;
    }

    @Override
    public IEntityBuilder dimensions(float width, float height) {
        this.width = Math.max(0.1f, width);
        this.height = Math.max(0.1f, height);
        return this;
    }

    @Override
    public IEntityBuilder maxHealth(double health) {
        this.maxHealth = Math.max(1.0, health);
        return this;
    }

    @Override
    public IEntityBuilder movementSpeed(double speed) {
        this.movementSpeed = Math.max(0.0, speed);
        return this;
    }

    @Override
    public IEntityBuilder attackDamage(double damage) {
        this.attackDamage = Math.max(0.0, damage);
        return this;
    }

    @Override
    public IEntityBuilder followRange(double range) {
        this.followRange = Math.max(1.0, range);
        return this;
    }

    @Override
    public IEntityBuilder armor(double armor) {
        this.armor = Math.max(0.0, armor);
        return this;
    }

    @Override
    public IEntityBuilder knockbackResistance(double resistance) {
        this.knockbackResistance = Math.max(0.0, Math.min(1.0, resistance));
        return this;
    }

    @Override
    public IEntityBuilder spawnEgg(int primaryColor, int secondaryColor) {
        this.hasSpawnEgg = true;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        return this;
    }

    @Override
    public IEntityBuilder model(String modelPath) {
        this.model = modelPath;
        return this;
    }

    @Override
    public IEntityBuilder texture(String texturePath) {
        this.texture = texturePath;
        return this;
    }

    @Override
    public IEntityBuilder bbmodel(String bbmodelPath) {
        this.bbmodel = bbmodelPath;
        return this;
    }

    @Override
    public IEntityBuilder ambientSound(String soundId) {
        this.ambientSound = soundId;
        return this;
    }

    @Override
    public IEntityBuilder hurtSound(String soundId) {
        this.hurtSound = soundId;
        return this;
    }

    @Override
    public IEntityBuilder deathSound(String soundId) {
        this.deathSound = soundId;
        return this;
    }

    @Override
    public IEntityBuilder drop(String itemId, int minCount, int maxCount) {
        this.dropItemId = itemId;
        this.minDropCount = Math.max(1, minCount);
        this.maxDropCount = Math.max(this.minDropCount, maxCount);
        return this;
    }

    @Override
    public IEntityBuilder experience(int exp) {
        this.experience = Math.max(0, exp);
        return this;
    }

    @Override
    public IEntityBuilder creativeTab(String tabId) {
        this.creativeTab = tabId;
        return this;
    }

    private String parentMob;

    @Override
    public IEntityBuilder parent(String parentMob) {
        this.parentMob = parentMob;
        return this;
    }

    @Override
    public IEntityBuilder parentMob(String parentMob) {
        this.parentMob = parentMob;
        return this;
    }

    @Override
    public String getParentMob() {
        if (parentMob != null && !parentMob.isBlank()) return parentMob;
        if (model != null && !model.isBlank()) return model;
        return "zombie";
    }

    private String spawnEggTexture;
    private boolean hasBossBar = false;
    private String bossBarTitle;
    private String bossBarColor = "RED";
    private String bossBarOverlay = "PROGRESS";

    @Override
    public IEntityBuilder spawnEggTexture(String texturePath) {
        this.spawnEggTexture = texturePath;
        this.hasSpawnEgg = true;
        return this;
    }

    @Override
    public IEntityBuilder bossBar(String title, String color, String overlay) {
        this.hasBossBar = true;
        this.bossBarTitle = title;
        if (color != null && !color.isBlank()) this.bossBarColor = color.toUpperCase();
        if (overlay != null && !overlay.isBlank()) this.bossBarOverlay = overlay.toUpperCase();
        return this;
    }

    @Override public String getId() { return id; }
    @Override public String getCategory() { return category; }
    @Override public float getWidth() { return width; }
    @Override public float getHeight() { return height; }
    @Override public double getMaxHealth() { return maxHealth; }
    @Override public double getMovementSpeed() { return movementSpeed; }
    @Override public double getAttackDamage() { return attackDamage; }
    @Override public double getFollowRange() { return followRange; }
    @Override public double getArmor() { return armor; }
    @Override public double getKnockbackResistance() { return knockbackResistance; }
    @Override public boolean hasSpawnEgg() { return hasSpawnEgg; }
    @Override public int getPrimaryColor() { return primaryColor; }
    @Override public int getSecondaryColor() { return secondaryColor; }
    @Override public String getSpawnEggTexture() { return spawnEggTexture; }
    @Override public boolean hasBossBar() { return hasBossBar; }
    @Override public String getBossBarTitle() { return bossBarTitle; }
    @Override public String getBossBarColor() { return bossBarColor; }
    @Override public String getBossBarOverlay() { return bossBarOverlay; }
    @Override public String getModel() { return model; }
    @Override public String getTexture() { return texture; }
    @Override public String getBbmodel() { return bbmodel; }
    @Override public String getAmbientSound() { return ambientSound; }
    @Override public String getHurtSound() { return hurtSound; }
    @Override public String getDeathSound() { return deathSound; }
    @Override public String getDropItemId() { return dropItemId; }
    @Override public int getMinDropCount() { return minDropCount; }
    @Override public int getMaxDropCount() { return maxDropCount; }
    @Override public int getExperience() { return experience; }
    @Override public String getCreativeTab() { return creativeTab; }
}
