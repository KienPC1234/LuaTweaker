package com.luatweaker.interaction;

import com.luatweaker.api.pal.IPlatformInteraction;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiblockPrimitivesBindingTest {

    private static final class MockInteraction extends BaseMockPlatformInteraction {
        final List<String> calls = new ArrayList<>();

        @Override
        public int getRedstoneSignal(@NotNull String dimension, int x, int y, int z, @Nullable String direction) {
            calls.add("getRedstoneSignal " + x + "," + y + "," + z + " dir=" + direction);
            return 15;
        }

        @Override
        public boolean checkPattern(@NotNull String dimension, int originX, int originY, int originZ,
                                    @NotNull List<Map<String, Object>> pattern) {
            calls.add("checkPattern origin=" + originX + "," + originY + "," + originZ + " size=" + pattern.size());
            return true;
        }

        @Override
        public int transferEnergy(@NotNull String dimension, int fromX, int fromY, int fromZ,
                                  int toX, int toY, int toZ, int maxAmount, boolean simulate) {
            calls.add("transferEnergy from=" + fromX + "," + fromY + "," + fromZ + " to=" + toX + "," + toY + "," + toZ + " max=" + maxAmount + " sim=" + simulate);
            return 500;
        }

        @Override
        public int transferFluid(@NotNull String dimension, int fromX, int fromY, int fromZ,
                                 int toX, int toY, int toZ, int maxAmount, boolean simulate) {
            calls.add("transferFluid from=" + fromX + "," + fromY + "," + fromZ + " to=" + toX + "," + toY + "," + toZ + " max=" + maxAmount + " sim=" + simulate);
            return 1000;
        }

        @Override
        public int transferItem(@NotNull String dimension, int fromX, int fromY, int fromZ, int fromSlot,
                                int toX, int toY, int toZ, int toSlot, int maxCount, boolean simulate) {
            calls.add("transferItem from=" + fromX + "," + fromY + "," + fromZ + "[" + fromSlot + "] to=" + toX + "," + toY + "," + toZ + "[" + toSlot + "] max=" + maxCount + " sim=" + simulate);
            return 16;
        }

        @Override
        public @NotNull Map<String, Object> validateCuboid(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2, @NotNull Map<String, Object> spec) {
            calls.add("validateCuboid " + x1 + "," + y1 + "," + z1 + " to " + x2 + "," + y2 + "," + z2);
            return Map.of("valid", true, "width", 3, "height", 3, "depth", 3);
        }

        @Override
        public int setBlockStatesInBox(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2, @Nullable String filterBlockId, @NotNull Map<String, Object> properties) {
            calls.add("setBlockStatesInBox " + x1 + "," + y1 + "," + z1 + " to " + x2 + "," + y2 + "," + z2 + " filter=" + filterBlockId);
            return 26;
        }
    }

    private MockInteraction interaction;
    private IPlatformInteraction previousInteraction;

    @BeforeEach
    public void setup() {
        interaction = new MockInteraction();
        previousInteraction = Platform.isInitialized() ? Platform.getInteraction() : null;
        Platform.setInteraction(interaction);
    }

    @AfterEach
    public void teardown() {
        if (previousInteraction != null) {
            Platform.setInteraction(previousInteraction);
        }
    }

    @Test
    public void testWorldMultiblockAndTransferMethods() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        engine.executeStringOrThrow("""
            local signal = World:GetRedstoneSignal(10, 64, 20, "UP")
            assert(signal == 15, "signal must be 15")

            local pattern = {
                { dx = 0, dy = -1, dz = 0, block = "minecraft:blast_furnace" },
                { dx = 1, dy = 0, dz = 0, tag = "c:storage_blocks/iron" }
            }
            local matched = World:CheckPattern(10, 64, 20, pattern)
            assert(matched == true, "pattern must match")

            local feMoved = World:TransferEnergy({X=10, Y=64, Z=20}, {X=11, Y=64, Z=20}, 500)
            assert(feMoved == 500, "feMoved must be 500")

            local fluidMoved = World:TransferFluid({X=10, Y=64, Z=20}, {X=10, Y=65, Z=20}, 1000)
            assert(fluidMoved == 1000, "fluidMoved must be 1000")

            local itemMoved = World:TransferItem({X=10, Y=64, Z=20}, 0, {X=10, Y=63, Z=20}, 1, 16)
            assert(itemMoved == 16, "itemMoved must be 16")
        """, "multiblock_test");

        assertEquals(5, interaction.calls.size());
        assertEquals("getRedstoneSignal 10,64,20 dir=UP", interaction.calls.get(0));
        assertEquals("checkPattern origin=10,64,20 size=2", interaction.calls.get(1));
        assertEquals("transferEnergy from=10,64,20 to=11,64,20 max=500 sim=false", interaction.calls.get(2));
        assertEquals("transferFluid from=10,64,20 to=10,65,20 max=1000 sim=false", interaction.calls.get(3));
        assertEquals("transferItem from=10,64,20[0] to=10,63,20[1] max=16 sim=false", interaction.calls.get(4));
    }

    @Test
    public void testValidateCuboidAndSetBlockStatesInBox() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        engine.executeStringOrThrow("""
            local spec = {
                frame = "mymod:casing",
                walls = "mymod:casing",
                interior = "minecraft:air"
            }
            local res = World:ValidateCuboid({X=0, Y=64, Z=0}, {X=2, Y=66, Z=2}, spec)
            assert(res.valid == true, "validation must succeed")
            assert(res.width == 3, "width must be 3")

            local updated = World:SetBlockStatesInBox({X=0, Y=64, Z=0}, {X=2, Y=66, Z=2}, "mymod:casing", { formed = true })
            assert(updated == 26, "updated count must be 26")
        """, "cuboid_test");

        assertEquals(2, interaction.calls.size());
        assertEquals("validateCuboid 0,64,0 to 2,66,2", interaction.calls.get(0));
        assertEquals("setBlockStatesInBox 0,64,0 to 2,66,2 filter=mymod:casing", interaction.calls.get(1));
    }
}
