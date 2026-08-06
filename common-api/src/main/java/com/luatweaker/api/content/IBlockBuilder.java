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

    @LuaDoc(description = "Sets the map color used on vanilla maps. Accepts a color name ('STONE', 'DIRT', 'WOOD', 'GRASS', 'METAL', 'GOLD', 'DIAMOND', 'COLOR_RED', 'TERRACOTTA_ORANGE', ...) or a map-color id 0-61.", params = {"colorName: string|integer"}, returnType = "IBlockBuilder")
    IBlockBuilder mapColor(String colorName);

    @LuaDoc(description = "Sets how much entities bounce when jumping on this block (1.0 default, 1.5 = slime, 0.75 = honey).", params = {"jumpFactor: number"}, returnType = "IBlockBuilder")
    IBlockBuilder jumpFactor(float jumpFactor);

    @LuaDoc(description = "Sets how much this block slows walking entities (1.0 default, 0.6 = soul sand, 0.4 = slime).", params = {"speedFactor: number"}, returnType = "IBlockBuilder")
    IBlockBuilder speedFactor(float speedFactor);

    @LuaDoc(description = "Makes the block non-collidable (entities walk through it, like a torch or air).", params = {"noCollision: boolean"}, returnType = "IBlockBuilder")
    IBlockBuilder noCollision(boolean noCollision);

    @LuaDoc(description = "Disables block face culling (faces are always rendered, like glass or stairs).", params = {"noOcclusion: boolean"}, returnType = "IBlockBuilder")
    IBlockBuilder noOcclusion(boolean noOcclusion);

    @LuaDoc(description = "Sets the piston push reaction: 'NORMAL' (movable), 'DESTROY' (destroyed), 'BLOCK' (immovable), 'IGNORE' (ignored by pistons), 'PUSH_ONLY' (pushed but never pulled).", params = {"pushReaction: string"}, returnType = "IBlockBuilder")
    IBlockBuilder pushReaction(String pushReaction);

    @LuaDoc(description = "Makes the block replaceable by other blocks (like grass or water).", params = {"replaceable: boolean"}, returnType = "IBlockBuilder")
    IBlockBuilder replaceable(boolean replaceable);

    @LuaDoc(description = "Makes the block able to be set on fire by lava (flammable blocks).", params = {"ignitedByLava: boolean"}, returnType = "IBlockBuilder")
    IBlockBuilder ignitedByLava(boolean ignitedByLava);

    @LuaDoc(description = "Marks the block as a liquid (renders in a fluid-like way on maps).", params = {"liquid: boolean"}, returnType = "IBlockBuilder")
    IBlockBuilder liquid(boolean liquid);

    @LuaDoc(description = "Sets the random model offset: 'NONE', 'XZ' (flowers/grass), 'XYZ' (leaves, seaweed).", params = {"offsetType: string"}, returnType = "IBlockBuilder")
    IBlockBuilder offsetType(String offsetType);

    @LuaDoc(description = "Sets whether the block conducts redstone (true = default for full blocks, false = like glass).", params = {"redstoneConductor: boolean"}, returnType = "IBlockBuilder")
    IBlockBuilder redstoneConductor(boolean redstoneConductor);

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

    @LuaDoc(description = "Sets a custom GUI background texture for this container block (e.g. 'ruby_mod:textures/gui/my_crate.png'). The texture is a 176 x (114 + rows*18) panel drawn from its top-left corner, matching the vanilla chest layout; slot cells are drawn on top automatically.", params = {"texturePath: string"}, returnType = "IBlockBuilder")
    IBlockBuilder containerTexture(String texturePath);

    @LuaDoc(description = "Sets the display title shown above the container GUI (defaults to the block name).", params = {"title: string"}, returnType = "IBlockBuilder")
    IBlockBuilder containerTitle(String title);

    @LuaDoc(description = "Moves a container slot to an absolute GUI position (x, y in the 176-wide panel). Useful for custom layouts; slots without an override keep the automatic centered grid.", params = {"slotIndex: integer (0-based)", "x: integer", "y: integer"}, returnType = "IBlockBuilder")
    IBlockBuilder slotPosition(int slotIndex, int x, int y);

    @LuaDoc(description = "Sets a custom slot cell texture for every slot of this container (e.g. 'ruby_mod:textures/gui/my_slot.png', 18x18 cell).", params = {"texturePath: string"}, returnType = "IBlockBuilder")
    IBlockBuilder slotTexture(String texturePath);

    @LuaDoc(description = "Locks a container slot so items can neither be placed into it nor taken from it (read-only storage).", params = {"slotIndex: integer (0-based)", "locked: boolean"}, returnType = "IBlockBuilder")
    IBlockBuilder lockSlot(int slotIndex, boolean locked);

    @LuaDoc(description = "Adds an FE (Forge Energy) buffer to this container block: capacity in FE, maxReceive and maxExtract FE per tick. Exposed to other mods through the energy capability and to Lua via World:SetBlockEntityData/GetBlockEntityData (key 'Energy').", params = {"capacity: integer", "maxReceive: integer", "maxExtract: integer"}, returnType = "IBlockBuilder")
    IBlockBuilder energyStorage(int capacity, int maxReceive, int maxExtract);

    @LuaDoc(description = "Adds a fluid tank (in millibuckets) to this container block. Exposed to other mods through the fluid capability and to Lua via World:SetBlockEntityData/GetBlockEntityData (keys 'FluidId', 'FluidAmount').", params = {"capacityMB: integer"}, returnType = "IBlockBuilder")
    IBlockBuilder fluidStorage(int capacityMB);

    @LuaDoc(description = "Adds a bar element to the container GUI. source: 'energy' (FE fill), 'fluid' (tank fill) or 'progress' (Lua progress 0..1). Color is ARGB, e.g. 0xFF00E676; 0 = source default.", params = {"id: string", "x: integer", "y: integer", "width: integer", "height: integer", "source: string", "color: integer"}, returnType = "IBlockBuilder")
    IBlockBuilder guiBar(String id, int x, int y, int width, int height, String source, int color);

    @LuaDoc(description = "Adds a boolean block-state pair (e.g. running/off). The blockstate JSON and both cube-all models are generated automatically from the two textures - no JSON files needed. Toggle it from Lua: World:SetBlockState(x, y, z, 'ns:id', { running = true }).", params = {"property: string (e.g. 'running')", "offTexture: string", "onTexture: string"}, returnType = "IBlockBuilder")
    IBlockBuilder booleanState(String property, String offTexture, String onTexture);

    @LuaDoc(description = "Turns the block into a connectable pipe: 6 connection properties (north/east/south/west/up/down) are added, connections are computed automatically when neighbors of the same block type are placed/removed, and the full 64-variant blockstate plus pipe models are generated automatically. The pipe texture is taken from :Texture(id). Transport logic is NOT built in - write it yourself with :OnTick + the World block-entity APIs.", params = {"connections: boolean"}, returnType = "IBlockBuilder")
    IBlockBuilder connectionState(boolean connections);

    @LuaDoc(description = "Registers a per-tick behavior handler for this block (machines, pipes, generators...). The handler receives a table {X, Y, Z, Energy, EnergyCapacity, FluidId, FluidAmount, FluidCapacity, Progress} of the block entity every tick and can read/write it via World:GetBlockEntityData / World:SetBlockEntityData, eject items with World:EjectContainerItem and toggle block states with World:SetBlockState. Only blocks with a handler tick, so no performance cost otherwise.", params = {"handler: function(data) -> void"}, returnType = "IBlockBuilder")
    IBlockBuilder onTick(java.util.function.BiConsumer<Object, Object> handler);

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
    String getMapColor();
    float getJumpFactor();
    float getSpeedFactor();
    boolean isNoCollision();
    boolean isNoOcclusion();
    String getPushReaction();
    boolean isReplaceable();
    boolean isIgnitedByLava();
    boolean isLiquid();
    String getOffsetType();
    Boolean getRedstoneConductor();
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
    java.util.Map<Integer, int[]> getSlotPositions();
    java.util.Set<Integer> getLockedSlots();
    String getSlotTexture();
    int getEnergyCapacity();
    int getEnergyMaxReceive();
    int getEnergyMaxExtract();
    int getFluidCapacity();
    java.util.List<MachineBarSpec> getGuiBars();
    BooleanStateSpec getBooleanState();
    boolean isConnectionState();
    java.util.function.BiConsumer<Object, Object> getTickHandler();
    String getDisplayName();
}



