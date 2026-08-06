package com.luatweaker.platform.worldgen;

import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The worldgen provider must turn every Lua-registered entry into a datapack
 * file that honors the requested dimension, biome filter and removal target -
 * nothing may silently fall back to the overworld or drop the entry.
 */
public class NeoForgeWorldgenProviderTest {

    private Map<String, String> apply(WorldgenServiceImpl service) {
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        new NeoForgeWorldgenProvider(service, datapack).applyAll();
        return datapack.getVirtualFiles();
    }

    @Test
    public void overworldOreUsesOverworldTagAndStoneTargets() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);

        Map<String, String> files = apply(service);
        assertTrue(files.size() >= 3, "configured + placed + biome modifier expected");

        String modifier = files.values().stream()
                .filter(j -> j.contains("neoforge:add_features"))
                .findFirst().orElse(null);
        assertNotNull(modifier);
        assertTrue(modifier.contains("\"biomes\":\"#minecraft:is_overworld\""));
        assertTrue(modifier.contains("\"features\":[\"luatweaker:mymod_ruby_ore_ore_0\"]"));

        String configured = files.values().stream()
                .filter(j -> j.contains("minecraft:ore\"") || j.contains("\"type\":\"minecraft:ore\""))
                .findFirst().orElse(null);
        assertNotNull(configured);
        assertTrue(configured.contains("discard_chance_on_air_exposure"), "vanilla field name required");
        assertTrue(configured.contains("minecraft:stone_ore_replaceables"));
        assertTrue(configured.contains("minecraft:deepslate_ore_replaceables"));
        assertTrue(configured.contains("\"size\":8"));
    }

    @Test
    public void netherOreUsesNetherTagAndNetherrackTarget() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addOre("mymod:nether_ruby", "minecraft:the_nether", 0, 64, 6, 4);

        Map<String, String> files = apply(service);
        String modifier = files.values().stream()
                .filter(j -> j.contains("neoforge:add_features"))
                .findFirst().orElse(null);
        assertNotNull(modifier);
        assertTrue(modifier.contains("\"biomes\":\"#minecraft:is_nether\""));

        String configured = files.values().stream()
                .filter(j -> j.contains("\"type\":\"minecraft:ore\""))
                .findFirst().orElse(null);
        assertNotNull(configured);
        assertTrue(configured.contains("\"block\":\"minecraft:netherrack\""));
    }

    @Test
    public void biomeFilteredOreUsesExplicitBiomeList() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addOreBiomeFiltered("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10,
                new String[]{"minecraft:plains", "minecraft:forest"});

        Map<String, String> files = apply(service);
        String modifier = files.values().stream()
                .filter(j -> j.contains("neoforge:add_features"))
                .findFirst().orElse(null);
        assertNotNull(modifier);
        assertTrue(modifier.contains("\"biomes\":[\"minecraft:plains\",\"minecraft:forest\"]"));
        assertFalse(modifier.contains("is_overworld"), "explicit biome list must win over the dimension tag");
    }

    @Test
    public void vegetationEmitsOnePlacedFeaturePerBiome() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addVegetation("mymod:crystal_flower", 0.1, new String[]{"minecraft:plains", "minecraft:desert"});

        Map<String, String> files = apply(service);
        long placed = files.keySet().stream().filter(k -> k.contains("placed_feature/")).count();
        assertEquals(2, placed, "one placed feature per biome (multiple 'biome' placements would AND together)");

        long biomePlacements = files.values().stream()
                .filter(j -> j.contains("\"type\":\"minecraft:biome\",\"biome\":"))
                .count();
        assertEquals(2, biomePlacements);
        assertTrue(files.values().stream().anyMatch(j -> j.contains("\"biome\":\"minecraft:plains\"")));
        assertTrue(files.values().stream().anyMatch(j -> j.contains("\"biome\":\"minecraft:desert\"")));
        assertTrue(files.values().stream().anyMatch(j -> j.contains("\"chance\":10")));
    }

    @Test
    public void removalOfVanillaOreEmitsRemoveFeaturesModifier() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.removeOre("minecraft:coal_ore", "minecraft:overworld");

        Map<String, String> files = apply(service);
        String removal = files.values().stream()
                .filter(j -> j.contains("neoforge:remove_features"))
                .findFirst().orElse(null);
        assertNotNull(removal, "removeOre must produce a remove_features biome modifier");
        assertTrue(removal.contains("minecraft:ore_coal_upper"));
        assertTrue(removal.contains("minecraft:ore_coal_lower"));
        assertTrue(removal.contains("\"biomes\":\"#minecraft:is_overworld\""));
    }

    @Test
    public void unknownRemovalAndUnsupportedDimensionAreSkippedLoudly() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.removeOre("minecraft:not_an_ore", "minecraft:overworld");
        service.addOre("mymod:weird_ore", "minecraft:custom_dim", -64, 32, 8, 10);

        Map<String, String> files = apply(service);
        assertTrue(files.isEmpty(), "no datapack files may be generated for unsupported targets");
    }

    @Test
    public void everyGeneratedJsonMustBeValid() {
        // Regression: the overworld ore targets once started with a stray '{'
        // producing "{{"target" - MalformedJsonException at $.config.targets[0]
        // which crashed world load in RegistryDataLoader.
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);
        service.addOre("mymod:nether_ruby", "minecraft:the_nether", 0, 64, 6, 4);
        service.addOre("mymod:end_ruby", "minecraft:the_end", 0, 64, 6, 4);
        service.addVegetation("mymod:flower", 0.1, new String[]{"minecraft:plains"});
        service.removeOre("minecraft:coal_ore", "minecraft:overworld");

        Map<String, String> files = apply(service);
        assertFalse(files.isEmpty());
        for (Map.Entry<String, String> entry : files.entrySet()) {
            assertDoesNotThrow(() -> com.google.gson.JsonParser.parseString(entry.getValue()),
                    "generated JSON must parse: " + entry.getKey() + " = " + entry.getValue());
        }

        // The overworld targets array must have exactly 2 valid target objects.
        String overworldOre = files.values().stream()
                .filter(j -> j.contains("minecraft:stone_ore_replaceables"))
                .findFirst().orElse(null);
        assertNotNull(overworldOre);
        var parsed = com.google.gson.JsonParser.parseString(overworldOre).getAsJsonObject();
        assertEquals(2, parsed.getAsJsonObject("config").getAsJsonArray("targets").size());
    }
}
