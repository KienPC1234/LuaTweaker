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

    private static final class MockInteraction implements IPlatformInteraction {
        final List<String> calls = new ArrayList<>();

        @Override public void shootProjectile(@NotNull com.luatweaker.api.entity.IEntity shooter, @NotNull String projectileType, double speed, double inaccuracy) {}
        @Nullable @Override public com.luatweaker.api.entity.IEntity shootProjectileAt(@NotNull com.luatweaker.api.entity.IEntity shooter, @NotNull String projectileType, @NotNull com.luatweaker.api.entity.IEntity target, double speed) { return null; }
        @Override public void playAnimation(@NotNull com.luatweaker.api.entity.IEntity entity, @NotNull String animName, double speed, double transition) {}
        @Override public boolean performBlockBreak(@NotNull com.luatweaker.api.entity.IEntity actor, int x, int y, int z) { return false; }
        @Override public boolean performBlockPlace(@NotNull com.luatweaker.api.entity.IEntity actor, int x, int y, int z, @NotNull String blockId) { return false; }
        @Override public boolean performBlockUse(@NotNull com.luatweaker.api.entity.IEntity actor, int x, int y, int z) { return false; }
        @Override public boolean performItemUse(@NotNull com.luatweaker.api.entity.IEntity actor, int slot) { return false; }
        @Override public void lookAt(@NotNull com.luatweaker.api.entity.IEntity actor, double x, double y, double z) {}
        @Override public void lookAt(@NotNull com.luatweaker.api.entity.IEntity actor, @NotNull com.luatweaker.api.entity.IEntity target) {}
        @Override public boolean moveInventoryItem(@NotNull com.luatweaker.api.entity.IEntity actor, int fromSlot, int toSlot) { return false; }
        @Override public boolean dropInventoryItem(@NotNull com.luatweaker.api.entity.IEntity actor, int slot, int count) { return false; }
        @NotNull @Override public List<com.luatweaker.api.objects.IWorldBlock> getNearbyBlocks(@NotNull com.luatweaker.api.entity.IEntity entity, int radius) { return List.of(); }
        @NotNull @Override public List<com.luatweaker.api.objects.ILocatedItem> getInventoryItems(@NotNull com.luatweaker.api.entity.IEntity entity) { return List.of(); }
        @NotNull @Override public List<com.luatweaker.api.entity.IEntity> getNearbyEntities(@NotNull com.luatweaker.api.entity.IEntity center, double radius) { return List.of(); }
        @Nullable @Override public com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(@NotNull String dimension, int x, int y, int z) { return null; }
        @Nullable @Override public com.luatweaker.api.interaction.IInteractableItem getInteractableItem(@NotNull Object entityOrBlock, int slot) { return null; }
        @Nullable @Override public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull String uuid) { return null; }
        @Nullable @Override public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull Object rawEntity) { return null; }
        @Nullable @Override public Map<String, Object> getBlockState(@NotNull String dimension, int x, int y, int z) { return null; }
        @Override public boolean setBlockState(@NotNull String dimension, int x, int y, int z, @NotNull String blockId, @Nullable Map<String, Object> properties) { return false; }
        @Nullable @Override public Map<String, Object> getBlockEntityData(@NotNull String dimension, int x, int y, int z) { return null; }
        @Override public boolean setBlockEntityData(@NotNull String dimension, int x, int y, int z, @NotNull Map<String, Object> data) { return false; }
        @Override public boolean ejectContainerItem(@NotNull String dimension, int x, int y, int z, int slot, int count) { return false; }
        @Override public long fillBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2, @NotNull String blockId, @Nullable Map<String, Object> properties) { return 0; }
        @Override public long replaceBlocks(@NotNull String dimension, int x1, int y1, int z1, int x2, int y2, int z2, @NotNull String fromId, @NotNull String toId) { return 0; }
        @Override public boolean placeStructure(@NotNull String dimension, @NotNull String templateId, int x, int y, int z, int rotationDegrees) { return false; }
        @Override public boolean executeCommand(@NotNull String command) { return false; }

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
}
