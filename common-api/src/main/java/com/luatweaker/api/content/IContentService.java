package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import java.util.Collection;
import java.util.function.Consumer;

@LuaDoc(description = "Service for creating and registering custom Items, Blocks, Weapons, Tools, Armor, and Fluids.")
public interface IContentService {
    @LuaDoc(description = "Creates and registers a custom Item.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createItem(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Sword weapon.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createSword(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Pickaxe tool.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createPickaxe(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Axe tool.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createAxe(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Shovel tool.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createShovel(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Hoe tool.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createHoe(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Helmet armor.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createHelmet(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Chestplate armor.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createChestplate(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Leggings armor.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createLeggings(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Boots armor.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createBoots(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Ranged weapon item.", params = {"id: string", "builder: function(item)"}, returnType = "IItemBuilder")
    IItemBuilder createRangedItem(String id, Consumer<IItemBuilder> builderConsumer);


    @LuaDoc(description = "Creates and registers a custom Block.", params = {"id: string", "builder: function(block)"}, returnType = "IBlockBuilder")
    IBlockBuilder createBlock(String id, Consumer<IBlockBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Fluid.", params = {"id: string", "builder: function(fluid)"}, returnType = "IFluidBuilder")
    IFluidBuilder createFluid(String id, Consumer<IFluidBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Creative Mode Tab.", params = {"id: string", "builder: function(tab)"}, returnType = "ICreativeTabBuilder")
    ICreativeTabBuilder createTab(String id, Consumer<ICreativeTabBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers an explicit custom Armor Material.", params = {"id: string", "builder: function(mat)"}, returnType = "IArmorMaterialBuilder")
    IArmorMaterialBuilder createArmorMaterial(String id, Consumer<IArmorMaterialBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Stairs block.", params = {"id: string", "baseBlockId: string"}, returnType = "IBlockBuilder")
    IBlockBuilder createStairs(String id, String baseBlockId);

    @LuaDoc(description = "Creates and registers a custom Slab block.", params = {"id: string", "baseBlockId: string"}, returnType = "IBlockBuilder")
    IBlockBuilder createSlab(String id, String baseBlockId);

    @LuaDoc(description = "Creates and registers a custom Wall block.", params = {"id: string", "baseBlockId: string"}, returnType = "IBlockBuilder")
    IBlockBuilder createWall(String id, String baseBlockId);

    Collection<IItemBuilder> getRegisteredItems();

    Collection<IBlockBuilder> getRegisteredBlocks();
    Collection<IFluidBuilder> getRegisteredFluids();
    Collection<ICreativeTabBuilder> getRegisteredTabs();
    Collection<IArmorMaterialBuilder> getRegisteredArmorMaterials();
    IArmorMaterialBuilder getArmorMaterial(String id);
}



