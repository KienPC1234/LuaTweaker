package com.luatweaker.interaction;

import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The World capability primitives are the bridge to other mods' machines. These tests pin the Lua
 * argument order, the dimension prefix and the values that round-trip through the capability layer.
 */
class CapabilityBindingTest {

    private RecordingCapability capability;

    @BeforeEach
    void setUp() {
        capability = new RecordingCapability();
        Platform.setCapability(capability);
    }

    @AfterEach
    void tearDown() {
        Platform.setCapability(null);
    }

    private ILuaEngine engineWithBindings() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);
        return engine;
    }

    @Test
    void energyPrimitivesForwardDimensionCoordinatesSideAndAmount() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow("""
                local energy = World:GetEnergy('minecraft:the_nether', 10, 64, -5, 'up')
                assert(energy.Stored == 100 and energy.Max == 400,
                    'energy values must round-trip')
                assert(energy.CanExtract == true and energy.CanReceive == false)
                assert(World:InsertEnergy(10, 64, -5, 50, 'down') == 7)
                assert(World:ExtractEnergy(10, 64, -5, 20) == 3)
                """, "capability_energy");

        assertEquals(List.of(
                        "getEnergy minecraft:the_nether 10,64,-5 UP",
                        "insertEnergy minecraft:overworld 10,64,-5 DOWN amount=50",
                        "extractEnergy minecraft:overworld 10,64,-5 null amount=20"),
                capability.calls,
                "energy reads must use the given dimension while writes default to the overworld");
    }

    @Test
    void itemPrimitivesForwardSlotsAndStacks() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow("""
                local slots = World:GetItemSlots(1, 2, 3)
                assert(#slots == 1, 'one occupied slot expected, got ' .. #slots)
                assert(slots[1].Slot == 2 and slots[1].Id == 'minecraft:iron_ingot')
                assert(slots[1].Count == 5)
                assert(World:InsertItem(1, 2, 3, 'minecraft:coal', 8, 'north') == 4)
                local taken = World:ExtractItem(1, 2, 3, 1, 2)
                assert(taken.Id == 'minecraft:coal' and taken.Count == 2 and taken.Slot == 1)
                """, "capability_items");

        assertEquals(List.of(
                        "getItemSlots minecraft:overworld 1,2,3 null",
                        "insertItem minecraft:overworld 1,2,3 NORTH item=minecraft:coal count=8",
                        "extractItem minecraft:overworld 1,2,3 null slot=1 count=2"),
                capability.calls,
                "item slot reads, inserts and extracts must forward slots and stack sizes");
    }

    @Test
    void fluidPrimitivesForwardIdsAndAmounts() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow("""
                local tank = World:GetFluid(4, 5, 6)
                assert(tank.Id == 'minecraft:water' and tank.Amount == 500
                    and tank.Capacity == 1000)
                assert(World:FillFluid(4, 5, 6, 'minecraft:lava', 300, 'west') == 250)
                assert(World:DrainFluid(4, 5, 6, '*', 200) == 125)
                """, "capability_fluids");

        assertEquals(List.of(
                        "getFluid minecraft:overworld 4,5,6 null",
                        "fillFluid minecraft:overworld 4,5,6 WEST fluid=minecraft:lava amount=300",
                        "drainFluid minecraft:overworld 4,5,6 null fluid=* amount=200"),
                capability.calls,
                "fluid reads, fills and drains must forward fluid ids and amounts");
    }

    @Test
    void blockInstanceDispatchesCapabilityMethods() {
        ILuaEngine engine = engineWithBindings();
        IInteractableBlock mockBlock = new IInteractableBlock() {
            @Override public String getId() { return "minecraft:furnace"; }
            @Override public void setId(String id) { }
            @Override public int getX() { return 12; }
            @Override public int getY() { return 64; }
            @Override public int getZ() { return -8; }
            @Override public String getDimension() { return "minecraft:overworld"; }
            @Override public boolean breakBlock() { return true; }
            @Override public boolean useBlock(Object actor) { return true; }
            @Override public Object getRawBlockState() { return this; }
        };

        ILuaTable blockTable = InteractionLuaBinding.wrapBlock(engine, mockBlock);
        engine.getGlobalEnvironment().rawset("TestBlock", blockTable);

        engine.executeStringOrThrow("""
                local energy = TestBlock:GetEnergy('UP')
                assert(energy ~= nil and energy.Stored == 100, 'energy stored mismatch')
                assert(TestBlock:InsertEnergy(50, 'NORTH') == 7, 'insertEnergy failed')
                assert(TestBlock:ExtractEnergy(20) == 3, 'extractEnergy failed')
                local slots = TestBlock:GetItemSlots('DOWN')
                assert(slots ~= nil and #slots == 1, 'slots mismatch')
                assert(TestBlock:InsertItem('minecraft:iron_ore', 5, 'UP') == 4, 'insertItem failed')
                assert(TestBlock:FillFluid('minecraft:lava', 100, 'WEST') == 250,
                    'fillFluid failed')
                assert(TestBlock:DrainFluid(50) == 125, 'drainFluid failed')
                """, "block_capability_test");

        assertEquals(
                List.of(
                        "getEnergy minecraft:overworld 12,64,-8 UP",
                        "insertEnergy minecraft:overworld 12,64,-8 NORTH amount=50",
                        "extractEnergy minecraft:overworld 12,64,-8 null amount=20",
                        "getItemSlots minecraft:overworld 12,64,-8 DOWN",
                        "insertItem minecraft:overworld 12,64,-8 UP item=minecraft:iron_ore count=5",
                        "fillFluid minecraft:overworld 12,64,-8 WEST fluid=minecraft:lava amount=100",
                        "drainFluid minecraft:overworld 12,64,-8 null fluid=* amount=50"),
                capability.calls,
                "a wrapped block must pass its own position and dimension to every capability call");
    }
}
