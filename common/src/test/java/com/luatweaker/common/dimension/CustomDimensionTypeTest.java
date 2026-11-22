package com.luatweaker.common.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DimensionLuaBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Custom dimension type fields must flow from the Lua config into the type JSON. */
class CustomDimensionTypeTest {

    private static final String TYPE_KEY = "data/minecraft/dimension_type/custom.json";

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
    }

    private static String customTypeJson() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "Dimensions:Create('minecraft:custom', {\n"
            + "    piglinSafe = true,\n"
            + "    hasRaids = true,\n"
            + "    monsterSpawnLightLevel = 5,\n"
            + "    monsterSpawnBlockLightLimit = 3,\n"
            + "    infiniburn = '#minecraft:infiniburn_nether',\n"
            + "    effectsLocation = 'minecraft:the_nether'\n"
            + "})\n",
            "setup_custom_type"
        );

        DatapackServiceImpl datapack = new DatapackServiceImpl();
        MinecraftDimensionProvider.applyAll(datapack);

        String typeJson = datapack.getData(TYPE_KEY);
        assertTrue(typeJson != null,
                "dimension type JSON must be written");
        return typeJson;
    }

    @Test
    void piglinSafeComesFromConfig() {
        assertTrue(customTypeJson().contains("\"piglin_safe\":true"),
                "piglin_safe must come from config");
    }

    @Test
    void hasRaidsComesFromConfig() {
        assertTrue(customTypeJson().contains("\"has_raids\":true"),
                "has_raids must come from config");
    }

    @Test
    void monsterLightLevelIsAKeyedIntProvider() {
        assertTrue(customTypeJson().contains(
                        "\"monster_spawn_light_level\":{\"type\":\"minecraft:constant\",\"value\":5}"),
                "monster light level must be a keyed IntProvider "
                        + "(constant decodes on 1.21.1; vanilla overworld uses uniform)");
    }

    @Test
    void blockLightLimitComesFromConfig() {
        assertTrue(customTypeJson().contains("\"monster_spawn_block_light_limit\":3"),
                "block light limit must come from config");
    }

    @Test
    void infiniburnComesFromConfig() {
        assertTrue(customTypeJson().contains("\"infiniburn\":\"#minecraft:infiniburn_nether\""),
                "infiniburn must come from config");
    }

    @Test
    void effectsComeFromConfig() {
        assertTrue(customTypeJson().contains("\"effects\":\"minecraft:the_nether\""),
                "effects must come from config");
    }
}
