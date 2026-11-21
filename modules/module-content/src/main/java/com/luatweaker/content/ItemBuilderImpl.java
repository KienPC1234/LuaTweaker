package com.luatweaker.content;

import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.content.ItemHurtHandler;
import com.luatweaker.api.content.ItemMineHandler;
import com.luatweaker.api.content.ItemReleaseHandler;
import com.luatweaker.api.content.ItemUseTickHandler;
import java.util.Locale;
import java.util.function.BiConsumer;

public class ItemBuilderImpl implements IItemBuilder {
    /** DSL defaults applied before any Lua callback runs. */
    private static final String DEFAULT_TYPE = "GENERIC";
    private static final int DEFAULT_MAX_STACK_SIZE = 64;
    private static final String DEFAULT_RARITY = "COMMON";
    private static final float DEFAULT_MINING_SPEED = 4.0f;
    private static final int DEFAULT_ENCHANTABILITY = 10;
    private static final String DEFAULT_USE_ANIMATION = "NONE";

    private final String id;
    private String itemType = DEFAULT_TYPE;
    private int maxStackSizeValue = DEFAULT_MAX_STACK_SIZE;
    private String rarityName = DEFAULT_RARITY;
    private int durabilityValue;
    private int miningLevelValue;
    private float miningSpeedValue = DEFAULT_MINING_SPEED;
    private float attackDamageValue;
    private float attackSpeedValue;
    private int defenseValue;
    private float toughnessValue;
    private float knockbackResistanceValue;
    private int enchantabilityValue = DEFAULT_ENCHANTABILITY;
    private int burnTimeTicks;
    private String displayNameText;
    private String modelPath;
    private String texturePath;
    private BiConsumer<Object, Object> rightClickHandler;

    public ItemBuilderImpl(String id) {
        this.id = id;
    }

    @Override
    public IItemBuilder type(String type) {
        if (type != null) {
            this.itemType = type.toUpperCase(Locale.ROOT);
        }
        return this;
    }

    @Override
    public IItemBuilder maxStackSize(int size) {
        this.maxStackSizeValue = size;
        return this;
    }

    @Override
    public IItemBuilder rarity(String rarity) {
        if (rarity != null) {
            this.rarityName = rarity.toUpperCase(Locale.ROOT);
        }
        return this;
    }

    @Override
    public IItemBuilder durability(int durability) {
        this.durabilityValue = durability;
        return this;
    }

    @Override
    public IItemBuilder miningLevel(int level) {
        this.miningLevelValue = level;
        return this;
    }

    @Override
    public IItemBuilder miningLevel(String levelName) {
        if (levelName != null) {
            this.miningLevelValue = MiningLevels.parse(levelName);
        }
        return this;
    }

    @Override
    public IItemBuilder miningSpeed(float speed) {
        this.miningSpeedValue = speed;
        return this;
    }

    @Override
    public IItemBuilder attackDamage(float damage) {
        this.attackDamageValue = damage;
        return this;
    }

    @Override
    public IItemBuilder attackSpeed(float speed) {
        this.attackSpeedValue = speed;
        return this;
    }

    @Override
    public IItemBuilder defense(int defense) {
        this.defenseValue = defense;
        return this;
    }

    @Override
    public IItemBuilder toughness(float toughness) {
        this.toughnessValue = toughness;
        return this;
    }

    @Override
    public IItemBuilder knockbackResistance(float kbRes) {
        this.knockbackResistanceValue = kbRes;
        return this;
    }

    @Override
    public IItemBuilder enchantability(int enchValue) {
        this.enchantabilityValue = enchValue;
        return this;
    }

    @Override
    public IItemBuilder burnTime(int ticks) {
        this.burnTimeTicks = ticks;
        return this;
    }

    @Override
    public IItemBuilder displayName(String name) {
        this.displayNameText = name;
        return this;
    }

    @Override
    public IItemBuilder model(String modelPath) {
        this.modelPath = modelPath;
        return this;
    }

    @Override
    public IItemBuilder texture(String texturePath) {
        this.texturePath = texturePath;
        return this;
    }

    private final java.util.List<String> tags = new java.util.ArrayList<>();
    private String armorMaterialId;
    private String armorTexturePath;
    private String creativeTabId;
    private BiConsumer<Object, Object> onHitEntityHandler;
    private java.util.function.BiFunction<Object, Object, Boolean> onUseOnBlockHandler;


    @Override
    public IItemBuilder armorMaterial(String materialId) {
        this.armorMaterialId = materialId;
        return this;
    }

    @Override
    public IItemBuilder armorTexture(String texturePath) {
        this.armorTexturePath = texturePath;
        return this;
    }

    @Override
    public IItemBuilder onRightClick(BiConsumer<Object, Object> handler) {
        this.rightClickHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder onUseOnBlock(java.util.function.BiFunction<Object, Object, Boolean> handler) {
        this.onUseOnBlockHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder onHitEntity(BiConsumer<Object, Object> handler) {
        this.onHitEntityHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder tag(String tagId) {
        if (tagId != null && !tagId.isBlank()) {
            this.tags.add(tagId);
        }
        return this;
    }

    @Override
    public IItemBuilder creativeTab(String tabId) {
        this.creativeTabId = tabId;
        return this;
    }

    private int foodHunger;
    private float foodSaturation;
    private boolean alwaysEdibleEnabled;
    private BiConsumer<Object, Object> onConsumeHandler;
    private boolean glowEnabled;
    private final java.util.List<String> tooltips = new java.util.ArrayList<>();

    private String useAnimationName = DEFAULT_USE_ANIMATION;
    private int useDurationTicks;
    private ItemUseTickHandler onUseTickHandler;
    private ItemReleaseHandler onReleaseUsingHandler;
    private BiConsumer<Object, Object> onFinishUsingHandler;
    private ItemHurtHandler onHurtEnemyHandler;
    private ItemMineHandler onMineBlockHandler;
    private BiConsumer<Object, Object> onSwingHandler;

    @Override
    public IItemBuilder food(int hunger, float saturation) {
        this.foodHunger = hunger;
        this.foodSaturation = saturation;
        return this;
    }

    @Override
    public IItemBuilder alwaysEdible() {
        this.alwaysEdibleEnabled = true;
        return this;
    }

    @Override
    public IItemBuilder onConsume(BiConsumer<Object, Object> handler) {
        this.onConsumeHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder glow(boolean enable) {
        this.glowEnabled = enable;
        return this;
    }

    @Override
    public IItemBuilder tooltip(String text) {
        if (text != null && !text.isBlank()) {
            this.tooltips.add(text);
        }
        return this;
    }

    @Override
    public IItemBuilder useAnimation(String animation) {
        if (animation != null) {
            this.useAnimationName = animation.toUpperCase(Locale.ROOT);
        }
        return this;
    }

    @Override
    public IItemBuilder useDuration(int ticks) {
        this.useDurationTicks = ticks;
        return this;
    }

    @Override
    public IItemBuilder onUseTick(ItemUseTickHandler handler) {
        this.onUseTickHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder onReleaseUsing(ItemReleaseHandler handler) {
        this.onReleaseUsingHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder onFinishUsing(BiConsumer<Object, Object> handler) {
        this.onFinishUsingHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder onHurtEnemy(ItemHurtHandler handler) {
        this.onHurtEnemyHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder onMineBlock(ItemMineHandler handler) {
        this.onMineBlockHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder onSwing(BiConsumer<Object, Object> handler) {
        this.onSwingHandler = handler;
        return this;
    }

    private com.luatweaker.integration.geo.GeoModelSpec geoModelSpec;

    @Override
    public IItemBuilder geoModel(String modelPath, String texturePath, String animationPath, @org.jetbrains.annotations.Nullable String defaultAnimation) {
        this.geoModelSpec = new com.luatweaker.integration.geo.GeoModelSpec(modelPath, texturePath, animationPath, defaultAnimation);
        return this;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public com.luatweaker.integration.geo.GeoModelSpec getGeoModel() {
        return geoModelSpec;
    }

    @Override public String getId() { return id; }
    @Override public String getType() { return itemType; }
    @Override public int getMaxStackSize() { return maxStackSizeValue; }
    @Override public String getRarity() { return rarityName; }
    @Override public int getDurability() { return durabilityValue; }
    @Override public int getMiningLevel() { return miningLevelValue; }
    @Override public float getMiningSpeed() { return miningSpeedValue; }
    @Override public float getAttackDamage() { return attackDamageValue; }
    @Override public String getDisplayName() { return displayNameText; }
    @Override public float getAttackSpeed() { return attackSpeedValue; }
    @Override public int getDefense() { return defenseValue; }
    @Override public float getToughness() { return toughnessValue; }
    @Override public float getKnockbackResistance() { return knockbackResistanceValue; }
    @Override public int getEnchantability() { return enchantabilityValue; }
    @Override public String getArmorMaterial() { return armorMaterialId; }
    @Override public String getArmorTexture() { return armorTexturePath; }
    @Override public int getBurnTime() { return burnTimeTicks; }
    @Override public String getModel() { return modelPath; }
    @Override public String getTexture() { return texturePath; }
    @Override public java.util.List<String> getTags() { return tags; }
    @Override public String getCreativeTab() { return creativeTabId; }
    @Override public BiConsumer<Object, Object> getRightClickHandler() { return rightClickHandler; }
    @Override public java.util.function.BiFunction<Object, Object, Boolean> getOnUseOnBlockHandler() { return onUseOnBlockHandler; }
    @Override public BiConsumer<Object, Object> getOnHitEntityHandler() { return onHitEntityHandler; }

    @Override public int getFoodHunger() { return foodHunger; }
    @Override public float getFoodSaturation() { return foodSaturation; }
    @Override public boolean isAlwaysEdible() { return alwaysEdibleEnabled; }
    @Override public BiConsumer<Object, Object> getOnConsumeHandler() { return onConsumeHandler; }
    @Override public boolean isGlow() { return glowEnabled; }
    @Override public java.util.List<String> getTooltips() { return tooltips; }

    @Override public String getUseAnimation() { return useAnimationName; }
    @Override public int getUseDuration() { return useDurationTicks; }
    @Override public ItemUseTickHandler getOnUseTickHandler() { return onUseTickHandler; }
    @Override public ItemReleaseHandler getOnReleaseUsingHandler() { return onReleaseUsingHandler; }
    @Override public BiConsumer<Object, Object> getOnFinishUsingHandler() { return onFinishUsingHandler; }
    @Override public ItemHurtHandler getOnHurtEnemyHandler() { return onHurtEnemyHandler; }
    @Override public ItemMineHandler getOnMineBlockHandler() { return onMineBlockHandler; }
    @Override public BiConsumer<Object, Object> getOnSwingHandler() { return onSwingHandler; }
}
