package com.luatweaker.content;

import com.luatweaker.api.content.IFluidBuilder;

public class FluidBuilderImpl implements IFluidBuilder {
    private final String id;
    private int color = 0xFFFFFFFF;
    private String stillTexture;
    private String flowingTexture;
    private int temperature = 300;
    private int viscosity = 1000;
    private int density = 1000;
    private int lightLevel = 0;
    private int slopeFindDistance = 4;
    private int levelDecreasePerBlock = 1;
    private int tickRate = 5;
    private float explosionResistance = 100.0f;
    private String rarity = "COMMON";
    private java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> touchHandler;
    private String creativeTab;
    private String displayName;

    public FluidBuilderImpl(String id) {
        this.id = id;
    }

    @Override
    public IFluidBuilder displayName(String name) {
        this.displayName = name;
        return this;
    }

    @Override
    public String getDisplayName() {
        return displayName != null ? displayName : id;
    }

    @Override
    public IFluidBuilder color(int colorHex) {
        this.color = colorHex;
        return this;
    }

    @Override
    public IFluidBuilder stillTexture(String texturePath) {
        this.stillTexture = texturePath;
        return this;
    }

    @Override
    public IFluidBuilder flowingTexture(String texturePath) {
        this.flowingTexture = texturePath;
        return this;
    }

    @Override
    public IFluidBuilder temperature(int temp) {
        this.temperature = temp;
        return this;
    }

    @Override
    public IFluidBuilder viscosity(int viscosity) {
        this.viscosity = viscosity;
        return this;
    }

    @Override
    public IFluidBuilder density(int density) {
        this.density = density;
        return this;
    }

    @Override
    public IFluidBuilder lightLevel(int level) {
        this.lightLevel = level;
        return this;
    }

    @Override
    public IFluidBuilder slopeFindDistance(int distance) {
        this.slopeFindDistance = distance;
        return this;
    }

    @Override
    public IFluidBuilder levelDecreasePerBlock(int decrease) {
        this.levelDecreasePerBlock = decrease;
        return this;
    }

    @Override
    public IFluidBuilder tickRate(int rate) {
        this.tickRate = rate;
        return this;
    }

    @Override
    public IFluidBuilder explosionResistance(float resistance) {
        this.explosionResistance = resistance;
        return this;
    }

    @Override
    public IFluidBuilder rarity(String rarity) {
        this.rarity = rarity;
        return this;
    }

    @Override
    public IFluidBuilder onTouch(java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> handler) {
        this.touchHandler = handler;
        return this;
    }

    @Override
    public IFluidBuilder creativeTab(String tabId) {
        this.creativeTab = tabId;
        return this;
    }

    @Override public String getId() { return id; }
    @Override public int getColor() { return color; }
    @Override public String getStillTexture() { return stillTexture; }
    @Override public String getFlowingTexture() { return flowingTexture; }
    @Override public int getTemperature() { return temperature; }
    @Override public int getViscosity() { return viscosity; }
    @Override public int getDensity() { return density; }
    @Override public int getLightLevel() { return lightLevel; }
    @Override public int getSlopeFindDistance() { return slopeFindDistance; }
    @Override public int getLevelDecreasePerBlock() { return levelDecreasePerBlock; }
    @Override public int getTickRate() { return tickRate; }
    @Override public float getExplosionResistance() { return explosionResistance; }
    @Override public String getRarity() { return rarity; }
    @Override public java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> getTouchHandler() { return touchHandler; }
    @Override public String getCreativeTab() { return creativeTab; }
}
