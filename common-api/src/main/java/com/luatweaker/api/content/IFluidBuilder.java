package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Builder interface for defining custom Fluid properties.")
public interface IFluidBuilder {
    @LuaDoc(description = "Sets the ARGB hex color of the fluid.", params = {"colorHex: integer"}, returnType = "IFluidBuilder")
    IFluidBuilder color(int colorHex);

    @LuaDoc(description = "Sets the texture path for still fluid.", params = {"texturePath: string"}, returnType = "IFluidBuilder")
    IFluidBuilder stillTexture(String texturePath);

    @LuaDoc(description = "Sets the texture path for flowing fluid.", params = {"texturePath: string"}, returnType = "IFluidBuilder")
    IFluidBuilder flowingTexture(String texturePath);

    @LuaDoc(description = "Sets the temperature in Kelvin.", params = {"temp: integer"}, returnType = "IFluidBuilder")
    IFluidBuilder temperature(int temp);

    @LuaDoc(description = "Sets the viscosity of the fluid.", params = {"viscosity: integer"}, returnType = "IFluidBuilder")
    IFluidBuilder viscosity(int viscosity);

    @LuaDoc(description = "Sets handler when an entity/player touches or enters the fluid.", params = {"handler: function(player)"}, returnType = "IFluidBuilder")
    IFluidBuilder onTouch(java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> handler);

    String getId();
    int getColor();
    String getStillTexture();
    String getFlowingTexture();
    int getTemperature();
    int getViscosity();
    java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> getTouchHandler();
}

