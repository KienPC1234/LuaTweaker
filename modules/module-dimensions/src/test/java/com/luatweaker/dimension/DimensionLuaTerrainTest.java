package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end checks of the Lua terrain generator callback and block picker through
 * the real Lua VM.
 */
class DimensionLuaTerrainTest extends DimensionLuaTestBase {

    private static final String DIMENSION_ID = "minecraft:terrain_dim";
    private static final String TERRAIN_SCRIPT =
            "Dimensions:Create('" + DIMENSION_ID + "', { surfaceBlock = '" + GRASS + "' })\n"
                    + "Dimensions:SetTerrainGenerator('" + DIMENSION_ID + "', function(x, z, baseHeight)\n"
                    + "    return baseHeight + 10, '" + STONE + "'\n"
                    + "end)\n";

    private record RealmInfo(boolean hasBlockPicker, int spawnX, int spawnZ,
                             String portalTarget, String unknownTarget) {
        static RealmInfo snapshot(ILuaTable info, DimensionServiceImpl service) {
            return new RealmInfo(info.rawget("hasBlockPicker").asBoolean(),
                    info.rawget("spawnX").asInt(), info.rawget("spawnZ").asInt(),
                    service.getPortalTarget(PORTAL), service.getPortalTarget("arcane:nope"));
        }
    }

    @Test
    void terrainGeneratorReturnsHeightFromLua() {
        assertEquals(73, terrainResult().height(),
                "the Lua terrain generator must receive the base height and return its height");
    }

    @Test
    void terrainGeneratorDefaultsBlockToSurface() {
        assertEquals(STONE, terrainResult().blockId(),
                "the block id returned by the Lua terrain generator must be used");
    }

    @Test
    void blockPickerProducesOverrides() {
        installBlockPicker();

        assertEquals(Map.of(77, "minecraft:air"),
                dimensionService().computeBlockOverrides(REALM, 4, 0, 80, -64, 320),
                "the Lua block picker must produce the in-range air override");
    }

    @Test
    void blockPickerSkipsColumnsOutsideRule() {
        installBlockPicker();

        assertTrue(dimensionService().computeBlockOverrides(REALM, 1, 0, 80, -64, 320).isEmpty(),
                "columns where the Lua picker returns nothing must produce no overrides");
    }

    @Test
    void blockPickerInfoAndPortalTargetsAreExposed() {
        installBlockPicker();
        ILuaTable info = (ILuaTable) dimensionService().getDimension(REALM);

        assertEquals(new RealmInfo(true, 42, -42, REALM, null),
                RealmInfo.snapshot(info, dimensionService()),
                "the info table must expose the picker flag and spawn point, and portal targets must resolve");
    }

    private DimensionServiceImpl.TerrainResult terrainResult() {
        engine.executeStringOrThrow(TERRAIN_SCRIPT, "lua_terrain_fixture");
        return dimensionService().computeTerrain(DIMENSION_ID, 3, 4, 63);
    }

    private void installBlockPicker() {
        engine.executeStringOrThrow(
                "Dimensions:Create('" + REALM + "', { surfaceBlock = 'arcane:crystal_grass' })\n"
                        + "Dimensions:SetBlockPicker('" + REALM + "', function(x, z, surfaceY, minY)\n"
                        + "    local o = {}\n"
                        + "    if x % 4 == 0 then o[surfaceY - 3] = 'minecraft:air' end\n"
                        + "    return o\n"
                        + "end)\n"
                        + "Dimensions:SetSpawnPoint('" + REALM + "', 42, -42)\n"
                        + "Dimensions:RegisterPortal('" + PORTAL + "', '" + REALM + "')\n"
                        + "local info = Dimensions:GetDimension('" + REALM + "')\n"
                        + "assert(info.hasBlockPicker == true)\n"
                        + "assert(Dimensions:GetPortalTarget('" + PORTAL + "') == '" + REALM + "')\n",
                "lua_block_picker_fixture");
    }
}
