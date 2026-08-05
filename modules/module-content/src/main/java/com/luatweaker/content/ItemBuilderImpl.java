package com.luatweaker.content;

import com.luatweaker.api.content.IItemBuilder;
import java.util.function.BiConsumer;

public class ItemBuilderImpl implements IItemBuilder {
    private final String id;
    private String type = "GENERIC";
    private int maxStackSize = 64;
    private String rarity = "COMMON";
    private int durability = 0;
    private int miningLevel = 0;
    private float miningSpeed = 4.0f;
    private float attackDamage = 0.0f;
    private float attackSpeed = 0.0f;
    private int defense = 0;
    private float toughness = 0.0f;
    private float knockbackResistance = 0.0f;
    private int enchantability = 10;
    private int burnTime = 0;
    private String displayName;
    private String model;
    private String texture;
    private BiConsumer<Object, Object> rightClickHandler;

    public ItemBuilderImpl(String id) {
        this.id = id;
    }

    @Override
    public IItemBuilder type(String type) {
        if (type != null) this.type = type.toUpperCase();
        return this;
    }

    @Override
    public IItemBuilder maxStackSize(int size) {
        this.maxStackSize = size;
        return this;
    }

    @Override
    public IItemBuilder rarity(String rarity) {
        if (rarity != null) this.rarity = rarity.toUpperCase();
        return this;
    }

    @Override
    public IItemBuilder durability(int durability) {
        this.durability = durability;
        return this;
    }

    @Override
    public IItemBuilder miningLevel(int level) {
        this.miningLevel = level;
        return this;
    }

    @Override
    public IItemBuilder miningLevel(String levelName) {
        if (levelName != null) {
            this.miningLevel = switch (levelName.toUpperCase()) {
                case "WOOD", "GOLD" -> 0;
                case "STONE" -> 1;
                case "IRON" -> 2;
                case "DIAMOND" -> 3;
                case "NETHERITE" -> 4;
                default -> {
                    try { 
                        yield Integer.parseInt(levelName); 
                    } catch (NumberFormatException e) { 
                        com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Invalid mining level: " + levelName + ". Defaulting to 1.");
                        yield 1; 
                    }
                }
            };
        }
        return this;
    }

    @Override
    public IItemBuilder miningSpeed(float speed) {
        this.miningSpeed = speed;
        return this;
    }

    @Override
    public IItemBuilder attackDamage(float damage) {
        this.attackDamage = damage;
        return this;
    }

    @Override
    public IItemBuilder attackSpeed(float speed) {
        this.attackSpeed = speed;
        return this;
    }

    @Override
    public IItemBuilder defense(int defense) {
        this.defense = defense;
        return this;
    }

    @Override
    public IItemBuilder toughness(float toughness) {
        this.toughness = toughness;
        return this;
    }

    @Override
    public IItemBuilder knockbackResistance(float kbRes) {
        this.knockbackResistance = kbRes;
        return this;
    }

    @Override
    public IItemBuilder enchantability(int enchValue) {
        this.enchantability = enchValue;
        return this;
    }

    @Override
    public IItemBuilder burnTime(int ticks) {
        this.burnTime = ticks;
        return this;
    }

    @Override
    public IItemBuilder displayName(String name) {
        this.displayName = name;
        return this;
    }

    @Override
    public IItemBuilder model(String modelPath) {
        this.model = modelPath;
        return this;
    }

    @Override
    public IItemBuilder texture(String texturePath) {
        this.texture = texturePath;
        return this;
    }

    private final java.util.List<String> tags = new java.util.ArrayList<>();
    private String armorMaterial;
    private String armorTexture;
    private String creativeTab;
    private BiConsumer<Object, Object> onHitEntityHandler;


    @Override
    public IItemBuilder armorMaterial(String materialId) {
        this.armorMaterial = materialId;
        return this;
    }

    @Override
    public IItemBuilder armorTexture(String texturePath) {
        this.armorTexture = texturePath;
        return this;
    }

    @Override
    public IItemBuilder onRightClick(BiConsumer<Object, Object> handler) {
        this.rightClickHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder onHitEntity(BiConsumer<Object, Object> handler) {
        this.onHitEntityHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder tag(String tagId) {
        if (tagId != null && !tagId.isBlank()) this.tags.add(tagId);
        return this;
    }

    @Override
    public IItemBuilder creativeTab(String tabId) {
        this.creativeTab = tabId;
        return this;
    }

    private int foodHunger;
    private float foodSaturation;
    private boolean alwaysEdible;
    private BiConsumer<Object, Object> onConsumeHandler;
    private boolean glow;
    private final java.util.List<String> tooltips = new java.util.ArrayList<>();

    @Override
    public IItemBuilder food(int hunger, float saturation) {
        this.foodHunger = hunger;
        this.foodSaturation = saturation;
        return this;
    }

    @Override
    public IItemBuilder alwaysEdible() {
        this.alwaysEdible = true;
        return this;
    }

    @Override
    public IItemBuilder onConsume(BiConsumer<Object, Object> handler) {
        this.onConsumeHandler = handler;
        return this;
    }

    @Override
    public IItemBuilder glow(boolean enable) {
        this.glow = enable;
        return this;
    }

    @Override
    public IItemBuilder tooltip(String text) {
        if (text != null && !text.isBlank()) this.tooltips.add(text);
        return this;
    }

    @Override public String getId() { return id; }
    @Override public String getType() { return type; }
    @Override public int getMaxStackSize() { return maxStackSize; }
    @Override public String getRarity() { return rarity; }
    @Override public int getDurability() { return durability; }
    @Override public int getMiningLevel() { return miningLevel; }
    @Override public float getMiningSpeed() { return miningSpeed; }
    @Override public float getAttackDamage() { return attackDamage; }
    @Override public String getDisplayName() { return displayName; }
    @Override public float getAttackSpeed() { return attackSpeed; }
    @Override public int getDefense() { return defense; }
    @Override public float getToughness() { return toughness; }
    @Override public float getKnockbackResistance() { return knockbackResistance; }
    @Override public int getEnchantability() { return enchantability; }
    @Override public String getArmorMaterial() { return armorMaterial; }
    @Override public String getArmorTexture() { return armorTexture; }
    @Override public int getBurnTime() { return burnTime; }
    @Override public String getModel() { return model; }
    @Override public String getTexture() { return texture; }
    @Override public java.util.List<String> getTags() { return tags; }
    @Override public String getCreativeTab() { return creativeTab; }
    @Override public BiConsumer<Object, Object> getRightClickHandler() { return rightClickHandler; }
    @Override public BiConsumer<Object, Object> getOnHitEntityHandler() { return onHitEntityHandler; }

    @Override public int getFoodHunger() { return foodHunger; }
    @Override public float getFoodSaturation() { return foodSaturation; }
    @Override public boolean isAlwaysEdible() { return alwaysEdible; }
    @Override public BiConsumer<Object, Object> getOnConsumeHandler() { return onConsumeHandler; }
    @Override public boolean isGlow() { return glow; }
    @Override public java.util.List<String> getTooltips() { return tooltips; }
}




