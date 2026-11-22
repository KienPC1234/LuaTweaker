package com.luatweaker.common.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DimensionLuaBinding;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated dimension JSON must reference its own type and the Lua generator. */
class DimensionJsonTest {

    private static final String DIMENSION_KEY = "data/arcane/dimension/crystal_realm.json";

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
    }

    private static String arcaneDimensionJson() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);

        engine.executeString(
            "Dimensions:Create('arcane:crystal_realm', {\n"
            + "    hasSkyLight = false,\n"
            + "    skyColor = 0xFF88FF,\n"
            + "    seaLevel = 40,\n"
            + "    minHeight = -32,\n"
            + "    maxHeight = 128,\n"
            + "    surfaceBlock = 'arcane:crystal_grass'\n"
            + "})\n",
            "setup"
        );

        Object serviceObj = LuaServiceRegistry.get("DimensionServiceImpl");
        assertTrue(serviceObj instanceof DimensionServiceImpl,
                "DimensionServiceImpl must be in the registry");

        DatapackServiceImpl datapack = new DatapackServiceImpl();
        MinecraftDimensionProvider.applyAll(datapack);

        String dimensionJson = datapack.getData(DIMENSION_KEY);
        assertTrue(dimensionJson != null,
                "dimension JSON must be written");
        return dimensionJson;
    }

    @Test
    void dimensionReferencesItsOwnType() {
        // CRITICAL: the dimension must reference ITS OWN dimension type, so the
        // custom min_y/height/seaLevel settings actually apply.
        assertTrue(arcaneDimensionJson().contains("\"type\":\"arcane:crystal_realm\""),
                "dimension must reference its own custom dimension type");
    }

    @Test
    void generatorUsesTheLuaCodec() {
        assertTrue(arcaneDimensionJson().contains("\"type\":\"luatweaker:lua\""),
                "generator must use the Lua codec");
    }

    @Test
    void generatorCarriesTheDimensionId() {
        assertTrue(arcaneDimensionJson().contains("\"dimension_id\":\"arcane:crystal_realm\""),
                "generator must carry the dimension id");
    }

    @Test
    void generatorEmbedsABiomeSource() {
        assertTrue(arcaneDimensionJson().contains("\"biome_source\""),
                "generator must embed a biome source");
    }
}
