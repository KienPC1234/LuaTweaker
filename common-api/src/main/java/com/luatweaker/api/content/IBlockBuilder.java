package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import com.luatweaker.integration.geo.GeoModelSpec;
import java.util.function.BiConsumer;
import java.util.Locale;

@LuaDoc(description = "Builder interface for defining custom Block properties, models, sounds, mining levels, and action handlers.")
public interface IBlockBuilder {
    @LuaDoc(description = "Sets the hardness of the block.", params = {"hardness: number"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder hardness(float hardness);

    @LuaDoc(description = "Sets the blast resistance of the block.", params = {"resistance: number"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder resistance(float resistance);

    @LuaDoc(description = "Sets the emission light level (0-15).", params = {"level: integer"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder lightLevel(int level);

    @LuaDoc(description = "Sets the sound type ('STONE', 'WOOD', 'GRAVEL', 'METAL', 'GLASS', 'GRASS', 'SAND', 'SNOW', 'WOOL', 'ANVIL', 'NETHERITE').", params = {"soundType: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder soundType(String soundType);

    @LuaDoc(description = "Sets whether the block requires a tool to drop items.", params = {"requires: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder requiresTool(boolean requires);

    @LuaDoc(description = "Sets the tool type required to mine this block ('PICKAXE', 'AXE', 'SHOVEL', 'HOE').", params = {"toolType: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder mineableWith(String toolType);

    @LuaDoc(description = "Sets the minimum tool tier level required to drop items (1='STONE', 2='IRON', 3='DIAMOND', 4='NETHERITE').", params = {"level: string|integer"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder miningLevel(int level);
    IBlockBuilder miningLevel(String levelName);

    @LuaDoc(description = "Sets the friction / slipperiness of the block (0.6 default, 0.98 ice).", params = {"friction: number"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder friction(float friction);

    @LuaDoc(description = "Sets the map color used on vanilla maps. Accepts a color name ('STONE', 'DIRT', 'WOOD', 'GRASS', 'METAL', 'GOLD', 'DIAMOND', 'COLOR_RED', 'TERRACOTTA_ORANGE', ...) or a map-color id 0-61.", params = {"colorName: string|integer"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder mapColor(String colorName);

    @LuaDoc(description = "Sets how much entities bounce when jumping on this block (1.0 default, 1.5 = slime, 0.75 = honey).", params = {"jumpFactor: number"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder jumpFactor(float jumpFactor);

    @LuaDoc(description = "Sets how much this block slows walking entities (1.0 default, 0.6 = soul sand, 0.4 = slime).", params = {"speedFactor: number"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder speedFactor(float speedFactor);

    @LuaDoc(description = "Makes the block non-collidable (entities walk through it, like a torch or air).", params = {"noCollision: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder noCollision(boolean noCollision);

    @LuaDoc(description = "Disables block face culling (faces are always rendered, like glass or stairs).", params = {"noOcclusion: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder noOcclusion(boolean noOcclusion);

    @LuaDoc(description = "Sets the piston push reaction: 'NORMAL' (movable), 'DESTROY' (destroyed), 'BLOCK' (immovable), 'IGNORE' (ignored by pistons), 'PUSH_ONLY' (pushed but never pulled).", params = {"pushReaction: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder pushReaction(String pushReaction);

    @LuaDoc(description = "Makes the block replaceable by other blocks (like grass or water).", params = {"replaceable: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder replaceable(boolean replaceable);

    @LuaDoc(description = "Makes the block able to be set on fire by lava (flammable blocks).", params = {"ignitedByLava: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder ignitedByLava(boolean ignitedByLava);

    @LuaDoc(description = "Marks the block as a liquid (renders in a fluid-like way on maps).", params = {"liquid: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder liquid(boolean liquid);

    @LuaDoc(description = "Sets the random model offset: 'NONE', 'XZ' (flowers/grass), 'XYZ' (leaves, seaweed).", params = {"offsetType: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder offsetType(String offsetType);

    @LuaDoc(description = "Sets whether the block conducts redstone (true = default for full blocks, false = like glass).", params = {"redstoneConductor: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder redstoneConductor(boolean redstoneConductor);

    /**
     * Selects the block shape. The platform registers a real {@code StairBlock}/{@code SlabBlock}/
     * {@code WallBlock} (collision, placement, blockstate) and generates the matching assets.
     * IDs ending in {@code _stairs}/{@code _slab}/{@code _wall} still get the shape automatically,
     * so this is only needed for custom names.
     *
     * @param shape {@code "STAIRS"}, {@code "SLAB"}, {@code "WALL"} or {@code "BLOCK"}
     */
    @LuaDoc(description = "Selects the block shape: 'BLOCK' (default), 'STAIRS', 'SLAB' or 'WALL'.", params = {"shape: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder shape(String shape);

    /**
     * Base block whose texture/model feeds the generated shape assets, and whose default state
     * backs stairs. Falls back to the {@code <id>_block} naming convention when unset.
     */
    @LuaDoc(description = "Sets the base block for stairs/slab/wall textures and stair base state (e.g. 'luatweaker:ruby_block').", params = {"baseBlockId: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder baseBlock(String baseBlockId);

    /**
     * Shape after applying the id convention: an explicit {@link #shape(String)} wins, otherwise
     * ids ending in {@code _stairs}/{@code _slab}/{@code _wall} keep their historic behavior.
     *
     * @return {@code "BLOCK"}, {@code "STAIRS"}, {@code "SLAB"} or {@code "WALL"}
     */
    default String getEffectiveShape() {
        String declared = getShape();
        if (declared != null && !declared.isBlank() && !"BLOCK".equalsIgnoreCase(declared)) {
            return declared.toUpperCase(Locale.ROOT);
        }
        return shapeFromId(getId() == null ? "" : getId().toLowerCase(Locale.ROOT));
    }

    /**
     * Infers {@code "STAIRS"}, {@code "SLAB"} or {@code "WALL"} from id
     * suffixes, defaulting to {@code "BLOCK"}.
     */
    private static String shapeFromId(String id) {
        if (id.endsWith("_stairs") || id.contains("stairs")) {
            return "STAIRS";
        }
        if (id.endsWith("_slab") || id.contains("slab")) {
            return "SLAB";
        }
        if (id.endsWith("_wall") || id.contains("wall")) {
            return "WALL";
        }
        return "BLOCK";
    }

    @LuaDoc(description = "Sets custom block model location (e.g. 'luatweaker:block/wood_crate').", params = {"modelPath: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder model(String modelPath);

    @LuaDoc(description = "Sets custom texture path.", params = {LuaDocFragments.Content.TEXTURE_PATH}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder texture(String texturePath);

    @LuaDoc(description = "Adds a tag to this block (e.g. 'c:storage_blocks/ruby' or 'minecraft:beacon_base_blocks').", params = {"tagId: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder tag(String tagId);

    @LuaDoc(description = "Sets the target creative mode tab for this block (e.g. 'luatweaker:ruby_tab' or 'minecraft:building_blocks').", params = {"tabId: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder creativeTab(String tabId);

    @LuaDoc(description = "Registers a right-click interaction callback.", params = {"handler: function(player, blockState)"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder onRightClick(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Configures inline item drops when this block is mined.", params = {"itemId: string", "minCount: integer", "maxCount: integer"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder drop(String itemId, int minCount, int maxCount);

    @LuaDoc(description = "Configures experience dropped when this block is mined.", params = {"minExp: integer", "maxExp: integer"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder dropExperience(int minExp, int maxExp);

    @LuaDoc(description = "Turns this block into an inventory container opened on right-click. dropMode: 'packed' (drop the block with its NBT, contents never spill), 'spill' (drop contents like a chest), 'none' (no drops).", params = {"rows: integer (1-6, 6 columns each)", "cols: integer", "dropMode: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder container(int rows, int cols, String dropMode);

    @LuaDoc(description = "Sets how far (in blocks) a player may stand from the container to open/use it (default 8.0 = vanilla reach).", params = {"distance: number"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder containerUseDistance(double distance);

    @LuaDoc(description = "Registers an item filter for container blocks: handler(itemId: string, count: integer) -> boolean. Return true to allow the item into a slot, false to reject it (item never enters the container, 'ContainerItemRejected' event fires).", params = {"handler: function(itemId, count)"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder itemFilter(java.util.function.BiFunction<Object, Object, Boolean> handler);

    @LuaDoc(description = "Sets a custom GUI background texture for this container block (e.g. 'luatweaker_test:textures/gui/my_crate.png'). The texture is a 176 x (114 + rows*18) panel drawn from its top-left corner, matching the vanilla chest layout; slot cells are drawn on top automatically.", params = {LuaDocFragments.Content.TEXTURE_PATH}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder containerTexture(String texturePath);

    @LuaDoc(description = "Sets the display title shown above the container GUI (defaults to the block name).", params = {"title: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder containerTitle(String title);

    @LuaDoc(description = "Moves a container slot to an absolute GUI position (x, y in the 176-wide panel). Useful for custom layouts; slots without an override keep the automatic centered grid.", params = {"slotIndex: integer (0-based)", LuaDocFragments.Geometry.X_INTEGER, LuaDocFragments.Geometry.Y_INTEGER}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder slotPosition(int slotIndex, int x, int y);

    @LuaDoc(description = "Skips the automatic grid: only slots with an explicit :SlotPosition are added to the menu (machine GUIs with a few hand-placed slots). Storage indices, panel size and player inventory are unchanged; unpositioned indices stay accessible to Lua but have no clickable cell.", params = {}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder manualSlots();

    @LuaDoc(description = "Sets a custom slot cell texture for every slot of this container (e.g. 'luatweaker_test:textures/gui/my_slot.png', 18x18 cell).", params = {LuaDocFragments.Content.TEXTURE_PATH}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder slotTexture(String texturePath);

    @LuaDoc(description = "Locks a container slot so items can neither be placed into it nor taken from it (read-only storage).", params = {"slotIndex: integer (0-based)", "locked: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder lockSlot(int slotIndex, boolean locked);

    @LuaDoc(description = "Adds an FE (Forge Energy) buffer to this block and gives it a block entity: capacity in FE, maxReceive and maxExtract FE per tick. Exposed to other mods through the energy capability on every face and to Lua via World:SetBlockEntityData/GetBlockEntityData (key 'Energy') or the World:GetEnergy/InsertEnergy/ExtractEnergy capability primitives.", params = {"capacity: integer", "maxReceive: integer", "maxExtract: integer"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder energyStorage(int capacity, int maxReceive, int maxExtract);

    @LuaDoc(description = "Adds a single fluid tank (in millibuckets) to this block and gives it a block entity. Exposed to other mods through the fluid capability on every face and to Lua via World:SetBlockEntityData/GetBlockEntityData (keys 'FluidId', 'FluidAmount') or World:GetFluid/FillFluid/DrainFluid.", params = {"capacityMB: integer"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder fluidStorage(int capacityMB);

    @LuaDoc(description = "Adds a bar element to the container GUI. source: 'energy' (FE fill), 'fluid' (tank fill) or 'progress' (Lua progress 0..1). Color is ARGB, e.g. 0xFF00E676; 0 = source default.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Geometry.X_INTEGER, LuaDocFragments.Geometry.Y_INTEGER, LuaDocFragments.Geometry.WIDTH_INTEGER, LuaDocFragments.Geometry.HEIGHT_INTEGER, "source: string", LuaDocFragments.Geometry.COLOR_INTEGER}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder guiBar(String id, int x, int y, int width, int height, String source, int color);

    @LuaDoc(description = "Adds a bar element pinned to a panel anchor (topleft/top/topright/left/center/right/bottomleft/bottom/bottomright, default topleft); x/y become offsets from the anchor point.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Geometry.X_INTEGER, LuaDocFragments.Geometry.Y_INTEGER, LuaDocFragments.Geometry.WIDTH_INTEGER, LuaDocFragments.Geometry.HEIGHT_INTEGER, "source: string", LuaDocFragments.Geometry.COLOR_INTEGER, "anchor: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder guiBar(String id, int x, int y, int width, int height, String source, int color, String anchor);

    @LuaDoc(description = "Adds a custom proprietary resource bar (e.g. Heat, Pressure, Mana, Radiation) synced from custom BlockEntity data keys.",
            params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Geometry.X_INTEGER, LuaDocFragments.Geometry.Y_INTEGER, LuaDocFragments.Geometry.WIDTH_INTEGER, LuaDocFragments.Geometry.HEIGHT_INTEGER, "currentKey: string", "maxKey: string", LuaDocFragments.Geometry.COLOR_INTEGER, "tooltip: string"},
            returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder customBar(String id, int x, int y, int width, int height, String currentKey, String maxKey, int color, @org.jetbrains.annotations.Nullable String tooltip);

    @LuaDoc(description = "Adds a custom bar pinned to a panel anchor (topleft/top/topright/left/center/right/bottomleft/bottom/bottomright, default topleft); x/y become offsets from the anchor point.",
            params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Geometry.X_INTEGER, LuaDocFragments.Geometry.Y_INTEGER, LuaDocFragments.Geometry.WIDTH_INTEGER, LuaDocFragments.Geometry.HEIGHT_INTEGER, "currentKey: string", "maxKey: string", LuaDocFragments.Geometry.COLOR_INTEGER, "tooltip: string", "anchor: string"},
            returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder customBar(String id, int x, int y, int width, int height, String currentKey, String maxKey, int color, @org.jetbrains.annotations.Nullable String tooltip, String anchor);

    @LuaDoc(description = "Adds a boolean block-state pair (e.g. running/off). The blockstate JSON and both cube-all models are generated automatically from the two textures - no JSON files needed. Toggle it from Lua: World:SetBlockState(x, y, z, 'ns:id', { running = true }).", params = {"property: string (e.g. 'running')", "offTexture: string", "onTexture: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder booleanState(String property, String offTexture, String onTexture);

    @LuaDoc(description = "Turns the block into a connectable pipe: 6 connection properties (north/east/south/west/up/down) are added, connections are computed automatically when neighbors of the same block type are placed/removed, and the full 64-variant blockstate plus pipe models are generated automatically. The pipe texture is taken from :Texture(id). Transport logic is NOT built in - write it yourself with :OnTick + the World block-entity APIs.", params = {"connections: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder connectionState(boolean connections);

    @LuaDoc(description = "Registers a per-tick behavior handler for this block (machines, pipes, generators...). The handler receives a table {X, Y, Z, Energy, EnergyCapacity, FluidId, FluidAmount, FluidCapacity, Progress} of the block entity every tick and can read/write it via World:GetBlockEntityData / World:SetBlockEntityData, move resources through other blocks with World:InsertEnergy/ExtractEnergy, World:InsertItem/ExtractItem, World:FillFluid/DrainFluid, eject items with World:EjectContainerItem and toggle block states with World:SetBlockState. Only blocks with a handler tick, so no performance cost otherwise.", params = {"handler: function(data) -> void"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder onTick(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Sets the display name of the block.", params = {"name: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder displayName(String name);

    @LuaDoc(description = "Defines a custom bounding box (0-16 voxel coords). Can be chained multiple times to create compound shapes.", params = {"minX: number", "minY: number", "minZ: number", "maxX: number", "maxY: number", "maxZ: number"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);

    @LuaDoc(description = "Sets block facing rotation behavior upon placement: 'NONE' (default), 'HORIZONTAL' (faces player horizontally), or 'DIRECTIONAL'/'6WAY' (faces 6 directions).", params = {"facing: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder facing(String facing);

    @LuaDoc(description = "Registers a client-side BlockEntityRenderer (BER) callback for dynamic rendering and animation.", params = {"handler: function(renderer, data)"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder onRender(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Adds an interactive button widget to this container screen.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Geometry.X_INTEGER, LuaDocFragments.Geometry.Y_INTEGER, LuaDocFragments.Geometry.WIDTH_INTEGER, LuaDocFragments.Geometry.HEIGHT_INTEGER, "label: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder button(String id, int x, int y, int width, int height, String label);

    @LuaDoc(description = "Adds an interactive button pinned to a panel anchor (topleft/top/topright/left/center/right/bottomleft/bottom/bottomright, default topleft); x/y become offsets from the anchor point.", params = {LuaDocFragments.Common.ID_STRING, LuaDocFragments.Geometry.X_INTEGER, LuaDocFragments.Geometry.Y_INTEGER, LuaDocFragments.Geometry.WIDTH_INTEGER, LuaDocFragments.Geometry.HEIGHT_INTEGER, "label: string", "anchor: string"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder button(String id, int x, int y, int width, int height, String label, String anchor);

    @LuaDoc(description = "Adds a dynamic text label widget to this container screen (supports '{Key}' placeholders from synced values and \\n line breaks).", params = {LuaDocFragments.Geometry.X_INTEGER, LuaDocFragments.Geometry.Y_INTEGER, "textOrVar: string", LuaDocFragments.Geometry.COLOR_INTEGER}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder label(int x, int y, String textOrVar, int color);

    @LuaDoc(description = "Adds a text label with alignment (left/center/right against the 176-wide panel), scale (1.0 = vanilla, 0.5..3.0) and optional drop shadow for readability.", params = {LuaDocFragments.Geometry.X_INTEGER, LuaDocFragments.Geometry.Y_INTEGER, "textOrVar: string", LuaDocFragments.Geometry.COLOR_INTEGER, "align: string", "scale: number", "shadow: boolean"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder label(int x, int y, String textOrVar, int color, String align, double scale, boolean shadow);

    @LuaDoc(description = "Registers a callback invoked on the server when a player clicks a container button.", params = {"handler: function(pos, buttonId, player)"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder onButtonClick(BiConsumer<Object, String> handler);

    @LuaDoc(description = "Registers a callback invoked when a neighbor block changes or redstone signal updates.", params = {"handler: function(pos, neighborPos)"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder onNeighborChanged(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Sets the GeckoLib skeletal model and animation specs for this block.", params = {"modelPath: string", LuaDocFragments.Content.TEXTURE_PATH, "animationPath: string", "defaultAnimation: string?"}, returnType = LuaDocFragments.Builder.I_BLOCK_BUILDER)
    IBlockBuilder geoModel(String modelPath, String texturePath, String animationPath, @org.jetbrains.annotations.Nullable String defaultAnimation);

    default IBlockBuilder geoModel(String modelPath, String texturePath, String animationPath) {
        return geoModel(modelPath, texturePath, animationPath, null);
    }

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
    String getShape();
    String getBaseBlockId();
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
    boolean isManualSlots();
    int getEnergyCapacity();
    int getEnergyMaxReceive();
    int getEnergyMaxExtract();
    int getFluidCapacity();
    java.util.List<MachineBarSpec> getGuiBars();
    BooleanStateSpec getBooleanState();
    boolean isConnectionState();
    BiConsumer<Object, Object> getTickHandler();
    String getDisplayName();
    java.util.List<BoxSpec> getBoxes();
    String getFacing();
    BiConsumer<Object, Object> getRenderHandler();
    java.util.List<MachineButtonSpec> getButtons();
    java.util.List<MachineLabelSpec> getLabels();
    BiConsumer<Object, String> getButtonClickHandler();
    BiConsumer<Object, Object> getNeighborChangedHandler();
    @org.jetbrains.annotations.Nullable
    GeoModelSpec getGeoModel();
}



