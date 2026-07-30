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

    @LuaDoc(description = "Sets the density of the fluid.", params = {"density: integer"}, returnType = "IFluidBuilder")
    IFluidBuilder density(int density);

    @LuaDoc(description = "Sets emission light level (0-15).", params = {"level: integer"}, returnType = "IFluidBuilder")
    IFluidBuilder lightLevel(int level);

    @LuaDoc(description = "Sets slope find distance (default 4).", params = {"distance: integer"}, returnType = "IFluidBuilder")
    IFluidBuilder slopeFindDistance(int distance);

    @LuaDoc(description = "Sets level decrease per block (default 1).", params = {"decrease: integer"}, returnType = "IFluidBuilder")
    IFluidBuilder levelDecreasePerBlock(int decrease);

    @LuaDoc(description = "Sets tick rate (default 5).", params = {"rate: integer"}, returnType = "IFluidBuilder")
    IFluidBuilder tickRate(int rate);

    @LuaDoc(description = "Sets explosion resistance.", params = {"resistance: number"}, returnType = "IFluidBuilder")
    IFluidBuilder explosionResistance(float resistance);

    @LuaDoc(description = "Sets item rarity ('COMMON', 'UNCOMMON', 'RARE', 'EPIC').", params = {"rarity: string"}, returnType = "IFluidBuilder")
    IFluidBuilder rarity(String rarity);

    @LuaDoc(description = "Sets handler when an entity/player touches or enters the fluid.", params = {"handler: function(player)"}, returnType = "IFluidBuilder")
    IFluidBuilder onTouch(java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> handler);

    @LuaDoc(description = "Sets the target creative mode tab for this fluid bucket.", params = {"tabId: string"}, returnType = "IFluidBuilder")
    IFluidBuilder creativeTab(String tabId);

    String getId();
    int getColor();
    String getStillTexture();
    String getFlowingTexture();
    int getTemperature();
    int getViscosity();
    int getDensity();
    int getLightLevel();
    int getSlopeFindDistance();
    int getLevelDecreasePerBlock();
    int getTickRate();
    float getExplosionResistance();
    String getRarity();
    java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> getTouchHandler();
    String getCreativeTab();
}
