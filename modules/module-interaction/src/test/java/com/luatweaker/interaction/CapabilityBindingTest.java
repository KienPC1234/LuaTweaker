package com.luatweaker.interaction;

import com.luatweaker.api.pal.IPlatformCapability;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The World capability primitives are the bridge to other mods' machines. These tests pin
 * the Lua argument order, the dimension prefix, side validation, and the "no capability"
 * result shape (nil for reads, 0 for writes) so a script can tell "no machine" from
 * "machine refused".
 */
public class CapabilityBindingTest {

    /** Records every call as {@code name dim x,y,z side [details]} so assertions stay exact. */
    private static final class MockCapability implements IPlatformCapability {
        final List<String> calls = new ArrayList<>();

        private void remember(String call, String dimension, int x, int y, int z, String side,
                              String details) {
            calls.add(call + " " + dimension + " " + x + "," + y + "," + z + " "
                    + (side == null ? "null" : side) + (details == null ? "" : " " + details));
        }

        @Override
        public Map<String, Object> getEnergy(String dimension, int x, int y, int z, String side) {
            remember("getEnergy", dimension, x, y, z, side, null);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("Stored", 100);
            data.put("Max", 400);
            data.put("CanExtract", true);
            data.put("CanReceive", false);
            return data;
        }

        @Override
        public int insertEnergy(String dimension, int x, int y, int z, int amount, String side) {
            remember("insertEnergy", dimension, x, y, z, side, "amount=" + amount);
            return 7;
        }

        @Override
        public int extractEnergy(String dimension, int x, int y, int z, int amount, String side) {
            remember("extractEnergy", dimension, x, y, z, side, "amount=" + amount);
            return 3;
        }

        @Override
        public List<Map<String, Object>> getItemSlots(String dimension, int x, int y, int z, String side) {
            remember("getItemSlots", dimension, x, y, z, side, null);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("Slot", 2);
            entry.put("Id", "minecraft:iron_ingot");
            entry.put("Count", 5);
            return List.of(entry);
        }

        @Override
        public int insertItem(String dimension, int x, int y, int z, String itemId, int count, String side) {
            remember("insertItem", dimension, x, y, z, side,
                    "item=" + itemId + " count=" + count);
            return 4;
        }

        @Override
        public Map<String, Object> extractItem(String dimension, int x, int y, int z,
                                               int slot, int count, String side) {
            remember("extractItem", dimension, x, y, z, side,
                    "slot=" + slot + " count=" + count);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("Slot", slot);
            data.put("Id", "minecraft:coal");
            data.put("Count", 2);
            return data;
        }

        @Override
        public Map<String, Object> getFluid(String dimension, int x, int y, int z, String side) {
            remember("getFluid", dimension, x, y, z, side, null);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("Id", "minecraft:water");
            data.put("Amount", 500);
            data.put("Capacity", 1000);
            return data;
        }

        @Override
        public int fillFluid(String dimension, int x, int y, int z, String fluidId, int amount, String side) {
            remember("fillFluid", dimension, x, y, z, side,
                    "fluid=" + fluidId + " amount=" + amount);
            return 250;
        }

        @Override
        public int drainFluid(String dimension, int x, int y, int z, String fluidId, int amount, String side) {
            remember("drainFluid", dimension, x, y, z, side,
                    "fluid=" + fluidId + " amount=" + amount);
            return 125;
        }
    }

    private MockCapability capability;

    @BeforeEach
    public void setUp() {
        capability = new MockCapability();
        Platform.setCapability(capability);
    }

    @AfterEach
    public void tearDown() {
        Platform.setCapability(null);
    }

    private ILuaEngine engineWithBindings() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);
        return engine;
    }

    @Test
    public void energyPrimitivesForwardDimensionCoordinatesSideAndAmount() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow(
                "local e = World:GetEnergy('minecraft:the_nether', 10, 64, -5, 'up')\n" +
                "assert(e.Stored == 100 and e.Max == 400, 'energy values must round-trip')\n" +
                "assert(e.CanExtract == true and e.CanReceive == false)\n" +
                "assert(World:InsertEnergy(10, 64, -5, 50, 'down') == 7)\n" +
                "assert(World:ExtractEnergy(10, 64, -5, 20) == 3)",
                "capability_energy");

        assertEquals(List.of(
                "getEnergy minecraft:the_nether 10,64,-5 UP",
                "insertEnergy minecraft:overworld 10,64,-5 DOWN amount=50",
                "extractEnergy minecraft:overworld 10,64,-5 null amount=20"), capability.calls);
    }

    @Test
    public void itemPrimitivesForwardSlotsAndStacks() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow(
                "local slots = World:GetItemSlots(1, 2, 3)\n" +
                "assert(#slots == 1, 'one occupied slot expected, got ' .. #slots)\n" +
                "assert(slots[1].Slot == 2 and slots[1].Id == 'minecraft:iron_ingot')\n" +
                "assert(slots[1].Count == 5)\n" +
                "assert(World:InsertItem(1, 2, 3, 'minecraft:coal', 8, 'north') == 4)\n" +
                "local taken = World:ExtractItem(1, 2, 3, 1, 2)\n" +
                "assert(taken.Id == 'minecraft:coal' and taken.Count == 2 and taken.Slot == 1)",
                "capability_items");

        assertEquals(List.of(
                "getItemSlots minecraft:overworld 1,2,3 null",
                "insertItem minecraft:overworld 1,2,3 NORTH item=minecraft:coal count=8",
                "extractItem minecraft:overworld 1,2,3 null slot=1 count=2"), capability.calls);
    }

    @Test
    public void fluidPrimitivesForwardIdsAndAmounts() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow(
                "local tank = World:GetFluid(4, 5, 6)\n" +
                "assert(tank.Id == 'minecraft:water' and tank.Amount == 500 and tank.Capacity == 1000)\n" +
                "assert(World:FillFluid(4, 5, 6, 'minecraft:lava', 300, 'west') == 250)\n" +
                "assert(World:DrainFluid(4, 5, 6, '*', 200) == 125)",
                "capability_fluids");

        assertEquals(List.of(
                "getFluid minecraft:overworld 4,5,6 null",
                "fillFluid minecraft:overworld 4,5,6 WEST fluid=minecraft:lava amount=300",
                "drainFluid minecraft:overworld 4,5,6 null fluid=* amount=200"), capability.calls);
    }

    @Test
    public void invalidSideIsRejectedBeforeThePlatform() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow(
                "assert(World:GetEnergy(0, 0, 0, 'sideways') == nil, 'a bad side must not fall back')\n" +
                "assert(World:InsertEnergy(0, 0, 0, 10, 'sideways') == 0)",
                "capability_bad_side");

        assertEquals(List.of(), capability.calls,
                "an invalid side must never reach the capability layer");
    }

    @Test
    public void missingCapabilityReportsNilForReadsAndZeroForWrites() {
        Platform.setCapability(null);
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow(
                "assert(World:GetEnergy(0, 0, 0) == nil)\n" +
                "assert(World:GetItemSlots(0, 0, 0) == nil)\n" +
                "assert(World:GetFluid(0, 0, 0) == nil)\n" +
                "assert(World:InsertEnergy(0, 0, 0, 10) == 0)\n" +
                "assert(World:InsertItem(0, 0, 0, 'minecraft:coal', 1) == 0)\n" +
                "assert(World:FillFluid(0, 0, 0, 'minecraft:water', 10) == 0)",
                "capability_missing");
    }

    @Test
    public void malformedArgumentsNeverReachThePlatform() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow(
                "assert(World:GetEnergy(1, 2) == nil)\n" +
                "assert(World:InsertItem(1, 2, 3, 5, 6) == 0, 'a non-string item id must be rejected')",
                "capability_bad_args");

        assertEquals(List.of(), capability.calls);
    }
}
