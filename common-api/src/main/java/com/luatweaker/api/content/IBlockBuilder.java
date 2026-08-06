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

    @LuaDoc(description = "Turns this block into an inventory container opened on right-click. dropMode: 'packed' (drop the block with its NBT, contents never spill), 'spill' (drop contents like a chest), 'none' (no drops).", params = {"rows: integer (1-6, 6 columns each)", "cols: integer", "dropMode: string"}, returnType = "IBlockBuilder")
    IBlockBuilder container(int rows, int cols, String dropMode);

    @LuaDoc(description = "Sets how far (in blocks) a player may stand from the container to open/use it (default 8.0 = vanilla reach).", params = {"distance: number"}, returnType = "IBlockBuilder")
    IBlockBuilder containerUseDistance(double distance);

    @LuaDoc(description = "Registers an item filter for container blocks: handler(itemId: string, count: integer) -> boolean. Return true to allow the item into a slot, false to reject it (item never enters the container, 'ContainerItemRejected' event fires).", params = {"handler: function(itemId, count)"}, returnType = "IBlockBuilder")
    IBlockBuilder itemFilter(java.util.function.BiFunction<Object, Object, Boolean> handler);

    @LuaDoc(description = "Sets a custom GUI background texture for this container block (e.g. 'ruby_mod:textures/gui/my_crate.png', 176x190 region, slots are drawn on top automatically).", params = {"texturePath: string"}, returnType = "IBlockBuilder")
    IBlockBuilder containerTexture(String texturePath);

    @LuaDoc(description = "Sets the display title shown above the container GUI (defaults to the block name).", params = {"title: string"}, returnType = "IBlockBuilder")
    IBlockBuilder containerTitle(String title);

    @LuaDoc(description = "Sets the display name of the block.", params = {"name: string"}, returnType = "IBlockBuilder")
    IBlockBuilder displayName(String name);

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
    boolean isContainer();
    int getContainerRows();
    int getContainerCols();
    String getContainerDropMode();
    double getContainerUseDistance();
    java.util.function.BiFunction<Object, Object, Boolean> getItemFilter();
    String getContainerTexture();
    String getContainerTitle();
    String getDisplayName();
}



