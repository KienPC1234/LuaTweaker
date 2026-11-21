package com.luatweaker.content;

import com.luatweaker.api.content.IFluidBuilder;

public class FluidBuilderImpl implements IFluidBuilder {
    private final String id;
    private int colorHex = 0xFFFFFFFF;
    private String stillTexturePath;
    private String flowingTexturePath;
    private int temperatureValue = 300;
    private int viscosityValue = 1000;
    private int densityValue = 1000;
    private int lightLevelValue;
    private int slopeFindDistanceValue = 4;
    private int levelDecreasePerBlockValue = 1;
    private int tickRateValue = 5;
    private float explosionResistanceValue = 100.0f;
    private String rarityName = "COMMON";
    private java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> touchHandler;
    private String creativeTabId;
    private String displayNameText;

    public FluidBuilderImpl(String id) {
        this.id = id;
    }

    @Override
    public IFluidBuilder displayName(String name) {
        this.displayNameText = name;
        return this;
    }

    @Override
    public String getDisplayName() {
        return displayNameText != null ? displayNameText : id;
    }

    @Override
    public IFluidBuilder color(int colorHex) {
        this.colorHex = colorHex;
        return this;
    }

    @Override
    public IFluidBuilder stillTexture(String texturePath) {
        this.stillTexturePath = texturePath;
        return this;
    }

    @Override
    public IFluidBuilder flowingTexture(String texturePath) {
        this.flowingTexturePath = texturePath;
        return this;
    }

    @Override
    public IFluidBuilder temperature(int temp) {
        this.temperatureValue = temp;
        return this;
    }

    @Override
    public IFluidBuilder viscosity(int viscosity) {
        this.viscosityValue = viscosity;
        return this;
    }

    @Override
    public IFluidBuilder density(int density) {
        this.densityValue = density;
        return this;
    }

    @Override
    public IFluidBuilder lightLevel(int level) {
        this.lightLevelValue = level;
        return this;
    }

    @Override
    public IFluidBuilder slopeFindDistance(int distance) {
        this.slopeFindDistanceValue = distance;
        return this;
    }

    @Override
    public IFluidBuilder levelDecreasePerBlock(int decrease) {
        this.levelDecreasePerBlockValue = decrease;
        return this;
    }

    @Override
    public IFluidBuilder tickRate(int rate) {
        this.tickRateValue = rate;
        return this;
    }

    @Override
    public IFluidBuilder explosionResistance(float resistance) {
        this.explosionResistanceValue = resistance;
        return this;
    }

    @Override
    public IFluidBuilder rarity(String rarity) {
        this.rarityName = rarity;
        return this;
    }

    @Override
    public IFluidBuilder onTouch(java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> handler) {
        this.touchHandler = handler;
        return this;
    }

    @Override
    public IFluidBuilder creativeTab(String tabId) {
        this.creativeTabId = tabId;
        return this;
    }

    @Override public String getId() { return id; }
    @Override public int getColor() { return colorHex; }
    @Override public String getStillTexture() { return stillTexturePath; }
    @Override public String getFlowingTexture() { return flowingTexturePath; }
    @Override public int getTemperature() { return temperatureValue; }
    @Override public int getViscosity() { return viscosityValue; }
    @Override public int getDensity() { return densityValue; }
    @Override public int getLightLevel() { return lightLevelValue; }
    @Override public int getSlopeFindDistance() { return slopeFindDistanceValue; }
    @Override public int getLevelDecreasePerBlock() { return levelDecreasePerBlockValue; }
    @Override public int getTickRate() { return tickRateValue; }
    @Override public float getExplosionResistance() { return explosionResistanceValue; }
    @Override public String getRarity() { return rarityName; }
    @Override public java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> getTouchHandler() { return touchHandler; }
    @Override public String getCreativeTab() { return creativeTabId; }
}
