package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import java.util.Collection;
import java.util.function.Consumer;

@LuaDoc(description = "Service for creating and registering custom Items, Blocks, Weapons, Tools, Armor, and Fluids.")
public interface IContentService {
    @LuaDoc(description = "Creates and registers a custom Item.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createItem(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Sword weapon.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createSword(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Pickaxe tool.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createPickaxe(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Axe tool.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createAxe(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Shovel tool.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createShovel(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Hoe tool.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createHoe(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Helmet armor.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createHelmet(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Chestplate armor.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createChestplate(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Leggings armor.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createLeggings(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Boots armor.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createBoots(String id, Consumer<IItemBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Ranged weapon item.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Content.BUILDER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder createRangedItem(String id, Consumer<IItemBuilder> builderConsumer);


    @LuaDoc(description = "Creates and registers a custom Block.", params = {LuaDocFragments.Common.ID_STRING, "builder: function(block)"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder createBlock(String id, Consumer<IBlockBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Fluid.", params = {LuaDocFragments.Common.ID_STRING, "builder: function(fluid)"}, returnType = "IFluidBuilder")
    IFluidBuilder createFluid(String id, Consumer<IFluidBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Creative Mode Tab.", params = {LuaDocFragments.Common.ID_STRING, "builder: function(tab)"}, returnType = "ICreativeTabBuilder")
    ICreativeTabBuilder createTab(String id, Consumer<ICreativeTabBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers an explicit custom Armor Material.", params = {LuaDocFragments.Common.ID_STRING, "builder: function(mat)"}, returnType = "IArmorMaterialBuilder")
    IArmorMaterialBuilder createArmorMaterial(String id, Consumer<IArmorMaterialBuilder> builderConsumer);

    @LuaDoc(description = "Creates and registers a custom Stairs block.", params = {LuaDocFragments.Common.ID_STRING, "baseBlockId: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder createStairs(String id, String baseBlockId);

    @LuaDoc(description = "Creates and registers a custom Slab block.", params = {LuaDocFragments.Common.ID_STRING, "baseBlockId: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder createSlab(String id, String baseBlockId);

    @LuaDoc(description = "Creates and registers a custom Wall block.", params = {LuaDocFragments.Common.ID_STRING, "baseBlockId: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder createWall(String id, String baseBlockId);

    @LuaDoc(description = "Creates and registers a custom Entity Type.", params = {LuaDocFragments.Common.ID_STRING, "builder: function(entity)"}, returnType = "IEntityBuilder")
    IEntityBuilder createEntity(String id, Consumer<IEntityBuilder> builderConsumer);

    Collection<IItemBuilder> getRegisteredItems();

    Collection<IBlockBuilder> getRegisteredBlocks();
    Collection<IFluidBuilder> getRegisteredFluids();
    Collection<ICreativeTabBuilder> getRegisteredTabs();
    Collection<IArmorMaterialBuilder> getRegisteredArmorMaterials();
    Collection<IEntityBuilder> getRegisteredEntities();
    IArmorMaterialBuilder getArmorMaterial(String id);
}




