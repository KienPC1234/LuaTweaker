package com.luatweaker.common.worldgen;

import com.google.gson.JsonParser;
import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every generated worldgen JSON must parse as valid JSON. */
class WorldgenJsonValidityTest {

    private static Map<String, String> mixedFiles() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.addOre("mymod:ruby_ore", "minecraft:overworld", -64, 32, 8, 10);
        service.addOre("mymod:nether_ruby", "minecraft:the_nether", 0, 64, 6, 4);
        service.addOre("mymod:end_ruby", "minecraft:the_end", 0, 64, 6, 4);
        service.addVegetation("mymod:flower", 0.1, "minecraft:plains");
        service.removeOre("minecraft:coal_ore", "minecraft:overworld");
        return WorldgenFixtures.apply(service);
    }

    @Test
    void filesAreGenerated() {
        assertFalse(mixedFiles().isEmpty(),
                "the mixed registration must generate datapack files");
    }

    @Test
    void everyFileParsesAsJson() {
        Map<String, String> files = mixedFiles();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            assertDoesNotThrow(() -> JsonParser.parseString(entry.getValue()),
                    "generated JSON must parse: " + entry.getKey() + " = " + entry.getValue());
        }
    }

    @Test
    void overworldOrePassesThrough() {
        // Regression: the overworld ore targets once started with a stray '{'
        // producing "{{"target" - MalformedJsonException at $.config.targets[0]
        // which crashed world load in RegistryDataLoader.
        String overworldOre = mixedFiles().values().stream()
                .filter(json -> json.contains("minecraft:stone_ore_replaceables"))
                .findFirst().orElse(null);

        assertNotNull(overworldOre,
                "the overworld ore feature must be generated");
    }

    @Test
    void overworldTargetsHoldTwoValidObjects() {
        String overworldOre = mixedFiles().values().stream()
                .filter(json -> json.contains("minecraft:stone_ore_replaceables"))
                .findFirst().orElseThrow(() -> new IllegalStateException("overworld ore missing"));
        var parsed = JsonParser.parseString(overworldOre).getAsJsonObject();

        assertEquals(2, parsed.getAsJsonObject("config").getAsJsonArray("targets").size(),
                "the overworld targets array must hold exactly 2 valid target objects");
    }
}
