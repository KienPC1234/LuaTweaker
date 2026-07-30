package com.luatweaker.content;

import com.luatweaker.api.content.IFluidBuilder;

public class FluidBuilderImpl implements IFluidBuilder {
    private final String id;
    private int color = 0xFFFFFFFF;
    private String stillTexture;
    private String flowingTexture;
    private int temperature = 300;
    private int viscosity = 1000;

    public FluidBuilderImpl(String id) {
        this.id = id;
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

    private java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> touchHandler;

    @Override
    public IFluidBuilder onTouch(java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> handler) {
        this.touchHandler = handler;
        return this;
    }

    @Override
    public String getId() { return id; }
    @Override
    public int getColor() { return color; }
    @Override
    public String getStillTexture() { return stillTexture; }
    @Override
    public String getFlowingTexture() { return flowingTexture; }
    @Override
    public int getTemperature() { return temperature; }
    @Override
    public int getViscosity() { return viscosity; }
    @Override
    public java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> getTouchHandler() { return touchHandler; }
}

