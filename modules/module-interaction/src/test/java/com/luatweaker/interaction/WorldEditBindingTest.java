package com.luatweaker.interaction;

import com.luatweaker.api.pal.IPlatformInteraction;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the World:FillBlocks / World:ReplaceBlocks Lua bindings against a
 * mock PAL: argument parsing (including the optional dimension prefix and
 * properties map), count round-trip, and the loud -1 failure path.
 */
public class WorldEditBindingTest {

    private static class MockInteraction implements IPlatformInteraction {
        String fillDimension;
        int[] fillCoords;
        String fillBlockId;
        Map<String, Object> fillProperties;
        long fillResult = 42;
        int fillCalls = 0;

        String replaceDimension;
        int[] replaceCoords;
        String replaceFromId;
        String replaceToId;
        long replaceResult = 7;
        int replaceCalls = 0;

        @Override
        public long fillBlocks(String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                               String blockId, Map<String, Object> properties) {
            fillCalls++;
            fillDimension = dimension;
            fillCoords = new int[] { x1, y1, z1, x2, y2, z2 };
            fillBlockId = blockId;
            fillProperties = properties;
            return fillResult;
        }

        @Override
        public long replaceBlocks(String dimension, int x1, int y1, int z1, int x2, int y2, int z2,
                                  String fromId, String toId) {
            replaceCalls++;
            replaceDimension = dimension;
            replaceCoords = new int[] { x1, y1, z1, x2, y2, z2 };
            replaceFromId = fromId;
            replaceToId = toId;
            return replaceResult;
        }

        // ---- unused PAL methods (trivial stubs for interface compliance) ----
        @Override public void shootProjectile(com.luatweaker.api.entity.IEntity s, String p, double sp, double i) {}
        @Override public com.luatweaker.api.entity.IEntity shootProjectileAt(com.luatweaker.api.entity.IEntity s, String p, com.luatweaker.api.entity.IEntity t, double sp) { return null; }
        @Override public void playAnimation(com.luatweaker.api.entity.IEntity e, String a, double sp, double tr) {}
        @Override public boolean performBlockBreak(com.luatweaker.api.entity.IEntity a, int x, int y, int z) { return false; }
        @Override public boolean performBlockPlace(com.luatweaker.api.entity.IEntity a, int x, int y, int z, String b) { return false; }
        @Override public boolean performBlockUse(com.luatweaker.api.entity.IEntity a, int x, int y, int z) { return false; }
        @Override public boolean performItemUse(com.luatweaker.api.entity.IEntity a, int s) { return false; }
        @Override public void lookAt(com.luatweaker.api.entity.IEntity a, double x, double y, double z) {}
        @Override public void lookAt(com.luatweaker.api.entity.IEntity a, com.luatweaker.api.entity.IEntity t) {}
        @Override public boolean moveInventoryItem(com.luatweaker.api.entity.IEntity a, int f, int t) { return false; }
        @Override public boolean dropInventoryItem(com.luatweaker.api.entity.IEntity a, int s, int c) { return false; }
        @Override public java.util.List<com.luatweaker.api.objects.IWorldBlock> getNearbyBlocks(com.luatweaker.api.entity.IEntity e, int r) { return java.util.List.of(); }
        @Override public java.util.List<com.luatweaker.api.objects.ILocatedItem> getInventoryItems(com.luatweaker.api.entity.IEntity e) { return java.util.List.of(); }
        @Override public java.util.List<com.luatweaker.api.entity.IEntity> getNearbyEntities(com.luatweaker.api.entity.IEntity c, double r) { return java.util.List.of(); }
        @Override public com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(String d, int x, int y, int z) { return null; }
        @Override public com.luatweaker.api.interaction.IInteractableItem getInteractableItem(Object e, int s) { return null; }
        @Override public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(String u) { return null; }
        @Override public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(Object e) { return null; }
        @Override public Map<String, Object> getBlockState(String d, int x, int y, int z) { return null; }
        @Override public boolean setBlockState(String d, int x, int y, int z, String b, Map<String, Object> p) { return false; }
        @Override public Map<String, Object> getBlockEntityData(String d, int x, int y, int z) { return null; }
        @Override public boolean setBlockEntityData(String d, int x, int y, int z, Map<String, Object> data) { return false; }
        @Override public boolean ejectContainerItem(String d, int x, int y, int z, int slot, int count) { return false; }
        @Override public boolean executeCommand(String c) { return false; }
    }

    private MockInteraction interaction;

    @BeforeEach
    public void setUp() {
        interaction = new MockInteraction();
        Platform.setInteraction(interaction);
    }

    @AfterAll
    public static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    /** Executes Lua and fails the test if the engine recorded a script error. */
    private static void executeLua(ILuaEngine engine, String code, String name) {
        engine.executeString(code, name);
        if (engine instanceof CobaltLuaEngine cobalt) {
            String error = cobalt.getAndClearLastExecutionError();
            assertNull(error, "Lua script failed: " + error);
        }
    }

    @Test
    public void fillBlocksPassesArgsAndRoundTripsCount() {
        ILuaEngine engine = new CobaltLuaEngine();
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "local n = World:FillBlocks(1, 2, 3, 4, 5, 6, 'minecraft:stone', { waterlogged = 'true' })\n" +
                "assert(n == 42, 'count must round-trip: ' .. tostring(n))",
                "fill_test");

        assertEquals(1, interaction.fillCalls);
        assertEquals("minecraft:overworld", interaction.fillDimension, "dimension must default to overworld");
        assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 6 }, interaction.fillCoords);
        assertEquals("minecraft:stone", interaction.fillBlockId);
        assertEquals(Map.of("waterlogged", "true"), interaction.fillProperties);
    }

    @Test
    public void fillBlocksSupportsDimensionPrefix() {
        ILuaEngine engine = new CobaltLuaEngine();
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "local n = World:FillBlocks('minecraft:the_nether', 0, 0, 0, 1, 1, 1, 'minecraft:netherrack')\n" +
                "assert(n == 42)",
                "fill_dim_test");

        assertEquals("minecraft:the_nether", interaction.fillDimension);
        assertEquals("minecraft:netherrack", interaction.fillBlockId);
    }

    @Test
    public void replaceBlocksPassesArgsAndRoundTripsCount() {
        ILuaEngine engine = new CobaltLuaEngine();
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "local n = World:ReplaceBlocks(0, 0, 0, 2, 2, 2, 'minecraft:stone', 'minecraft:cobblestone')\n" +
                "assert(n == 7, 'count must round-trip: ' .. tostring(n))",
                "replace_test");

        assertEquals(1, interaction.replaceCalls);
        assertEquals("minecraft:overworld", interaction.replaceDimension);
        assertArrayEquals(new int[] { 0, 0, 0, 2, 2, 2 }, interaction.replaceCoords);
        assertEquals("minecraft:stone", interaction.replaceFromId);
        assertEquals("minecraft:cobblestone", interaction.replaceToId);
    }

    @Test
    public void missingArgumentsReturnMinusOne() {
        ILuaEngine engine = new CobaltLuaEngine();
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "assert(World:FillBlocks(1, 2, 3) == -1, 'too few fill args must return -1')\n" +
                "assert(World:ReplaceBlocks(1, 2, 3) == -1, 'too few replace args must return -1')\n" +
                "assert(World:FillBlocks() == -1, 'no args must return -1')",
                "fill_missing_args_test");

        assertEquals(0, interaction.fillCalls, "the platform must not be called for malformed input");
        assertEquals(0, interaction.replaceCalls);
    }

    @Test
    public void platformRejectionRoundTrips() {
        interaction.fillResult = -1;
        interaction.replaceResult = -1;
        ILuaEngine engine = new CobaltLuaEngine();
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "assert(World:FillBlocks(0, 0, 0, 1, 1, 1, 'minecraft:stone') == -1)\n" +
                "assert(World:ReplaceBlocks(0, 0, 0, 1, 1, 1, 'minecraft:stone', 'minecraft:cobblestone') == -1)",
                "fill_reject_test");

        assertEquals(1, interaction.fillCalls);
        assertEquals(1, interaction.replaceCalls);
    }

    @Test
    public void camelCaseAliasesExist() {
        ILuaEngine engine = new CobaltLuaEngine();
        InteractionLuaBinding.registerBindings(engine);

        ILuaValue fill = engine.getGlobalEnvironment().rawget("World").asTable().rawget("fillBlocks");
        assertNotNull(fill);
        assertTrue(fill.isFunction(), "World.fillBlocks alias must exist");

        ILuaValue replace = engine.getGlobalEnvironment().rawget("World").asTable().rawget("replaceBlocks");
        assertNotNull(replace);
        assertTrue(replace.isFunction(), "World.replaceBlocks alias must exist");
    }
}
