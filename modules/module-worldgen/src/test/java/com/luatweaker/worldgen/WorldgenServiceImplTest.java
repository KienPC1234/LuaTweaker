package com.luatweaker.worldgen;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class WorldgenServiceImplTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private WorldgenServiceImpl service;

    @BeforeEach
    void setup() {
        service = new WorldgenServiceImpl();
    }

    @Test
    void testAddOre_StoresEntry() {
        service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);

        List<WorldgenServiceImpl.OreEntry> ores = service.getPendingOres();
        assertEquals(1, ores.size());

        WorldgenServiceImpl.OreEntry entry = ores.get(0);
        assertEquals("mymod:ruby_ore", entry.blockId());
        assertEquals("minecraft:overworld", entry.dimension());
        assertEquals(-64, entry.minHeight());
        assertEquals(32, entry.maxHeight());
        assertEquals(8, entry.clusterSize());
        assertEquals(10, entry.frequency());
        assertTrue(entry.biomes().isEmpty());
    }

    @Test
    void testAddOreBiomeFiltered_StoresBiomes() {
        service.addOreBiomeFiltered("mymod:ruby_ore", "minecraft:overworld",
                -64, 32, 8, 10, new String[]{"minecraft:plains", "minecraft:forest"});

        WorldgenServiceImpl.OreEntry entry = service.getPendingOres().get(0);
        assertEquals(2, entry.biomes().size());
        assertEquals("minecraft:plains", entry.biomes().get(0));
    }

    @Test
    void testAddOreBiomeFiltered_RejectsEmptyBiomes() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOreBiomeFiltered("mymod:ruby_ore", "minecraft:overworld",
                -64, 32, 8, 10, new String[]{}));
    }

    @Test
    void testAddOre_RejectsBlankBlockId() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOre("", "minecraft:overworld", -64, 32, 8, 10));
    }

    @Test
    void testAddOre_RejectsInvalidHeightRange() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOre("mymod:ruby_ore", "minecraft:overworld", 32, -64, 8, 10));
    }

    @Test
    void testAddOre_RejectsZeroClusterSize() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 0, 10));
    }

    @Test
    void testAddOre_RejectsZeroFrequency() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 0));
    }

    @Test
    void testAddVegetation_StoresEntry() {
        service.addVegetation("mymod:crystal_flower", 0.1, new String[]{"minecraft:plains"});

        List<WorldgenServiceImpl.VegetationEntry> veg = service.getPendingVegetation();
        assertEquals(1, veg.size());
        assertEquals("mymod:crystal_flower", veg.get(0).blockId());
        assertEquals(0.1, veg.get(0).chance());
    }

    @Test
    void testAddVegetation_RejectsInvalidChance() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addVegetation("mymod:flower", 1.5, new String[]{"minecraft:plains"}));
    }

    @Test
    void testAddVegetation_RejectsEmptyBiomes() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addVegetation("mymod:flower", 0.5, new String[]{}));
    }

    @Test
    void testRemoveOre_StoresEntry() {
        service.removeOre("minecraft:coal_ore", "minecraft:overworld");

        List<WorldgenServiceImpl.OreRemovalEntry> removals = service.getPendingRemovals();
        assertEquals(1, removals.size());
        assertEquals("minecraft:coal_ore", removals.get(0).blockId());
    }

    @Test
    void testRemoveOre_RejectsBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            service.removeOre("", "minecraft:overworld"));
    }

    @Test
    void testGetModifications_ReturnsCorrectSummary() {
        service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);
        service.addOre("mymod:sapphire_ore", "minecraft:overworld", -64, 16, 6, 5);
        service.addVegetation("mymod:flower", 0.1, new String[]{"minecraft:plains"});
        service.removeOre("minecraft:coal_ore", "minecraft:overworld");

        Map<String, Object> summary = service.getModifications();
        assertEquals(2, summary.get("ores"));
        assertEquals(1, summary.get("vegetation"));
        assertEquals(1, summary.get("removals"));
    }

    @Test
    void testClearAll_RemovesEverything() {
        service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);
        service.addVegetation("mymod:flower", 0.1, new String[]{"minecraft:plains"});
        service.removeOre("minecraft:coal_ore", "minecraft:overworld");

        service.clearAll();

        assertTrue(service.getPendingOres().isEmpty());
        assertTrue(service.getPendingVegetation().isEmpty());
        assertTrue(service.getPendingRemovals().isEmpty());
    }

    // ---- modifyBiome ----

    @Test
    void testModifyBiome_AddFeatures_EmitsVerifiedModifierJson() {
        FakeLuaTable addEntry = new FakeLuaTable()
                .put("feature", "mymod:ruby_stone")
                .put("step", "underground_ores");
        FakeLuaTable options = new FakeLuaTable()
                .put("biomes", FakeLuaTable.listOf("minecraft:plains", "minecraft:forest"))
                .put("add", FakeLuaTable.listOf(addEntry));

        service.modifyBiome("minecraft:plains", options);

        assertEquals(1, service.getPendingBiomeMods().size());
        Map<String, String> files = service.buildBiomeModifierFiles();
        assertEquals(1, files.size());
        String path = files.keySet().iterator().next();
        assertTrue(path.startsWith("luatweaker/neoforge/biome_modifier/"), "unexpected path: " + path);
        assertTrue(path.endsWith(".json"), "unexpected path: " + path);
        String json = files.values().iterator().next();
        assertTrue(json.contains("\"type\":\"neoforge:add_features\""), json);
        assertTrue(json.contains("\"biomes\":[\"minecraft:plains\",\"minecraft:forest\"]"), json);
        assertTrue(json.contains("\"features\":[\"mymod:ruby_stone\"]"), json);
        assertTrue(json.contains("\"step\":\"underground_ores\""), json);
    }

    @Test
    void testModifyBiome_GroupsAddsByStep() {
        FakeLuaTable first = new FakeLuaTable()
                .put("feature", "mymod:ruby_stone")
                .put("step", "underground_ores");
        FakeLuaTable second = new FakeLuaTable()
                .put("feature", "mymod:glowing_berries")
                .put("step", "vegetal_decoration");
        FakeLuaTable options = new FakeLuaTable()
                .put("biome", "minecraft:plains")
                .put("add", FakeLuaTable.listOf(first, second));

        service.modifyBiome("minecraft:plains", options);

        Map<String, String> files = service.buildBiomeModifierFiles();
        assertEquals(2, files.size(), "one modifier file per generation step");
        assertTrue(files.values().stream().anyMatch(j -> j.contains("\"step\":\"underground_ores\"")));
        assertTrue(files.values().stream().anyMatch(j -> j.contains("\"step\":\"vegetal_decoration\"")));
        assertTrue(files.values().stream().anyMatch(j -> j.contains("\"features\":[\"mymod:glowing_berries\"]")));
    }

    @Test
    void testModifyBiome_Remove_EmitsPluralStepsJson() {
        FakeLuaTable options = new FakeLuaTable()
                .put("biomes", "minecraft:plains")
                .put("remove", FakeLuaTable.listOf("minecraft:ore_coal_upper", "minecraft:ore_coal_lower"));

        service.modifyBiome("minecraft:plains", options);

        Map<String, String> files = service.buildBiomeModifierFiles();
        assertEquals(1, files.size());
        String json = files.values().iterator().next();
        assertTrue(json.contains("\"type\":\"neoforge:remove_features\""), json);
        assertTrue(json.contains("\"features\":[\"minecraft:ore_coal_upper\",\"minecraft:ore_coal_lower\"]"), json);
        assertTrue(json.contains("\"steps\":[\"underground_ores\"]"), json);
        assertFalse(json.contains("\"step\":"), "remove modifier must use plural steps, got: " + json);
    }

    @Test
    void testModifyBiome_AcceptsTagBiomesAndRemoveSteps() {
        FakeLuaTable options = new FakeLuaTable()
                .put("biomes", FakeLuaTable.listOf("#minecraft:is_overworld"))
                .put("remove", "minecraft:ore_dirt")
                .put("removeSteps", FakeLuaTable.listOf("underground_ores", "vegetal_decoration"));

        service.modifyBiome("mymod:rocky_plains", options);

        String json = service.buildBiomeModifierFiles().values().iterator().next();
        assertTrue(json.contains("\"biomes\":[\"#minecraft:is_overworld\"]"), json);
        assertTrue(json.contains("\"steps\":[\"underground_ores\",\"vegetal_decoration\"]"), json);
    }

    @Test
    void testModifyBiome_RejectsBlankBiomeId() {
        FakeLuaTable options = new FakeLuaTable()
                .put("biomes", "minecraft:plains")
                .put("remove", "minecraft:ore_dirt");
        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome("", options));
    }

    @Test
    void testModifyBiome_RejectsEmptyBiomes() {
        FakeLuaTable options = new FakeLuaTable()
                .put("biomes", FakeLuaTable.listOf())
                .put("remove", "minecraft:ore_dirt");
        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome("minecraft:plains", options));
    }

    @Test
    void testModifyBiome_RejectsUnknownStep() {
        FakeLuaTable options = new FakeLuaTable()
                .put("biomes", "minecraft:plains")
                .put("step", "magic_sparkles")
                .put("add", "mymod:ruby_stone");
        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome("minecraft:plains", options));
    }

    @Test
    void testModifyBiome_RejectsAddWithoutRemove() {
        FakeLuaTable options = new FakeLuaTable().put("biomes", "minecraft:plains");
        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome("minecraft:plains", options));
    }

    @Test
    void testModifyBiome_RejectsInvalidBiomeEntry() {
        FakeLuaTable options = new FakeLuaTable()
                .put("biomes", FakeLuaTable.listOf("not a biome!!"))
                .put("remove", "minecraft:ore_dirt");
        assertThrows(IllegalArgumentException.class, () -> service.modifyBiome("minecraft:plains", options));
    }

    // ---- addFeature ----

    @Test
    void testAddFeature_EmitsConfiguredPlacedAndModifier() {
        FakeLuaTable spec = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", FakeLuaTable.listOf("minecraft:plains"));

        service.addFeature("mymod:ruby_vein", spec);

        assertEquals(1, service.getPendingFeatures().size());
        Map<String, String> files = service.buildBiomeModifierFiles();
        assertEquals(3, files.size());
        assertTrue(files.containsKey("luatweaker/worldgen/configured_feature/mymod_ruby_vein.json"));
        assertTrue(files.containsKey("luatweaker/worldgen/placed_feature/mymod_ruby_vein.json"));
        assertTrue(files.containsKey("luatweaker/neoforge/biome_modifier/add_mymod_ruby_vein.json"));

        String configured = files.get("luatweaker/worldgen/configured_feature/mymod_ruby_vein.json");
        assertTrue(configured.contains("\"type\":\"minecraft:ore\""), configured);
        assertTrue(configured.contains("\"size\":8"), configured);
        assertTrue(configured.contains("minecraft:stone_ore_replaceables"), configured);
        assertTrue(configured.contains("minecraft:deepslate_ore_replaceables"), configured);
        assertTrue(configured.contains("\"Name\":\"mymod:ruby_ore\""), configured);

        String placed = files.get("luatweaker/worldgen/placed_feature/mymod_ruby_vein.json");
        assertTrue(placed.contains("\"feature\":\"luatweaker:mymod_ruby_vein\""), placed);
        assertTrue(placed.contains("\"count\":10"), placed);
        assertTrue(placed.contains("\"absolute\":-64"), placed);
        assertTrue(placed.contains("\"absolute\":32"), placed);

        String modifier = files.get("luatweaker/neoforge/biome_modifier/add_mymod_ruby_vein.json");
        assertTrue(modifier.contains("\"type\":\"neoforge:add_features\""), modifier);
        assertTrue(modifier.contains("\"biomes\":[\"minecraft:plains\"]"), modifier);
        assertTrue(modifier.contains("\"features\":[\"luatweaker:mymod_ruby_vein\"]"), modifier);
        assertTrue(modifier.contains("\"step\":\"underground_ores\""), modifier);
    }

    @Test
    void testAddFeature_NetherReplace_UsesNetherrack() {
        FakeLuaTable spec = new FakeLuaTable()
                .put("block", "mymod:nether_ruby")
                .put("biome", "minecraft:nether_wastes")
                .put("replace", "nether")
                .put("count", 6)
                .put("size", 4)
                .put("minHeight", 0)
                .put("maxHeight", 64)
                .put("step", "underground_decoration");

        service.addFeature("mymod:nether_vein", spec);

        String configured = service.buildBiomeModifierFiles()
                .get("luatweaker/worldgen/configured_feature/mymod_nether_vein.json");
        assertNotNull(configured);
        assertTrue(configured.contains("\"block\":\"minecraft:netherrack\""), configured);
        assertTrue(configured.contains("\"size\":4"), configured);
        assertFalse(configured.contains("stone_ore_replaceables"), configured);
    }

    @Test
    void testAddFeature_RejectsBlankFeatureId() {
        FakeLuaTable spec = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", "minecraft:plains");
        assertThrows(IllegalArgumentException.class, () -> service.addFeature("", spec));
    }

    @Test
    void testAddFeature_RejectsMissingBlock() {
        FakeLuaTable spec = new FakeLuaTable().put("biomes", "minecraft:plains");
        assertThrows(IllegalArgumentException.class, () -> service.addFeature("mymod:ruby_vein", spec));
    }

    @Test
    void testAddFeature_RejectsEmptyBiomes() {
        FakeLuaTable spec = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", FakeLuaTable.listOf());
        assertThrows(IllegalArgumentException.class, () -> service.addFeature("mymod:ruby_vein", spec));
    }

    @Test
    void testAddFeature_RejectsBadHeightRange() {
        FakeLuaTable spec = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", "minecraft:plains")
                .put("minHeight", 32)
                .put("maxHeight", -64);
        assertThrows(IllegalArgumentException.class, () -> service.addFeature("mymod:ruby_vein", spec));
    }

    @Test
    void testAddFeature_RejectsZeroCountAndSize() {
        FakeLuaTable zeroCount = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", "minecraft:plains")
                .put("count", 0);
        assertThrows(IllegalArgumentException.class, () -> service.addFeature("mymod:ruby_vein", zeroCount));

        FakeLuaTable zeroSize = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", "minecraft:plains")
                .put("size", 0);
        assertThrows(IllegalArgumentException.class, () -> service.addFeature("mymod:ruby_vein", zeroSize));
    }

    @Test
    void testAddFeature_RejectsUnknownStepAndReplace() {
        FakeLuaTable badStep = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", "minecraft:plains")
                .put("step", "not_a_step");
        assertThrows(IllegalArgumentException.class, () -> service.addFeature("mymod:ruby_vein", badStep));

        FakeLuaTable badReplace = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", "minecraft:plains")
                .put("replace", "moon");
        assertThrows(IllegalArgumentException.class, () -> service.addFeature("mymod:ruby_vein", badReplace));
    }

    // ---- removal registry ----

    @Test
    void testRemovalDefaults_CoverVanillaOres() {
        assertEquals(List.of("minecraft:ore_coal_upper", "minecraft:ore_coal_lower"),
                service.resolveRemovalFeatures("minecraft:coal_ore"));
        assertEquals(List.of("minecraft:ore_quartz_nether"),
                service.resolveRemovalFeatures("minecraft:nether_quartz_ore"));
        assertEquals(List.of("minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small"),
                service.resolveRemovalFeatures("minecraft:ancient_debris"));
    }

    @Test
    void testRegisterRemovalFeatures_StoresCustomAndOverride() {
        assertTrue(service.registerRemovalFeatures("mymod:ruby_ore",
                new String[]{"mymod:ruby_ore_upper", "mymod:ruby_ore_lower"}));
        assertEquals(List.of("mymod:ruby_ore_upper", "mymod:ruby_ore_lower"),
                service.resolveRemovalFeatures("mymod:ruby_ore"));

        assertTrue(service.registerRemovalFeatures("minecraft:coal_ore",
                new String[]{"mymod:coal_vein"}));
        assertEquals(List.of("mymod:coal_vein"),
                service.resolveRemovalFeatures("minecraft:coal_ore"));
    }

    @Test
    void testRegisterRemovalFeatures_RejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () ->
            service.registerRemovalFeatures("", new String[]{"mymod:ruby_ore_upper"}));
        assertThrows(IllegalArgumentException.class, () ->
            service.registerRemovalFeatures("mymod:ruby_ore", new String[]{}));
        assertThrows(IllegalArgumentException.class, () ->
            service.registerRemovalFeatures("mymod:ruby_ore", new String[]{"not a feature!!"}));
    }

    @Test
    void testResolveRemovalFeatures_UnknownBlock_ReturnsEmpty() {
        assertTrue(service.resolveRemovalFeatures("unknownmod:unknown_ore").isEmpty());
    }

    // ---- dimension registry ----

    @Test
    void testDimensionDefaults_CoverVanillaDims() {
        assertEquals(Optional.of("#minecraft:is_overworld"),
                service.resolveDimensionBiomeTag("minecraft:overworld"));
        assertEquals(Optional.of("#minecraft:is_nether"),
                service.resolveDimensionBiomeTag("minecraft:the_nether"));
        assertEquals(Optional.of("#minecraft:is_end"),
                service.resolveDimensionBiomeTag("minecraft:the_end"));
        assertEquals(3, service.getKnownDimensions().size());
        assertEquals("#minecraft:is_overworld", service.getKnownDimensions().get("minecraft:overworld"));
    }

    @Test
    void testRegisterDimensionBiomeTag_StoresCustomDim() {
        assertTrue(service.registerDimensionBiomeTag(
                "twilightforest:twilight_forest", "#twilightforest:is_twilight_forest"));
        assertEquals(Optional.of("#twilightforest:is_twilight_forest"),
                service.resolveDimensionBiomeTag("twilightforest:twilight_forest"));
        assertEquals(4, service.getKnownDimensions().size());
    }

    @Test
    void testRegisterDimensionBiomeTag_RejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () ->
            service.registerDimensionBiomeTag("", "#minecraft:is_overworld"));
        assertThrows(IllegalArgumentException.class, () ->
            service.registerDimensionBiomeTag("mymod:dim", ""));
        assertThrows(IllegalArgumentException.class, () ->
            service.registerDimensionBiomeTag("mymod:dim", "#not a tag!!"));
    }

    @Test
    void testResolveDimensionBiomeTag_Unknown_ReturnsEmpty() {
        assertEquals(Optional.empty(), service.resolveDimensionBiomeTag("mymod:unknown_dim"));
    }

    // ---- vegetation kinds ----

    @Test
    void testAddVegetationPatch_StoresPatchKind() {
        service.addVegetationPatch("minecraft:short_grass", 0.2, new String[]{"minecraft:plains"});

        List<WorldgenServiceImpl.VegetationEntry> veg = service.getPendingVegetation();
        assertEquals(1, veg.size());
        assertEquals("patch", veg.get(0).kind());
        assertEquals("flower", serviceGetFlowerKind());
    }

    private String serviceGetFlowerKind() {
        service.addVegetation("mymod:flower", 0.1, new String[]{"minecraft:plains"});
        return service.getPendingVegetation().get(1).kind();
    }

    @Test
    void testVegetationFlowerJson_UsesTypedProvider() {
        service.addVegetation("mymod:crystal_flower", 0.1, new String[]{"minecraft:plains"});

        String json = service.buildVegetationConfiguredJson(service.getPendingVegetation().get(0));
        assertTrue(json.contains("\"type\":\"minecraft:flower\""), json);
        assertTrue(json.contains("\"tries\":64"), json);
        assertTrue(json.contains("\"type\":\"minecraft:simple_state_provider\""), json);
        assertTrue(json.contains("\"state\":{\"Name\":\"mymod:crystal_flower\"}"), json);
        assertTrue(json.contains("\"type\":\"minecraft:block_predicate_filter\""), json);
    }

    @Test
    void testVegetationPatchJson_UsesRandomPatch() {
        service.addVegetationPatch("minecraft:short_grass", 0.2, new String[]{"minecraft:plains"});

        String json = service.buildVegetationConfiguredJson(service.getPendingVegetation().get(0));
        assertTrue(json.contains("\"type\":\"minecraft:random_patch\""), json);
        assertTrue(json.contains("\"tries\":32"), json);
        assertTrue(json.contains("\"state\":{\"Name\":\"minecraft:short_grass\"}"), json);
        assertFalse(json.contains("\"type\":\"minecraft:flower\""), json);
    }

    @Test
    void testAddVegetationPatch_RejectsInvalidChance() {
        assertThrows(IllegalArgumentException.class, () ->
            service.addVegetationPatch("minecraft:short_grass", 1.5, new String[]{"minecraft:plains"}));
    }

    // ---- getBiome (unsupported stub) ----

    @Test
    void testGetBiome_ThrowsLoudUnsupported() {
        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class, () ->
            service.getBiome("minecraft:plains"));
        assertTrue(error.getMessage().contains("STUB"), error.getMessage());
        assertTrue(error.getMessage().contains("not supported"), error.getMessage());
    }

    // ---- summary and clear ----

    @Test
    void testGetModifications_IncludesNewCounts() {
        FakeLuaTable options = new FakeLuaTable()
                .put("biomes", "minecraft:plains")
                .put("remove", "minecraft:ore_dirt");
        service.modifyBiome("minecraft:plains", options);
        FakeLuaTable spec = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", "minecraft:plains");
        service.addFeature("mymod:ruby_vein", spec);

        Map<String, Object> summary = service.getModifications();
        assertEquals(1, summary.get("biomeModifiers"));
        assertEquals(1, summary.get("customFeatures"));
        assertEquals(0, summary.get("ores"));
    }

    @Test
    void testClearAll_ClearsNewPendingButKeepsRegistries() {
        FakeLuaTable options = new FakeLuaTable()
                .put("biomes", "minecraft:plains")
                .put("remove", "minecraft:ore_dirt");
        service.modifyBiome("minecraft:plains", options);
        FakeLuaTable spec = new FakeLuaTable()
                .put("block", "mymod:ruby_ore")
                .put("biomes", "minecraft:plains");
        service.addFeature("mymod:ruby_vein", spec);
        service.registerRemovalFeatures("mymod:ruby_ore", new String[]{"mymod:ruby_ore_upper"});
        service.registerDimensionBiomeTag("mymod:deep", "#mymod:is_deep");

        service.clearAll();

        assertTrue(service.getPendingBiomeMods().isEmpty());
        assertTrue(service.getPendingFeatures().isEmpty());
        assertTrue(service.buildBiomeModifierFiles().isEmpty());
        assertEquals(List.of("mymod:ruby_ore_upper"), service.resolveRemovalFeatures("mymod:ruby_ore"));
        assertEquals(Optional.of("#mymod:is_deep"), service.resolveDimensionBiomeTag("mymod:deep"));
    }
}
