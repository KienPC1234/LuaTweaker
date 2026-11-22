package com.luatweaker.common.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DimensionLuaBinding;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated dimension type JSON must carry every configured field. */
class DimensionTypeJsonTest {

    private static final String TYPE_KEY = "data/arcane/dimension_type/crystal_realm.json";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
    }

    private static String arcaneTypeJson() {
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

        String typeJson = datapack.getData(TYPE_KEY);
        assertTrue(typeJson != null,
                "dimension type JSON must be written");
        return typeJson;
    }

    @Test
    void typeJsonCarriesSkyLight() {
        assertTrue(arcaneTypeJson().contains("\"has_skylight\":false"),
                "type JSON must carry has_skylight");
    }

    @Test
    void typeJsonCarriesMinY() {
        assertTrue(arcaneTypeJson().contains("\"min_y\":-32"),
                "type JSON must carry min_y");
    }

    @Test
    void typeJsonCarriesHeight() {
        assertTrue(arcaneTypeJson().contains("\"height\":160"),
                "type JSON must carry height = maxY - minY");
    }

    @Test
    void typeJsonCarriesCoordinateScale() {
        assertTrue(arcaneTypeJson().contains("\"coordinate_scale\":1.0"),
                "type JSON must carry coordinate_scale");
    }

    @Test
    void typeJsonReferencesCustomSkyEffects() {
        assertTrue(arcaneTypeJson().contains("\"effects\":\"luatweaker:lua\""),
                "type JSON must reference the custom sky effects");
    }

    @Test
    void typeJsonPassesTheVanillaCodec() {
        String typeJson = arcaneTypeJson();
        // Parse the generated JSON with the real vanilla codec: this catches missing or
        // misspelled fields and the DimensionType min_y/height/logical_height constraints.
        var parsedType = net.minecraft.world.level.dimension.DimensionType.DIRECT_CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString(typeJson));

        assertTrue(parsedType.result().isPresent(),
                () -> "vanilla DimensionType codec rejected the generated JSON: " + parsedType.error());
    }
}
