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
import java.util.Locale;

public class EntityBuilderImpl implements IEntityBuilder {
    private static final String ENTITY_MSG_PREFIX = "entity '";
    private static final double RENDER_SCALE_MIN_EXCLUSIVE = 0.0;
    private static final double RENDER_SCALE_MAX_INCLUSIVE = 16.0;
    private static final int MIN_TICK_RATE = 1;

    /** DSL defaults applied before any Lua callback runs. */
    private static final String DEFAULT_CATEGORY = "MONSTER";
    private static final String DEFAULT_RIG = "parent";
    private static final double DEFAULT_RENDER_SCALE = 1.0;
    private static final int DEFAULT_TICK_RATE = 5;
    private static final float DEFAULT_WIDTH = 0.6f;
    private static final float DEFAULT_HEIGHT = 1.8f;
    private static final double DEFAULT_MAX_HEALTH = 20.0;
    private static final double DEFAULT_MOVEMENT_SPEED = 0.25;
    private static final double DEFAULT_ATTACK_DAMAGE = 3.0;
    private static final double DEFAULT_FOLLOW_RANGE = 32.0;
    private static final int DEFAULT_PRIMARY_COLOR = 0x00FF00;
    private static final int DEFAULT_SECONDARY_COLOR = 0x0000FF;
    private static final int DEFAULT_EXPERIENCE = 5;
    private static final String DEFAULT_BOSS_BAR_COLOR = "RED";
    private static final String DEFAULT_BOSS_BAR_OVERLAY = "PROGRESS";
    private static final String DEFAULT_PARENT_MOB = "zombie";

    /** Lower bounds enforced by the stat setters. */
    private static final float MIN_DIMENSION = 0.1f;
    private static final double MIN_MAX_HEALTH = 1.0;
    private static final double MIN_FOLLOW_RANGE = 1.0;
    private static final double MIN_NON_NEGATIVE = 0.0;
    private static final double MAX_KNOCKBACK_RESISTANCE = 1.0;
    private static final int MIN_DROP_COUNT = 1;
    private static final int MIN_EXPERIENCE = 0;

    private final String id;
    private String categoryName = DEFAULT_CATEGORY;
    private String rigName = DEFAULT_RIG;
    private String goalsPreset;
    private double renderScaleValue = DEFAULT_RENDER_SCALE;
    private boolean fireImmuneEnabled;
    private int tickRateValue = DEFAULT_TICK_RATE;
    private float width = DEFAULT_WIDTH;
    private float height = DEFAULT_HEIGHT;
    private double maxHealthValue = DEFAULT_MAX_HEALTH;
    private double movementSpeedValue = DEFAULT_MOVEMENT_SPEED;
    private double attackDamageValue = DEFAULT_ATTACK_DAMAGE;
    private double followRangeValue = DEFAULT_FOLLOW_RANGE;
    private double armorValue;
    private double knockbackResistanceValue;
    private boolean spawnEggEnabled;
    private int primaryColor = DEFAULT_PRIMARY_COLOR;
    private int secondaryColor = DEFAULT_SECONDARY_COLOR;
    private String texturePath;
    private String ambientSoundId;
    private String hurtSoundId;
    private String deathSoundId;
    private final List<EntityDrop> drops = new ArrayList<>();
    private int experienceValue = DEFAULT_EXPERIENCE;
    private boolean explicitExperienceFlag;
    private String creativeTabId;
    private String parentMobId;
    private String spawnEggTexturePath;
    private boolean bossBarEnabled;
    private String bossBarTitle;
    private String bossBarColor = DEFAULT_BOSS_BAR_COLOR;
    private String bossBarOverlay = DEFAULT_BOSS_BAR_OVERLAY;
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
            this.categoryName = category.toUpperCase(Locale.ROOT);
        }
        return this;
    }

    @Override
    public IEntityBuilder rig(String rig) {
        if (rig == null || rig.isBlank()) {
            throw new IllegalArgumentException(ENTITY_MSG_PREFIX + id + "' rig must not be blank "
                    + "(use 'parent', 'humanoid' or 'quadruped')");
        }
        String clean = rig.strip().toLowerCase(Locale.ROOT);
        if (!"parent".equals(clean) && !"humanoid".equals(clean) && !"quadruped".equals(clean)) {
            throw new IllegalArgumentException(ENTITY_MSG_PREFIX + id + "' has unknown rig '" + rig
                    + "' (use 'parent', 'humanoid' or 'quadruped')");
        }
        this.rigName = clean;
        return this;
    }

    @Override
    public IEntityBuilder defaultGoals(String preset) {
        this.goalsPreset = cleanGoalsPreset(preset, id);
        return this;
    }

    private static String cleanGoalsPreset(String preset, String id) {
        if (preset == null || preset.isBlank()) {
            return null;
        }
        String clean = preset.strip().toLowerCase(Locale.ROOT);
        if (!"monster".equals(clean) && !"animal".equals(clean) && !"none".equals(clean)) {
            throw new IllegalArgumentException(ENTITY_MSG_PREFIX + id + "' has unknown defaultGoals '" + preset
                    + "' (use 'monster', 'animal' or 'none')");
        }
        return clean;
    }

    @Override
    public IEntityBuilder renderScale(double scale) {
        if (scale <= RENDER_SCALE_MIN_EXCLUSIVE || scale > RENDER_SCALE_MAX_INCLUSIVE) {
            throw new IllegalArgumentException(ENTITY_MSG_PREFIX + id + "' renderScale must be in (0, 16], got: " + scale);
        }
        this.renderScaleValue = scale;
        return this;
    }

    @Override
    public IEntityBuilder fireImmune(boolean immune) {
        this.fireImmuneEnabled = immune;
        return this;
    }

    @Override
    public IEntityBuilder tickRate(int intervalTicks) {
        if (intervalTicks < MIN_TICK_RATE) {
            throw new IllegalArgumentException(ENTITY_MSG_PREFIX + id + "' tickRate must be >= 1, got: " + intervalTicks);
        }
        this.tickRateValue = intervalTicks;
        return this;
    }

    @Override
    public IEntityBuilder dimensions(float width, float height) {
        this.width = Math.max(MIN_DIMENSION, width);
        this.height = Math.max(MIN_DIMENSION, height);
        return this;
    }

    @Override
    public IEntityBuilder maxHealth(double health) {
        this.maxHealthValue = Math.max(MIN_MAX_HEALTH, health);
        return this;
    }

    @Override
    public IEntityBuilder movementSpeed(double speed) {
        this.movementSpeedValue = Math.max(MIN_NON_NEGATIVE, speed);
        return this;
    }

    @Override
    public IEntityBuilder attackDamage(double damage) {
        this.attackDamageValue = Math.max(MIN_NON_NEGATIVE, damage);
        return this;
    }

    @Override
    public IEntityBuilder followRange(double range) {
        this.followRangeValue = Math.max(MIN_FOLLOW_RANGE, range);
        return this;
    }

    @Override
    public IEntityBuilder armor(double armor) {
        this.armorValue = Math.max(MIN_NON_NEGATIVE, armor);
        return this;
    }

    @Override
    public IEntityBuilder knockbackResistance(double resistance) {
        this.knockbackResistanceValue = Math.max(MIN_NON_NEGATIVE, Math.min(MAX_KNOCKBACK_RESISTANCE, resistance));
        return this;
    }

    @Override
    public IEntityBuilder spawnEgg(int primaryColor, int secondaryColor) {
        this.spawnEggEnabled = true;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        return this;
    }

    @Override
    public IEntityBuilder model(String modelPath) {
        throw new IllegalArgumentException(ENTITY_MSG_PREFIX + id + "': model() was never implemented by the engine "
                + "(custom geometry files are not loaded). Use rig('humanoid' | 'quadruped') + texture(path) instead.");
    }

    @Override
    public IEntityBuilder texture(String texturePath) {
        this.texturePath = texturePath;
        return this;
    }

    @Override
    public IEntityBuilder bbmodel(String bbmodelPath) {
        throw new IllegalArgumentException(ENTITY_MSG_PREFIX + id + "': bbmodel() was never implemented by the engine "
                + "(Blockbench files are not loaded). Use rig('humanoid' | 'quadruped') + texture(path) instead.");
    }

    @Override
    public IEntityBuilder ambientSound(String soundId) {
        this.ambientSoundId = soundId;
        return this;
    }

    @Override
    public IEntityBuilder hurtSound(String soundId) {
        this.hurtSoundId = soundId;
        return this;
    }

    @Override
    public IEntityBuilder deathSound(String soundId) {
        this.deathSoundId = soundId;
        return this;
    }

    @Override
    public IEntityBuilder drop(String itemId, int minCount, int maxCount) {
        int min = Math.max(MIN_DROP_COUNT, minCount);
        int max = Math.max(min, maxCount);
        this.drops.add(new EntityDrop(itemId, min, max));
        return this;
    }

    @Override
    public IEntityBuilder experience(int exp) {
        this.experienceValue = Math.max(MIN_EXPERIENCE, exp);
        this.explicitExperienceFlag = true;
        return this;
    }

    @Override
    public IEntityBuilder creativeTab(String tabId) {
        this.creativeTabId = tabId;
        return this;
    }

    @Override
    public IEntityBuilder parent(String parentMob) {
        this.parentMobId = parentMob;
        return this;
    }

    @Override
    public IEntityBuilder parentMob(String parentMob) {
        this.parentMobId = parentMob;
        return this;
    }

    @Override
    public String getParentMob() {
        if (parentMobId != null && !parentMobId.isBlank()) {
            return parentMobId;
        }
        return DEFAULT_PARENT_MOB;
    }

    @Override
    public IEntityBuilder spawnEggTexture(String texturePath) {
        this.spawnEggTexturePath = texturePath;
        this.spawnEggEnabled = true;
        return this;
    }

    @Override
    public IEntityBuilder bossBar(String title, String color, String overlay) {
        this.bossBarEnabled = true;
        this.bossBarTitle = title;
        if (color != null && !color.isBlank()) {
            this.bossBarColor = color.toUpperCase(Locale.ROOT);
        }
        if (overlay != null && !overlay.isBlank()) {
            this.bossBarOverlay = overlay.toUpperCase(Locale.ROOT);
        }
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
    @Override public String getCategory() { return categoryName; }
    @Override public String getRig() { return rigName; }
    @Override public String getDefaultGoals() { return goalsPreset; }
    @Override public double getRenderScale() { return renderScaleValue; }
    @Override public boolean isFireImmune() { return fireImmuneEnabled; }
    @Override public int getTickRate() { return tickRateValue; }
    @Override public float getWidth() { return width; }
    @Override public float getHeight() { return height; }
    @Override public double getMaxHealth() { return maxHealthValue; }
    @Override public double getMovementSpeed() { return movementSpeedValue; }
    @Override public double getAttackDamage() { return attackDamageValue; }
    @Override public double getFollowRange() { return followRangeValue; }
    @Override public double getArmor() { return armorValue; }
    @Override public double getKnockbackResistance() { return knockbackResistanceValue; }
    @Override public boolean hasSpawnEgg() { return spawnEggEnabled; }
    @Override public int getPrimaryColor() { return primaryColor; }
    @Override public int getSecondaryColor() { return secondaryColor; }
    @Override public String getSpawnEggTexture() { return spawnEggTexturePath; }
    @Override public boolean hasBossBar() { return bossBarEnabled; }
    @Override public String getBossBarTitle() { return bossBarTitle; }
    @Override public String getBossBarColor() { return bossBarColor; }
    @Override public String getBossBarOverlay() { return bossBarOverlay; }
    @Override public String getModel() { return null; }
    @Override public String getTexture() { return texturePath; }
    @Override public String getBbmodel() { return null; }
    @Override public String getAmbientSound() { return ambientSoundId; }
    @Override public String getHurtSound() { return hurtSoundId; }
    @Override public String getDeathSound() { return deathSoundId; }
    @Override public List<EntityDrop> getDrops() { return List.copyOf(drops); }
    @Override public int getExperience() { return experienceValue; }
    @Override public boolean hasExplicitExperience() { return explicitExperienceFlag; }
    @Override public @Nullable EntityTickHandler getOnTickHandler() { return onTickHandler; }
    @Override public @Nullable EntityHurtHandler getOnHurtHandler() { return onHurtHandler; }
    @Override public @Nullable EntityDeathHandler getOnDeathHandler() { return onDeathHandler; }
    private com.luatweaker.integration.geo.GeoModelSpec geoModelSpec;

    @Override
    public IEntityBuilder geoModel(String modelPath, String texturePath, String animationPath, @Nullable String defaultAnimation) {
        this.geoModelSpec = new com.luatweaker.integration.geo.GeoModelSpec(modelPath, texturePath, animationPath, defaultAnimation);
        return this;
    }

    @Override public @Nullable EntityInteractHandler getOnInteractHandler() { return onInteractHandler; }
    @Override public String getCreativeTab() { return creativeTabId; }
    @Override @Nullable public com.luatweaker.integration.geo.GeoModelSpec getGeoModel() { return geoModelSpec; }
}
