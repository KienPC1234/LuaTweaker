package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;

@LuaDoc(description = "Builder interface for defining custom Fluid properties.")
public interface IFluidBuilder {
    @LuaDoc(description = "Sets human-readable display name for the fluid.", params = {"name: string"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder displayName(String name);

    @LuaDoc(description = "Sets the ARGB hex color of the fluid.", params = {"colorHex: integer"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder color(int colorHex);

    @LuaDoc(description = "Sets the texture path for still fluid.", params = {"texturePath: string"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder stillTexture(String texturePath);

    @LuaDoc(description = "Sets the texture path for flowing fluid.", params = {"texturePath: string"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder flowingTexture(String texturePath);

    @LuaDoc(description = "Sets the temperature in Kelvin.", params = {"temp: integer"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder temperature(int temp);

    @LuaDoc(description = "Sets the viscosity of the fluid.", params = {"viscosity: integer"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder viscosity(int viscosity);

    @LuaDoc(description = "Sets the density of the fluid.", params = {"density: integer"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder density(int density);

    @LuaDoc(description = "Sets emission light level (0-15).", params = {"level: integer"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder lightLevel(int level);

    @LuaDoc(description = "Sets slope find distance (default 4).", params = {"distance: integer"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder slopeFindDistance(int distance);

    @LuaDoc(description = "Sets level decrease per block (default 1).", params = {"decrease: integer"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder levelDecreasePerBlock(int decrease);

    @LuaDoc(description = "Sets tick rate (default 5).", params = {"rate: integer"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder tickRate(int rate);

    @LuaDoc(description = "Sets explosion resistance.", params = {"resistance: number"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder explosionResistance(float resistance);

    @LuaDoc(description = "Sets item rarity ('COMMON', 'UNCOMMON', 'RARE', 'EPIC').", params = {"rarity: string"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder rarity(String rarity);

    @LuaDoc(description = "Sets handler when an entity/player touches or enters the fluid.", params = {"handler: function(player)"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder onTouch(java.util.function.Consumer<com.luatweaker.api.entity.IPlayer> handler);

    @LuaDoc(description = "Sets the target creative mode tab for this fluid bucket.", params = {"tabId: string"}, returnType = LuaDocFragments.Builder.I_FLUID_BUILDER)
    IFluidBuilder creativeTab(String tabId);

    String getId();
    String getDisplayName();
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
