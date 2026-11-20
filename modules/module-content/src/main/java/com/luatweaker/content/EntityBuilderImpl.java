package com.luatweaker.content;

import com.luatweaker.api.content.EntityDeathHandler;
import com.luatweaker.api.content.EntityDrop;
import com.luatweaker.api.content.EntityHurtHandler;
import com.luatweaker.api.content.EntityInteractHandler;
import com.luatweaker.api.content.EntityTickHandler;
import com.luatweaker.api.content.IEntityBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EntityBuilderImpl implements IEntityBuilder {
    private final String id;
    private String category = "MONSTER";
    private String rig = "parent";
    private String defaultGoals;
    private double renderScale = 1.0;
    private boolean fireImmune = false;
    private int tickRate = 5;
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
    private String texture;
    private String ambientSound;
    private String hurtSound;
    private String deathSound;
    private final List<EntityDrop> drops = new ArrayList<>();
    private int experience = 5;
    private boolean explicitExperience = false;
    private String creativeTab;
    private String parentMob;
    private String spawnEggTexture;
    private boolean hasBossBar = false;
    private String bossBarTitle;
    private String bossBarColor = "RED";
    private String bossBarOverlay = "PROGRESS";
    private EntityTickHandler onTickHandler;
    private EntityHurtHandler onHurtHandler;
    private EntityDeathHandler onDeathHandler;
    private EntityInteractHandler onInteractHandler;

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
    public IEntityBuilder rig(String rig) {
        if (rig == null || rig.isBlank()) {
            throw new IllegalArgumentException("entity '" + id + "' rig must not be blank "
                    + "(use 'parent', 'humanoid' or 'quadruped')");
        }
        String clean = rig.strip().toLowerCase();
        if (!clean.equals("parent") && !clean.equals("humanoid") && !clean.equals("quadruped")) {
            throw new IllegalArgumentException("entity '" + id + "' has unknown rig '" + rig
                    + "' (use 'parent', 'humanoid' or 'quadruped')");
        }
        this.rig = clean;
        return this;
    }

    @Override
    public IEntityBuilder defaultGoals(String preset) {
        if (preset == null || preset.isBlank()) {
            this.defaultGoals = null;
            return this;
        }
        String clean = preset.strip().toLowerCase();
        if (!clean.equals("monster") && !clean.equals("animal") && !clean.equals("none")) {
            throw new IllegalArgumentException("entity '" + id + "' has unknown defaultGoals '" + preset
                    + "' (use 'monster', 'animal' or 'none')");
        }
        this.defaultGoals = clean;
        return this;
    }

    @Override
    public IEntityBuilder renderScale(double scale) {
        if (scale <= 0.0 || scale > 16.0) {
            throw new IllegalArgumentException("entity '" + id + "' renderScale must be in (0, 16], got: " + scale);
        }
        this.renderScale = scale;
        return this;
    }

    @Override
    public IEntityBuilder fireImmune(boolean immune) {
        this.fireImmune = immune;
        return this;
    }

    @Override
    public IEntityBuilder tickRate(int intervalTicks) {
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("entity '" + id + "' tickRate must be >= 1, got: " + intervalTicks);
        }
        this.tickRate = intervalTicks;
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
        throw new IllegalArgumentException("entity '" + id + "': model() was never implemented by the engine "
                + "(custom geometry files are not loaded). Use rig('humanoid' | 'quadruped') + texture(path) instead.");
    }

    @Override
    public IEntityBuilder texture(String texturePath) {
        this.texture = texturePath;
        return this;
    }

    @Override
    public IEntityBuilder bbmodel(String bbmodelPath) {
        throw new IllegalArgumentException("entity '" + id + "': bbmodel() was never implemented by the engine "
                + "(Blockbench files are not loaded). Use rig('humanoid' | 'quadruped') + texture(path) instead.");
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
        int min = Math.max(1, minCount);
        int max = Math.max(min, maxCount);
        this.drops.add(new EntityDrop(itemId, min, max));
        return this;
    }

    @Override
    public IEntityBuilder experience(int exp) {
        this.experience = Math.max(0, exp);
        this.explicitExperience = true;
        return this;
    }

    @Override
    public IEntityBuilder creativeTab(String tabId) {
        this.creativeTab = tabId;
        return this;
    }

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
        return "zombie";
    }

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

    @Override
    public IEntityBuilder onTick(EntityTickHandler handler) {
        this.onTickHandler = handler;
        return this;
    }

    @Override
    public IEntityBuilder onHurt(EntityHurtHandler handler) {
        this.onHurtHandler = handler;
        return this;
    }

    @Override
    public IEntityBuilder onDeath(EntityDeathHandler handler) {
        this.onDeathHandler = handler;
        return this;
    }

    @Override
    public IEntityBuilder onInteract(EntityInteractHandler handler) {
        this.onInteractHandler = handler;
        return this;
    }

    @Override public String getId() { return id; }
    @Override public String getCategory() { return category; }
    @Override public String getRig() { return rig; }
    @Override public String getDefaultGoals() { return defaultGoals; }
    @Override public double getRenderScale() { return renderScale; }
    @Override public boolean isFireImmune() { return fireImmune; }
    @Override public int getTickRate() { return tickRate; }
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
    @Override public String getModel() { return null; }
    @Override public String getTexture() { return texture; }
    @Override public String getBbmodel() { return null; }
    @Override public String getAmbientSound() { return ambientSound; }
    @Override public String getHurtSound() { return hurtSound; }
    @Override public String getDeathSound() { return deathSound; }
    @Override public List<EntityDrop> getDrops() { return List.copyOf(drops); }
    @Override public int getExperience() { return experience; }
    @Override public boolean hasExplicitExperience() { return explicitExperience; }
    @Override public @Nullable EntityTickHandler getOnTickHandler() { return onTickHandler; }
    @Override public @Nullable EntityHurtHandler getOnHurtHandler() { return onHurtHandler; }
    @Override public @Nullable EntityDeathHandler getOnDeathHandler() { return onDeathHandler; }
    private com.luatweaker.api.content.GeoModelSpec geoModel;

    @Override
    public IEntityBuilder geoModel(String modelPath, String texturePath, String animationPath, @org.jetbrains.annotations.Nullable String defaultAnimation) {
        this.geoModel = new com.luatweaker.api.content.GeoModelSpec(modelPath, texturePath, animationPath, defaultAnimation);
        return this;
    }

    @Override public @Nullable EntityInteractHandler getOnInteractHandler() { return onInteractHandler; }
    @Override public String getCreativeTab() { return creativeTab; }
    @Override @Nullable public com.luatweaker.api.content.GeoModelSpec getGeoModel() { return geoModel; }
}
