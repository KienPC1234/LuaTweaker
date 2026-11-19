package com.luatweaker.interaction;

import com.luatweaker.api.pal.IPlatformInteraction;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
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

        String placeDimension;
        String placeTemplateId;
        int[] placeArgs;
        boolean placeResult = true;
        int placeCalls = 0;

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

        @Override
        public boolean placeStructure(String dimension, String templateId,
                                      int x, int y, int z, int rotationDegrees) {
            placeCalls++;
            placeDimension = dimension;
            placeTemplateId = templateId;
            placeArgs = new int[] { x, y, z, rotationDegrees };
            return placeResult;
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

        String particleId;
        double[] particlePos = new double[3];
        int particleCount = -1;
        double particleSpeed = -1;
        int particleCalls = 0;

        @Override
        public void spawnParticle(String dimension, String particleId, double x, double y, double z,
                                  double vx, double vy, double vz, int count, double speed) {
            particleCalls++;
            this.particleId = particleId;
            particlePos = new double[] { x, y, z };
            particleCount = count;
            particleSpeed = speed;
        }

        String soundDimension;
        String soundId;
        String soundSource;
        double[] soundPos = new double[3];
        float soundVolume = 1.0f;
        float soundPitch = 1.0f;
        int soundCalls = 0;
        String stoppedSoundId;
        String stoppedSoundSource;
        int stopCalls = 0;

        @Override
        public void playSound(String dimension, String soundId, double x, double y, double z,
                              float volume, float pitch, String source) {
            soundCalls++;
            soundDimension = dimension;
            this.soundId = soundId;
            soundPos = new double[] { x, y, z };
            soundVolume = volume;
            soundPitch = pitch;
            soundSource = source;
        }

        @Override
        public boolean stopSound(String dimension, String soundId, String source) {
            stopCalls++;
            soundDimension = dimension;
            stoppedSoundId = soundId;
            stoppedSoundSource = source;
            return true;
        }
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
        String error = engine.getAndClearLastExecutionError();
        assertNull(error, "Lua script failed: " + error);
    }

    @Test
    public void fillBlocksPassesArgsAndRoundTripsCount() {
        ILuaEngine engine = LuaEngineFactory.create(false);
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
        ILuaEngine engine = LuaEngineFactory.create(false);
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
        ILuaEngine engine = LuaEngineFactory.create(false);
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
        ILuaEngine engine = LuaEngineFactory.create(false);
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
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "assert(World:FillBlocks(0, 0, 0, 1, 1, 1, 'minecraft:stone') == -1)\n" +
                "assert(World:ReplaceBlocks(0, 0, 0, 1, 1, 1, 'minecraft:stone', 'minecraft:cobblestone') == -1)",
                "fill_reject_test");

        assertEquals(1, interaction.fillCalls);
        assertEquals(1, interaction.replaceCalls);
    }

    @Test
    public void placeStructurePassesArgs() {
        interaction.placeResult = true;
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "local ok = World:PlaceStructure('mymod:shrine', 100, 64, -200, 90)\n" +
                "assert(ok == true, 'placeStructure must round-trip the result')",
                "place_struct_test");

        assertEquals(1, interaction.placeCalls);
        assertEquals("minecraft:overworld", interaction.placeDimension, "dimension must default to overworld");
        assertEquals("mymod:shrine", interaction.placeTemplateId);
        assertArrayEquals(new int[] { 100, 64, -200, 90 }, interaction.placeArgs);
    }

    @Test
    public void placeStructureSupportsDimensionPrefixAndDefaultRotation() {
        interaction.placeResult = false;
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "local ok = World:PlaceStructure('luatweaker:crystal_realm', 'mymod:shrine', 1, 2, 3)\n" +
                "assert(ok == false, 'placeStructure must round-trip the result')",
                "place_struct_dim_test");

        assertEquals(1, interaction.placeCalls);
        assertEquals("luatweaker:crystal_realm", interaction.placeDimension);
        assertArrayEquals(new int[] { 1, 2, 3, 0 }, interaction.placeArgs, "rotation must default to 0");
    }

    @Test
    public void missingArgumentsReturnFalse() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "assert(World:PlaceStructure() == false, 'too few args must return false')\n" +
                "assert(World:PlaceStructure('mymod:shrine') == false, 'missing position must return false')",
                "place_struct_missing_test");

        assertEquals(0, interaction.placeCalls, "the platform must not be called for malformed input");
    }

    @Test
    public void camelCaseAliasesExist() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        ILuaValue fill = engine.getGlobalEnvironment().rawget("World").asTable().rawget("fillBlocks");
        assertNotNull(fill);
        assertTrue(fill.isFunction(), "World.fillBlocks alias must exist");

        ILuaValue replace = engine.getGlobalEnvironment().rawget("World").asTable().rawget("replaceBlocks");
        assertNotNull(replace);
        assertTrue(replace.isFunction(), "World.replaceBlocks alias must exist");

        ILuaValue place = engine.getGlobalEnvironment().rawget("World").asTable().rawget("placeStructure");
        assertNotNull(place);
        assertTrue(place.isFunction(), "World.placeStructure alias must exist");
    }

    @Test
    public void playSoundPassesPositionVolumePitchAndSource() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "World:PlaySound('minecraft:entity.experience_orb.pickup', 1, 2, 3, 0.5, 1.5, 'HOSTILE')\n" +
                "World:PlaySound('luatweaker:crystal_realm', 'minecraft:block.note_block.harp', " +
                "{ X = 4, Y = 5, Z = 6 }, 0.25, 2.0, 'BLOCKS')",
                "play_sound_args");

        assertEquals(2, interaction.soundCalls);
        assertArrayEquals(new double[] { 4, 5, 6 }, interaction.soundPos,
                "the second call must use the Vector3 table");
        assertEquals("minecraft:block.note_block.harp", interaction.soundId);
        assertEquals("luatweaker:crystal_realm", interaction.soundDimension);
        assertEquals(0.25f, interaction.soundVolume);
        assertEquals(2.0f, interaction.soundPitch);
        assertEquals("BLOCKS", interaction.soundSource);
    }

    @Test
    public void playSoundWithoutPositionNeverReachesThePlatform() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "assert(World:PlaySound('minecraft:entity.experience_orb.pickup') == nil)\n" +
                "assert(World:PlaySound('minecraft:entity.experience_orb.pickup', 'PLAYERS') == nil)\n" +
                "World:SpawnParticle('minecraft:flame')",
                "sound_missing_position");

        assertEquals(0, interaction.soundCalls,
                "defaulting a missing position to 0,0,0 would play at the world origin");
    }

    @Test
    public void stopSoundDisambiguatesSourceFromDimension() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "assert(World:StopSound('minecraft:block.note_block.harp', 'PLAYERS') == true)",
                "stop_sound_source");

        assertEquals("minecraft:overworld", interaction.soundDimension,
                "'PLAYERS' is a source, so the first string must stay the sound id");
        assertEquals("minecraft:block.note_block.harp", interaction.stoppedSoundId);
        assertEquals("PLAYERS", interaction.stoppedSoundSource);

        executeLua(engine,
                "assert(World:StopSound('minecraft:the_nether', 'minecraft:block.note_block.harp') == true)",
                "stop_sound_dimension");

        assertEquals("minecraft:the_nether", interaction.soundDimension,
                "a non-source second string makes the first string the dimension");
    }

    @Test
    public void particleArgumentsAreFullyParsed() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);

        executeLua(engine,
                "World:SpawnParticle('minecraft:flame', { X = 1, Y = 2, Z = 3 }, " +
                "{ X = 0.1, Y = 0.2, Z = 0.3 }, 12, 0.5)",
                "particle_vector_args");

        assertEquals("minecraft:flame", interaction.particleId);
        assertArrayEquals(new double[] { 1, 2, 3 }, interaction.particlePos);
        assertEquals(12, interaction.particleCount);
        assertEquals(0.5, interaction.particleSpeed, 1e-9);
    }
}
