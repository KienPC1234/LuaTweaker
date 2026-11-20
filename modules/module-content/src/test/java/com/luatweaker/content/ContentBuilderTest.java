package com.luatweaker.content;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IItemBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class ContentBuilderTest {

    private ILuaEngine engine;
    private ContentServiceImpl contentService;
    private StorageServiceImpl storageService;
    private DatapackServiceImpl datapackService;

    @BeforeEach
    public void setUp() {
        engine = LuaEngineFactory.create(false);
        contentService = new ContentServiceImpl();
        storageService = new StorageServiceImpl(new File("build/tmp/test_storage.json"));
        datapackService = new DatapackServiceImpl();

        ContentLuaBinding.registerBindings(engine, contentService, storageService, datapackService);
    }

    private File createTempScript(String content) throws IOException {
        File file = File.createTempFile("test_script_", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), content);
        return file;
    }

    @Test
    public void tagHelpersRegisterFilesWithDotAndCallSyntax() {
        engine.executeString(
            "tag.item('c:gems', {'luatweaker:custom_ruby'})\n" +
            "tag('block', 'minecraft:beacon_base_blocks', {'luatweaker:ruby_block'})",
            "tag_helpers"
        );
        assertNull(engine.getAndClearLastExecutionError());

        var files = datapackService.getVirtualFiles();
        assertTrue(files.containsKey("data/c/tags/item/gems.json"),
            "tag.item dot call must register the item tag: " + files.keySet());
        assertTrue(files.containsKey("data/minecraft/tags/block/beacon_base_blocks.json"),
            "tag(...) call form must register the block tag: " + files.keySet());
        assertTrue(files.get("data/c/tags/item/gems.json").contains("luatweaker:custom_ruby"));
        assertTrue(files.get("data/minecraft/tags/block/beacon_base_blocks.json")
                .contains("luatweaker:ruby_block"));
    }

    @Test
    public void createStairsRecordsShapeAndBaseBlock() {
        engine.executeStringOrThrow(
                "startup:createStairs('luatweaker:marble_step', 'luatweaker:marble_block')",
                "stairs_shape");

        IBlockBuilder stairs = contentService.getRegisteredBlocks().stream()
                .filter(b -> "luatweaker:marble_step".equals(b.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("createStairs must register the block"));
        assertEquals("STAIRS", stairs.getEffectiveShape(),
                "an explicit createStairs id must not need a _stairs suffix");
        assertEquals("luatweaker:marble_block", stairs.getBaseBlockId(),
                "the base block must reach the registrar/assets, not be dropped");
    }

    @Test
    public void shapeConventionStillAppliesWithoutExplicitShape() {
        contentService.createBlock("luatweaker:rune_wall", b -> { });
        IBlockBuilder wall = contentService.getRegisteredBlocks().stream()
                .filter(b -> "luatweaker:rune_wall".equals(b.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("WALL", wall.getEffectiveShape(),
                "legacy _wall ids must keep their shape");
        assertEquals("BLOCK", contentService.createBlock("luatweaker:plain", b -> { }).getEffectiveShape());
    }

    @Test
    public void createStairsWithoutBaseBlockLeavesTheConventionInCharge() {
        engine.executeStringOrThrow("startup:createStairs('luatweaker:ruby_stairs')", "stairs_no_base");
        IBlockBuilder stairs = contentService.getRegisteredBlocks().stream()
                .filter(b -> "luatweaker:ruby_stairs".equals(b.getId()))
                .findFirst()
                .orElseThrow();
        assertNull(stairs.getBaseBlockId(),
                "omitting the base must fall back to <id>_block textures, not repeat the stair id");
    }

    @Test
    public void createArmorMaterialKeepsTheDeclaredStats() {
        engine.executeStringOrThrow(
                "startup:createArmorMaterial('test_ruby', function(mat)\n" +
                "    mat:layer('luatweaker:test_ruby')\n" +
                "       :equipSound('minecraft:item.armor.equip_iron')\n" +
                "       :defense('helmet', 5)\n" +
                "       :toughness(4.5)\n" +
                "       :knockbackResistance(0.2)\n" +
                "       :enchantability(30)\n" +
                "end)",
                "armor_material");

        var definition = contentService.getArmorMaterial("test_ruby");
        assertNotNull(definition, "createArmorMaterial must register the definition");
        assertEquals(5, definition.getDefense("helmet"));
        assertEquals(4.5f, definition.getToughness());
        assertEquals(0.2f, definition.getKnockbackResistance());
        assertEquals(30, definition.getEnchantability());
        assertEquals("luatweaker:test_ruby", definition.getLayer());
        assertEquals("minecraft:item.armor.equip_iron", definition.getEquipSound());
    }

    @Test
    public void testCreateItemFromLua() throws IOException {
        String script = """
            startup:createItem("custom_ruby", function(item)
                item:maxStackSize(16)
                    :rarity("EPIC")
                    :burnTime(400)
                    :displayName("Enchanted Ruby Gem")
            end)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(1, contentService.getRegisteredItems().size());
        IItemBuilder builder = contentService.getRegisteredItems().iterator().next();
        assertEquals("custom_ruby", builder.getId());
        assertEquals(16, builder.getMaxStackSize());
        assertEquals("EPIC", builder.getRarity());
        assertEquals(400, builder.getBurnTime());
        assertEquals("Enchanted Ruby Gem", builder.getDisplayName());
    }

    @Test
    public void testPascalCaseItemBuilderFromLua() throws IOException {
        String script = """
            local Content = require("LuaTweaker.Content")
            local ruby = Content.NewItem("pascal_ruby")
                :MaxStackSize(64)
                :Rarity("EPIC")
                :BurnTime(400)
                :DisplayName("Pascal Ruby Gem")
                :CreativeTab("ruby_tab")
                :Register()
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(1, contentService.getRegisteredItems().size());
        IItemBuilder builder = contentService.getRegisteredItems().iterator().next();
        assertEquals("pascal_ruby", builder.getId());
        assertEquals(64, builder.getMaxStackSize());
        assertEquals("EPIC", builder.getRarity());
        assertEquals(400, builder.getBurnTime());
        assertEquals("Pascal Ruby Gem", builder.getDisplayName());
    }

    @Test
    public void testCreateBlockFromLua() throws IOException {
        String script = """
            startup:createBlock("custom_ruby_block", function(block)
                block:hardness(3.0)
                     :resistance(12.0)
                     :lightLevel(10)
                     :soundType("STONE")
            end)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(1, contentService.getRegisteredBlocks().size());
        IBlockBuilder builder = contentService.getRegisteredBlocks().iterator().next();
        assertEquals("custom_ruby_block", builder.getId());
        assertEquals(3.0f, builder.getHardness());
        assertEquals(12.0f, builder.getResistance());
        assertEquals(10, builder.getLightLevel());
        assertEquals("STONE", builder.getSoundType());
    }

    @Test
    public void testContainerUseDistanceFromLua() throws IOException {
        String script = """
            local Content = require("LuaTweaker.Content")
            local crate = Content.NewBlock("test_crate")
                :Container(4, 6, "packed")
                :ContainerUseDistance(10)
                :Register()
            local defaultCrate = Content.NewBlock("default_crate")
                :Container(2, 3, "spill")
                :Register()
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(2, contentService.getRegisteredBlocks().size());
        boolean sawCustom = false;
        boolean sawDefault = false;
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            assertTrue(builder.isContainer());
            if ("test_crate".equals(builder.getId())) {
                assertEquals(10.0, builder.getContainerUseDistance());
                sawCustom = true;
            } else if ("default_crate".equals(builder.getId())) {
                assertEquals(8.0, builder.getContainerUseDistance(),
                        "use distance must default to vanilla reach (8 blocks)");
                sawDefault = true;
            }
        }
        assertTrue(sawCustom, "custom-distance container must have been registered");
        assertTrue(sawDefault, "default-distance container must have been registered");
    }

    @Test
    public void testNewBlockPropertiesFromLua() throws IOException {
        String script = """
            local Content = require("LuaTweaker.Content")
            local b = Content.NewBlock("prop_block")
                :MapColor("RED")
                :JumpFactor(1.5)
                :SpeedFactor(0.5)
                :NoCollision(true)
                :NoOcclusion(true)
                :PushReaction("BLOCK")
                :Replaceable(true)
                :IgnitedByLava(true)
                :OffsetType("XZ")
                :RedstoneConductor(false)
                :Register()
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        IBlockBuilder builder = contentService.getRegisteredBlocks().iterator().next();
        assertEquals("prop_block", builder.getId());
        assertEquals("RED", builder.getMapColor());
        assertEquals(1.5f, builder.getJumpFactor());
        assertEquals(0.5f, builder.getSpeedFactor());
        assertTrue(builder.isNoCollision());
        assertTrue(builder.isNoOcclusion());
        assertEquals("BLOCK", builder.getPushReaction());
        assertTrue(builder.isReplaceable());
        assertTrue(builder.isIgnitedByLava());
        assertFalse(builder.isLiquid(), "liquid must default to false");
        assertEquals("XZ", builder.getOffsetType());
        assertEquals(Boolean.FALSE, builder.getRedstoneConductor());
    }

    @Test
    public void testContainerSlotCustomizationFromLua() throws IOException {
        String script = """
            local Content = require("LuaTweaker.Content")
            local crate = Content.NewBlock("slot_crate")
                :Container(4, 6, "packed")
                :SlotTexture("luatweaker:textures/gui/wood_crate_slot.png")
                :LockSlot(0, true)
                :LockedSlots({ 5, 11 })
                :SlotPosition(23, 44, 89)
                :Register()
            local openCrate = Content.NewBlock("open_crate")
                :Container(2, 3, "spill")
                :LockSlot(2, true)
                :LockSlot(2, false)
                :Register()
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(2, contentService.getRegisteredBlocks().size());
        boolean sawSlots = false;
        boolean sawUnlock = false;
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            if ("slot_crate".equals(builder.getId())) {
                assertEquals("luatweaker:textures/gui/wood_crate_slot.png", builder.getSlotTexture());
                assertEquals(java.util.Set.of(0, 5, 11), builder.getLockedSlots());
                assertArrayEquals(new int[]{44, 89}, builder.getSlotPositions().get(23),
                        "custom slot position must be stored per index");
                assertEquals(1, builder.getSlotPositions().size());
                sawSlots = true;
            } else if ("open_crate".equals(builder.getId())) {
                assertTrue(builder.getLockedSlots().isEmpty(),
                        "locking then unlocking a slot must remove it from the locked set");
                sawUnlock = true;
            }
        }
        assertTrue(sawSlots, "customized container must have been registered");
        assertTrue(sawUnlock, "unlock container must have been registered");
    }

    @Test
    public void testMachineFeaturesFromLua() throws IOException {
        String script = """
            local Content = require("LuaTweaker.Content")
            local machine = Content.NewBlock("test_machine")
                :Container(3, 3, "packed")
                :EnergyStorage(10000, 500, 250)
                :FluidStorage(8000)
                :GuiBar("energy_bar", 8, 60, 90, 10, "energy", 0xFF00E676)
                :GuiBar("tank", 150, 40, 8, 40, "fluid", 0xFF2196F3)
                :BooleanState("running", "luatweaker:block/test_machine", "luatweaker:block/test_machine_running")
                :Register()
            local pipe = Content.NewBlock("test_pipe")
                :Texture("luatweaker:block/test_pipe")
                :Container(1, 1, "none")
                :ConnectionState(true)
                :EnergyStorage(2000, 200, 200)
                :FluidStorage(4000)
                :OnTick(function(data)
                    print("tick at " .. data.X .. "," .. data.Y .. "," .. data.Z)
                end)
                :Register()
            local machine = Content.NewBlock("tick_machine")
                :Container(2, 2, "packed")
                :EnergyStorage(1000, 100, 100)
                :OnTick(function(data)
                    print("machine energy " .. data.Energy)
                end)
                :Register()
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(3, contentService.getRegisteredBlocks().size());
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            if ("test_machine".equals(builder.getId())) {
                assertEquals(10000, builder.getEnergyCapacity());
                assertEquals(500, builder.getEnergyMaxReceive());
                assertEquals(250, builder.getEnergyMaxExtract());
                assertEquals(8000, builder.getFluidCapacity());
                assertEquals(2, builder.getGuiBars().size());
                var energyBar = builder.getGuiBars().get(0);
                assertEquals("energy_bar", energyBar.id());
                assertEquals(90, energyBar.width());
                assertEquals("energy", energyBar.source());
                assertEquals(0xFF00E676, energyBar.color());
                assertEquals("fluid", builder.getGuiBars().get(1).source());
                assertNotNull(builder.getBooleanState());
                assertEquals("running", builder.getBooleanState().property());
                assertEquals("luatweaker:block/test_machine_running", builder.getBooleanState().onTexture());
                assertFalse(builder.isConnectionState());
                assertNull(builder.getTickHandler(), "no onTick registered for the plain machine");
            } else if ("test_pipe".equals(builder.getId())) {
                assertTrue(builder.isConnectionState());
                assertNull(builder.getBooleanState());
                assertEquals("luatweaker:block/test_pipe", builder.getTexture());
                assertEquals(2000, builder.getEnergyCapacity());
                assertEquals(4000, builder.getFluidCapacity());
                assertNotNull(builder.getTickHandler(), "pipe transport must be Lua-defined via onTick");
            } else if ("tick_machine".equals(builder.getId())) {
                assertNotNull(builder.getTickHandler(), "onTick handler must be stored");
            }
        }
    }

    @Test
    public void testBlockBoxesFacingAndOnRenderFromLua() throws IOException {
        String script = """
            local Content = require("LuaTweaker.Content")
            local altar = Content.NewBlock("altar_block")
                :Facing("horizontal")
                :Box(0, 0, 0, 16, 4, 16)
                :Box(4, 4, 4, 12, 12, 12)
                :OnRender(function(renderer, data)
                    -- render callback
                end)
                :Register()
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        IBlockBuilder builder = null;
        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
            if ("altar_block".equals(b.getId())) {
                builder = b;
                break;
            }
        }
        assertNotNull(builder, "altar_block must be registered");
        assertEquals("HORIZONTAL", builder.getFacing());
        assertEquals(2, builder.getBoxes().size());
        assertEquals(0.0, builder.getBoxes().get(0).minX());
        assertEquals(16.0, builder.getBoxes().get(0).maxX());
        assertEquals(4.0, builder.getBoxes().get(0).maxY());
        assertEquals(4.0, builder.getBoxes().get(1).minX());
        assertEquals(12.0, builder.getBoxes().get(1).maxX());
        assertNotNull(builder.getRenderHandler(), "render handler must be stored");
    }

    @Test
    public void testItemOnUseOnBlockFromLua() throws IOException {
        String script = """
            local Content = require("LuaTweaker.Content")
            local wrench = Content.NewItem("test_wrench")
                :MaxStackSize(1)
                :DisplayName("Test Wrench")
                :OnUseOnBlock(function(player, hit)
                    print("hit " .. hit.X .. "," .. hit.Y .. "," .. hit.Z .. " face=" .. hit.Face)
                    return true
                end)
                :Register()
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        IItemBuilder builder = contentService.getRegisteredItems().iterator().next();
        assertEquals("test_wrench", builder.getId());
        assertNotNull(builder.getOnUseOnBlockHandler(), "onUseOnBlock handler must be stored");
    }

    @Test
    public void testStorageFromLua() throws IOException {
        String script = """
            storage:set("test_key", "hello_world")
            storage:set("number_key", 42)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals("hello_world", storageService.get("test_key", null));
        assertEquals(42, ((Number) storageService.get("number_key", null)).intValue());
    }

    @Test
    public void testDatapackFromLua() throws IOException {
        String script = """
            datapack:addJsonRecipe("custom_crafting", '{"type":"minecraft:crafting_shapeless"}')
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(1, datapackService.getVirtualFiles().size());
        assertTrue(datapackService.getVirtualFiles().containsKey("data/luatweaker/recipe/custom_crafting.json"));
    }

    @Test
    public void testDatapackClearWipesEverything() {
        datapackService.addJsonRecipe("custom_crafting", "{}");
        datapackService.addData("data/mymod/recipe/stale.json", "{}");
        datapackService.addTag("block", "minecraft:mineable/pickaxe", java.util.List.of("mymod:test_block"));
        assertFalse(datapackService.getVirtualFiles().isEmpty());

        datapackService.clear();

        assertTrue(datapackService.getVirtualFiles().isEmpty(),
                "a reload must start from a clean virtual pack so stale files disappear");
    }

    @Test
    public void testCreateEntityFromLua() throws IOException {
        String script = """
            startup:createEntity("custom_zombie", function(entity)
                entity:category("MONSTER")
                      :dimensions(0.8, 2.0)
                      :maxHealth(50.0)
                      :movementSpeed(0.3)
                      :attackDamage(8.0)
                      :spawnEgg(0x00FF00, 0x0000FF)
            end)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        assertEquals(1, contentService.getRegisteredEntities().size());
        var builder = contentService.getRegisteredEntities().iterator().next();
        assertEquals("custom_zombie", builder.getId());
        assertEquals("MONSTER", builder.getCategory());
        assertEquals(0.8f, builder.getWidth());
        assertEquals(2.0f, builder.getHeight());
        assertEquals(50.0, builder.getMaxHealth());
        assertEquals(0.3, builder.getMovementSpeed());
        assertEquals(8.0, builder.getAttackDamage());
        assertTrue(builder.hasSpawnEgg());
        assertEquals(0x00FF00, builder.getPrimaryColor());
        assertEquals(0x0000FF, builder.getSecondaryColor());
    }

    @Test
    public void testItemAnimationAndLifecycleCallbacks() throws IOException {
        String script = """
            startup:createItem("magic_staff", function(item)
                item:useAnimation("BOW")
                    :useDuration(72000)
                    :onUseTick(function(player, stack, ticks) end)
                    :onReleaseUsing(function(player, stack, timeLeft) end)
                    :onFinishUsing(function(player, stack) end)
                    :onHurtEnemy(function(target, attacker, stack) end)
                    :onMineBlock(function(player, stack, x, y, z) end)
                    :onSwing(function(player, stack) end)
            end)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        IItemBuilder builder = contentService.getRegisteredItems().stream()
                .filter(i -> i.getId().equals("magic_staff"))
                .findFirst().orElse(null);
        assertNotNull(builder);
        assertEquals("BOW", builder.getUseAnimation());
        assertEquals(72000, builder.getUseDuration());
        assertNotNull(builder.getOnUseTickHandler());
        assertNotNull(builder.getOnReleaseUsingHandler());
        assertNotNull(builder.getOnFinishUsingHandler());
        assertNotNull(builder.getOnHurtEnemyHandler());
        assertNotNull(builder.getOnMineBlockHandler());
        assertNotNull(builder.getOnSwingHandler());
    }

    @Test
    public void testBlockButtonsLabelsAndCallbacks() throws IOException {
        String script = """
            startup:createBlock("advanced_furnace", function(block)
                block:container(3, 3)
                     :button("power_btn", 10, 20, 30, 15, "Power")
                     :label(5, 10, "Heat: {Temp}", 0xFF0000)
                     :onButtonClick(function(pos, btnId) end)
                     :onNeighborChanged(function(pos, nPos) end)
            end)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        IBlockBuilder builder = contentService.getRegisteredBlocks().stream()
                .filter(b -> b.getId().equals("advanced_furnace"))
                .findFirst().orElse(null);
        assertNotNull(builder);
        assertEquals(1, builder.getButtons().size());
        assertEquals("power_btn", builder.getButtons().get(0).id());
        assertEquals(10, builder.getButtons().get(0).x());
        assertEquals(20, builder.getButtons().get(0).y());
        assertEquals(30, builder.getButtons().get(0).width());
        assertEquals(15, builder.getButtons().get(0).height());
        assertEquals("Power", builder.getButtons().get(0).label());

        assertEquals(1, builder.getLabels().size());
        assertEquals(5, builder.getLabels().get(0).x());
        assertEquals(10, builder.getLabels().get(0).y());
        assertEquals("Heat: {Temp}", builder.getLabels().get(0).textOrVar());
        assertEquals(0xFF0000, builder.getLabels().get(0).color());

        assertNotNull(builder.getButtonClickHandler());
        assertNotNull(builder.getNeighborChangedHandler());
    }

    @Test
    public void testBlockCustomBarFromLua() throws IOException {
        String script = """
            local startup = require("LuaTweaker.Startup")
            startup:createBlock("fusion_reactor", function(block)
                block:container(3, 3)
                     :customBar("heat_gauge", 10, 20, 16, 50, "CoreHeat", "MaxHeat", 0xFF4400, "Core: %d / %d C")
            end)
        """;
        File file = createTempScript(script);
        engine.executeScript(file, "TEST");

        IBlockBuilder builder = contentService.getRegisteredBlocks().stream()
                .filter(b -> b.getId().equals("fusion_reactor"))
                .findFirst().orElse(null);
        assertNotNull(builder);
        assertEquals(1, builder.getGuiBars().size());
        var bar = builder.getGuiBars().get(0);
        assertEquals("heat_gauge", bar.id());
        assertEquals(10, bar.x());
        assertEquals(20, bar.y());
        assertEquals(16, bar.width());
        assertEquals(50, bar.height());
        assertEquals("custom", bar.source());
        assertEquals("CoreHeat", bar.currentKey());
        assertEquals("MaxHeat", bar.maxKey());
        assertEquals(0xFF4400, bar.color());
        assertEquals("Core: %d / %d C", bar.tooltipFormat());
    }
}
