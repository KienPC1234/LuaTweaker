package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import java.util.function.BiConsumer;

@LuaDoc(description = "Builder interface for defining custom Block properties, models, sounds, mining levels, and action handlers.")
public interface IBlockBuilder {
    @LuaDoc(description = "Sets the hardness of the block.", params = {"hardness: number"}, returnType = "IBlockBuilder")
    IBlockBuilder hardness(float hardness);

    @LuaDoc(description = "Sets the blast resistance of the block.", params = {"resistance: number"}, returnType = "IBlockBuilder")
    IBlockBuilder resistance(float resistance);

    @LuaDoc(description = "Sets the emission light level (0-15).", params = {"level: integer"}, returnType = "IBlockBuilder")
    IBlockBuilder lightLevel(int level);

    @LuaDoc(description = "Sets the sound type ('STONE', 'WOOD', 'GRAVEL', 'METAL', 'GLASS', 'GRASS', 'SAND', 'SNOW', 'WOOL', 'ANVIL', 'NETHERITE').", params = {"soundType: string"}, returnType = "IBlockBuilder")
    IBlockBuilder soundType(String soundType);

    @LuaDoc(description = "Sets whether the block requires a tool to drop items.", params = {"requires: boolean"}, returnType = "IBlockBuilder")
    IBlockBuilder requiresTool(boolean requires);

    @LuaDoc(description = "Sets the tool type required to mine this block ('PICKAXE', 'AXE', 'SHOVEL', 'HOE').", params = {"toolType: string"}, returnType = "IBlockBuilder")
    IBlockBuilder mineableWith(String toolType);

    @LuaDoc(description = "Sets the minimum tool tier level required to drop items (1='STONE', 2='IRON', 3='DIAMOND', 4='NETHERITE').", params = {"level: string|integer"}, returnType = "IBlockBuilder")
    IBlockBuilder miningLevel(int level);
    IBlockBuilder miningLevel(String levelName);

    @LuaDoc(description = "Sets the friction / slipperiness of the block (0.6 default, 0.98 ice).", params = {"friction: number"}, returnType = "IBlockBuilder")
    IBlockBuilder friction(float friction);

    @LuaDoc(description = "Sets custom block model location (e.g. 'luatweaker:block/wood_crate').", params = {"modelPath: string"}, returnType = "IBlockBuilder")
    IBlockBuilder model(String modelPath);

    @LuaDoc(description = "Sets custom texture path.", params = {"texturePath: string"}, returnType = "IBlockBuilder")
    IBlockBuilder texture(String texturePath);

    @LuaDoc(description = "Adds a tag to this block (e.g. 'c:storage_blocks/ruby' or 'minecraft:beacon_base_blocks').", params = {"tagId: string"}, returnType = "IBlockBuilder")
    IBlockBuilder tag(String tagId);

    @LuaDoc(description = "Sets the target creative mode tab for this block (e.g. 'luatweaker:ruby_tab' or 'minecraft:building_blocks').", params = {"tabId: string"}, returnType = "IBlockBuilder")
    IBlockBuilder creativeTab(String tabId);

    @LuaDoc(description = "Registers a right-click interaction callback.", params = {"handler: function(player, blockState)"}, returnType = "IBlockBuilder")
    IBlockBuilder onRightClick(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Configures inline item drops when this block is mined.", params = {"itemId: string", "minCount: integer", "maxCount: integer"}, returnType = "IBlockBuilder")
    IBlockBuilder drop(String itemId, int minCount, int maxCount);

    @LuaDoc(description = "Configures experience dropped when this block is mined.", params = {"minExp: integer", "maxExp: integer"}, returnType = "IBlockBuilder")
    IBlockBuilder dropExperience(int minExp, int maxExp);

    String getId();
    float getHardness();
    float getResistance();
    int getLightLevel();
    String getSoundType();
    boolean getRequiresTool();
    String getMineableWith();
    int getMiningLevel();
    float getFriction();
    String getModel();
    String getTexture();
    java.util.List<String> getTags();
    String getCreativeTab();
    BiConsumer<Object, Object> getRightClickHandler();
    String getDropItemId();
    int getMinDropCount();
    int getMaxDropCount();
    int getMinExp();
    int getMaxExp();
}



