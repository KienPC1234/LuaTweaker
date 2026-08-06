package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class ContentBuilderTest {

    private CobaltLuaEngine engine;
    private ContentServiceImpl contentService;
    private StorageServiceImpl storageService;
    private DatapackServiceImpl datapackService;

    @BeforeEach
    public void setUp() {
        engine = new CobaltLuaEngine();
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
}
