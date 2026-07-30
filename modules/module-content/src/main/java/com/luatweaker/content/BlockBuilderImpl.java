package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import java.util.function.BiConsumer;

public class BlockBuilderImpl implements IBlockBuilder {
    private final String id;
    private float hardness = 1.5f;
    private float resistance = 6.0f;
    private int lightLevel = 0;
    private String soundType = "STONE";
    private boolean requiresTool = false;
    private String mineableWith;
    private int miningLevel = 0;
    private float friction = 0.6f;
    private String model;
    private String texture;
    private BiConsumer<Object, Object> rightClickHandler;

    public BlockBuilderImpl(String id) {
        this.id = id;
    }

    @Override
    public IBlockBuilder hardness(float hardness) {
        this.hardness = hardness;
        return this;
    }

    @Override
    public IBlockBuilder resistance(float resistance) {
        this.resistance = resistance;
        return this;
    }

    @Override
    public IBlockBuilder lightLevel(int level) {
        this.lightLevel = level;
        return this;
    }

    @Override
    public IBlockBuilder soundType(String soundType) {
        this.soundType = soundType != null ? soundType.toUpperCase() : "STONE";
        return this;
    }

    @Override
    public IBlockBuilder requiresTool(boolean requires) {
        this.requiresTool = requires;
        return this;
    }

    @Override
    public IBlockBuilder mineableWith(String toolType) {
        this.mineableWith = toolType != null ? toolType.toUpperCase() : null;
        this.requiresTool = true;
        return this;
    }

    @Override
    public IBlockBuilder miningLevel(int level) {
        this.miningLevel = level;
        this.requiresTool = true;
        return this;
    }

    @Override
    public IBlockBuilder miningLevel(String levelName) {
        if (levelName != null) {
            this.miningLevel = switch (levelName.toUpperCase()) {
                case "WOOD", "GOLD" -> 0;
                case "STONE" -> 1;
                case "IRON" -> 2;
                case "DIAMOND" -> 3;
                case "NETHERITE" -> 4;
                default -> {
                    try { yield Integer.parseInt(levelName); } catch (Exception e) { yield 1; }
                }
            };
            this.requiresTool = true;
        }
        return this;
    }

    @Override
    public IBlockBuilder friction(float friction) {
        this.friction = friction;
        return this;
    }

    @Override
    public IBlockBuilder model(String modelPath) {
        this.model = modelPath;
        return this;
    }

    @Override
    public IBlockBuilder texture(String texturePath) {
        this.texture = texturePath;
        return this;
    }

    private final java.util.List<String> tags = new java.util.ArrayList<>();
    private String creativeTab;

    @Override
    public IBlockBuilder onRightClick(BiConsumer<Object, Object> handler) {
        this.rightClickHandler = handler;
        return this;
    }

    @Override
    public IBlockBuilder tag(String tagId) {
        if (tagId != null && !tagId.isBlank()) this.tags.add(tagId);
        return this;
    }

    @Override
    public IBlockBuilder creativeTab(String tabId) {
        this.creativeTab = tabId;
        return this;
    }

    private String dropItemId;
    private int minDropCount = 1;
    private int maxDropCount = 1;
    private int minExp = 0;
    private int maxExp = 0;

    @Override
    public IBlockBuilder drop(String itemId, int minCount, int maxCount) {
        this.dropItemId = itemId;
        this.minDropCount = Math.max(1, minCount);
        this.maxDropCount = Math.max(this.minDropCount, maxCount);
        return this;
    }

    @Override
    public IBlockBuilder dropExperience(int minExp, int maxExp) {
        this.minExp = Math.max(0, minExp);
        this.maxExp = Math.max(this.minExp, maxExp);
        return this;
    }

    @Override public String getId() { return id; }
    @Override public float getHardness() { return hardness; }
    @Override public float getResistance() { return resistance; }
    @Override public int getLightLevel() { return lightLevel; }
    @Override public String getSoundType() { return soundType; }
    @Override public boolean getRequiresTool() { return requiresTool; }
    @Override public String getMineableWith() { return mineableWith; }
    @Override public int getMiningLevel() { return miningLevel; }
    @Override public float getFriction() { return friction; }
    @Override public String getModel() { return model; }
    @Override public String getTexture() { return texture; }
    @Override public java.util.List<String> getTags() { return tags; }
    @Override public String getCreativeTab() { return creativeTab; }
    @Override public BiConsumer<Object, Object> getRightClickHandler() { return rightClickHandler; }
    @Override public String getDropItemId() { return dropItemId; }
    @Override public int getMinDropCount() { return minDropCount; }
    @Override public int getMaxDropCount() { return maxDropCount; }
    @Override public int getMinExp() { return minExp; }
    @Override public int getMaxExp() { return maxExp; }
}



