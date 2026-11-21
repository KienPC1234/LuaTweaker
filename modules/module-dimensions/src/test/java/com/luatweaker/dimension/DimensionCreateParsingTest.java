package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that {@code Dimensions:Create} parses every supported config field into
 * an immutable {@link DimensionConfig}. Each test compares one whole config snapshot,
 * so a mismatch reports every parsed value at once.
 */
class DimensionCreateParsingTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:test";

    private record FullConfig(boolean skyLight, int skyColor, double ambientLight, int seaLevel,
                              int minHeight, int maxHeight, int logicalHeight, long fixedTime,
                              String surfaceBlock, List<String> biomeIds, List<Integer> biomeWeights,
                              List<Integer> spawnMaxGroups) {
        static FullConfig snapshot(DimensionConfig config) {
            return new FullConfig(config.hasSkyLight(), config.skyColor(), config.ambientLight(),
                    config.seaLevel(), config.minHeight(), config.maxHeight(), config.logicalHeight(),
                    config.fixedTime(), config.surfaceBlock(),
                    config.biomes().stream().map(DimensionConfig.BiomeEntry::id).toList(),
                    config.biomes().stream().map(DimensionConfig.BiomeEntry::weight).toList(),
                    config.spawnEntities().stream().map(DimensionConfig.SpawnEntry::maxGroup).toList());
        }
    }

    private record DefaultConfig(boolean skyLight, int seaLevel, int minHeight, int maxHeight,
                                 String surfaceBlock, String fillerBlock, int skyColor,
                                 int biomeCount, int spawnCount, Long fixedTime) {
        static DefaultConfig snapshot(DimensionConfig config) {
            return new DefaultConfig(config.hasSkyLight(), config.seaLevel(), config.minHeight(),
                    config.maxHeight(), config.surfaceBlock(), config.fillerBlock(), config.skyColor(),
                    config.biomes().size(), config.spawnEntities().size(), config.fixedTime());
        }
    }

    private record ExtraConfig(String waterBlock, boolean bedrock, int biomeSize,
                               Integer spawnX, Integer spawnZ) {
        static ExtraConfig snapshot(DimensionConfig config) {
            return new ExtraConfig(config.waterBlock(), config.hasBedrock(), config.biomeSize(),
                    config.spawnX(), config.spawnZ());
        }
    }

    private record AdvancedType(boolean piglinSafe, boolean hasRaids, int monsterSpawnLightLevel,
                                int monsterSpawnBlockLightLimit, String infiniburn,
                                String effectsLocation) {
        static AdvancedType snapshot(DimensionConfig config) {
            return new AdvancedType(config.piglinSafe(), config.hasRaids(),
                    config.monsterSpawnLightLevel(), config.monsterSpawnBlockLightLimit(),
                    config.infiniburn(), config.effectsLocation());
        }
    }

    @Test
    void createParsesFullConfig() {
        ILuaTable config = engine.createTable();
        config.rawset("hasSkyLight", false);
        config.rawset("skyColor", 0xFF88FF);
        config.rawset("ambientLight", 0.25);
        config.rawset("seaLevel", 32);
        config.rawset("minHeight", -64);
        config.rawset("maxHeight", 256);
        config.rawset("surfaceBlock", "arcane:crystal_grass");
        config.rawset("subsurfaceBlock", "arcane:crystal_dirt");
        config.rawset("fillerBlock", "minecraft:stone");
        config.rawset("fixedTime", 6000.0);
        config.rawset("biomes", this.biomeList());
        config.rawset("spawnEntities", this.spawnList());

        service.create("arcane:crystal_realm", config);

        assertEquals(new FullConfig(false, 0xFF88FF, 0.25, 32, -64, 256, 320, 6000L,
                        "arcane:crystal_grass", List.of("arcane:crystal_plains", "minecraft:plains"),
                        List.of(5, 1), List.of(3)),
                FullConfig.snapshot(service.getConfig("arcane:crystal_realm")),
                "create must parse every supported config field");
    }

    @Test
    void createUsesDefaultsForMissingFields() {
        service.create(DIMENSION_ID, engine.createTable());

        assertEquals(new DefaultConfig(true, 63, -64, 320, "minecraft:grass_block", "minecraft:stone",
                        0x78A7FF, 0, 0, null),
                DefaultConfig.snapshot(service.getConfig(DIMENSION_ID)),
                "missing config fields must fall back to vanilla-safe defaults");
    }

    @Test
    void createParsesWaterBiomeBedrockAndSpawnFields() {
        ILuaTable config = engine.createTable();
        config.rawset("waterBlock", "minecraft:lava");
        config.rawset("hasBedrock", true);
        config.rawset("biomeSize", 16);
        config.rawset("spawnX", 100);
        config.rawset("spawnZ", -200);
        service.create(DIMENSION_ID, config);

        assertEquals(new ExtraConfig("minecraft:lava", true, 16, 100, -200),
                ExtraConfig.snapshot(service.getConfig(DIMENSION_ID)),
                "create must parse water block, bedrock, biome size and spawn point");
    }

    @Test
    void createDefaultsOptionalFields() {
        service.create(DIMENSION_ID, engine.createTable());

        assertEquals(new ExtraConfig("minecraft:water", false, 4, null, null),
                ExtraConfig.snapshot(service.getConfig(DIMENSION_ID)),
                "optional dimension fields must default when omitted");
    }

    @Test
    void createParsesAdvancedDimensionTypeFields() {
        ILuaTable config = engine.createTable();
        config.rawset("piglinSafe", true);
        config.rawset("hasRaids", true);
        config.rawset("monsterSpawnLightLevel", 5);
        config.rawset("monsterSpawnBlockLightLimit", 3);
        config.rawset("infiniburn", "#minecraft:infiniburn_nether");
        config.rawset("effectsLocation", "minecraft:the_nether");
        service.create(DIMENSION_ID, config);

        assertEquals(new AdvancedType(true, true, 5, 3, "#minecraft:infiniburn_nether",
                        "minecraft:the_nether"),
                AdvancedType.snapshot(service.getConfig(DIMENSION_ID)),
                "create must parse advanced dimension type fields");
    }

    @Test
    void createTwiceOverwritesConfig() {
        ILuaTable first = engine.createTable();
        first.rawset("seaLevel", 10);
        ILuaTable second = engine.createTable();
        second.rawset("seaLevel", 90);
        service.create(DIMENSION_ID, first);
        service.create(DIMENSION_ID, second);

        assertEquals(List.of(90, 1), List.of(service.getConfig(DIMENSION_ID).seaLevel(),
                        service.getDimensionIds().size()),
                "re-creating a dimension must replace its config and keep one id");
    }

    @Test
    void createWithoutSpawnPairLeavesSpawnUnset() {
        service.create(DIMENSION_ID, engine.createTable());

        assertNull(service.getConfig(DIMENSION_ID).spawnX(),
                "a dimension created without a spawn pair must report no spawn X");
    }

    private ILuaTable biomeList() {
        ILuaTable first = engine.createTable();
        first.rawset("id", "arcane:crystal_plains");
        first.rawset("weight", 5);
        ILuaTable second = engine.createTable();
        second.rawset("id", "minecraft:plains");
        ILuaTable biomes = engine.createTable();
        biomes.rawset(1, first);
        biomes.rawset(2, second);
        return biomes;
    }

    private ILuaTable spawnList() {
        ILuaTable spawn = engine.createTable();
        spawn.rawset("entity", "arcane:crystal_elemental");
        spawn.rawset("weight", 10);
        spawn.rawset("minGroup", 1);
        spawn.rawset("maxGroup", 3);
        ILuaTable spawns = engine.createTable();
        spawns.rawset(1, spawn);
        return spawns;
    }
}
