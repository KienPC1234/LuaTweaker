package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the read-only info table returned by {@code Dimensions:GetDimension}. */
class DimensionInfoTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "arcane:crystal_realm";
    private static final String PORTAL_BLOCK = "arcane:crystal_portal";

    private record InfoView(String id, int seaLevel, String surfaceBlock, String waterBlock,
                            boolean bedrock, int biomeSize, int spawnX, int spawnZ,
                            boolean hasBlockPicker, String portalTarget) {
        static InfoView snapshot(ILuaTable info) {
            ILuaTable portals = info.rawget("portals").asTable();
            return new InfoView(info.rawget("id").asString(), info.rawget("seaLevel").asInt(),
                    info.rawget("surfaceBlock").asString(), info.rawget("waterBlock").asString(),
                    info.rawget("hasBedrock").asBoolean(), info.rawget("biomeSize").asInt(),
                    info.rawget("spawnX").asInt(), info.rawget("spawnZ").asInt(),
                    info.rawget("hasBlockPicker").asBoolean(),
                    portals.rawget(PORTAL_BLOCK).asString());
        }
    }

    @Test
    void infoTableDescribesDimension() {
        service.create(DIMENSION_ID, configuredDimension());
        service.registerPortal(PORTAL_BLOCK, DIMENSION_ID);
        service.setSpawnPoint(DIMENSION_ID, 7, 9);

        ILuaTable info = (ILuaTable) service.getDimension(DIMENSION_ID);

        assertEquals(new InfoView(DIMENSION_ID, 40, "arcane:crystal_grass", "minecraft:lava",
                        true, 12, 7, 9, false, DIMENSION_ID),
                InfoView.snapshot(info),
                "the info table must expose the parsed dimension state and portal mapping");
    }

    @Test
    void infoTableExposesCollectionTables() {
        service.create(DIMENSION_ID, configuredDimension());

        ILuaTable info = (ILuaTable) service.getDimension(DIMENSION_ID);

        assertEquals(List.of(true, true),
                List.of(info.rawget("biomes").isTable(), info.rawget("spawnEntities").isTable()),
                "the info table must expose biomes and spawnEntities as tables");
    }

    @Test
    void infoTableReportsBatchFlags() {
        service.create("minecraft:batch", engine.createTable());
        engine.executeStringOrThrow(
                "function infoChunk(cx, cz, ctx)\n"
                        + "    local h = {}\n"
                        + "    for i = 1, 256 do h[i] = 64 end\n"
                        + "    return h\n"
                        + "end", "info_batch_fixture");
        service.setChunkTerrain("minecraft:batch", engine.getGlobalEnvironment().rawget("infoChunk"));

        ILuaTable info = (ILuaTable) service.getDimension("minecraft:batch");

        assertEquals(List.of("chunk", true, false, false),
                List.of(info.rawget("terrainMode").asString(),
                        info.rawget("hasChunkTerrain").asBoolean(),
                        info.rawget("hasBiomeBatch").asBoolean(),
                        info.rawget("hasTerrainSpec").asBoolean()),
                "the info table must report chunk terrain flags and omit unused providers");
    }

    @Test
    void infoTableIsNullForUnknownDimension() {
        assertNull(service.getDimension("minecraft:unknown"),
                "an unknown dimension must have no info table");
    }

    @Test
    void infoTableRejectsInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> service.getDimension("Bogus Id!"),
                "an invalid dimension id must be rejected by getDimension");
    }

    private ILuaTable configuredDimension() {
        ILuaTable config = engine.createTable();
        config.rawset("seaLevel", 40);
        config.rawset("surfaceBlock", "arcane:crystal_grass");
        config.rawset("waterBlock", "minecraft:lava");
        config.rawset("hasBedrock", true);
        config.rawset("biomeSize", 12);
        return config;
    }
}
