package com.luatweaker.common.worldgen;

import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Removing a vanilla ore must emit a remove_features modifier for its dimension. */
class OreRemovalTest {

    private static final String COAL_ORE = "minecraft:coal_ore";
    private static final String OVERWORLD = "minecraft:overworld";

    private static Map<String, String> removalFiles() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.removeOre(COAL_ORE, OVERWORLD);
        return WorldgenFixtures.apply(service);
    }

    private static String removalModifier() {
        String removal = removalFiles().values().stream()
                .filter(json -> json.contains("neoforge:remove_features"))
                .findFirst().orElse(null);

        assertNotNull(removal,
                "removeOre must produce a remove_features biome modifier");
        return removal;
    }

    @Test
    void removalTargetsUpperCoal() {
        assertTrue(removalModifier().contains("minecraft:ore_coal_upper"),
                "the removal must target upper coal ore");
    }

    @Test
    void removalTargetsLowerCoal() {
        assertTrue(removalModifier().contains("minecraft:ore_coal_lower"),
                "the removal must target lower coal ore");
    }

    @Test
    void removalTargetsTheOverworldTag() {
        assertTrue(removalModifier().contains("\"biomes\":\"#minecraft:is_overworld\""),
                "the removal must target the overworld biome tag");
    }

    @Test
    void unknownTargetsAreSkippedLoudly() {
        WorldgenServiceImpl service = new WorldgenServiceImpl();
        service.removeOre("minecraft:not_an_ore", OVERWORLD);
        service.addOre("mymod:weird_ore", "minecraft:custom_dim", -64, 32, 8, 10);

        assertTrue(WorldgenFixtures.apply(service).isEmpty(),
                "no datapack files may be generated for unsupported targets");
    }
}
