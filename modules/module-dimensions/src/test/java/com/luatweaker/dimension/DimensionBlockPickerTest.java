package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies block-picker overrides: storage, bounds filtering and fallbacks. */
class DimensionBlockPickerTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:test";

    @Test
    void blockPickerComputesOverrides() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setBlockPicker(DIMENSION_ID, luaFunction(
                "picker = function(x, z, surfaceY, minY)\n"
                        + "    local o = {}\n"
                        + "    o[surfaceY - 5] = 'minecraft:air'\n"
                        + "    o[surfaceY - 10] = 'luatweaker:crystal_ore'\n"
                        + "    o[99999] = 'minecraft:stone'\n"
                        + "    o['bad'] = 'minecraft:stone'\n"
                        + "    return o\n"
                        + "end", "picker"));

        assertEquals(Map.of(75, "minecraft:air", 70, "luatweaker:crystal_ore"),
                service.computeBlockOverrides(DIMENSION_ID, 1, 2, 80, -64, 320),
                "picker overrides must keep in-range numeric entries and drop the rest");
    }

    @Test
    void blockPickerRejectsNonFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setBlockPicker(DIMENSION_ID, engine.wrapNumber(5)),
                "block picker must be a Lua function");
    }

    @Test
    void blockPickerYieldsNoOverridesWhenUnset() {
        service.create(DIMENSION_ID, engine.createTable());

        assertTrue(service.computeBlockOverrides(DIMENSION_ID, 0, 0, 80, -64, 320).isEmpty(),
                "a dimension without a block picker must produce no overrides");
    }

    @Test
    void blockPickerDropsInvalidBlockIds() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setBlockPicker(DIMENSION_ID, luaFunction(
                "badPicker = function(x, z, surfaceY, minY) return { [10] = 'not a block id' } end",
                "badPicker"));

        assertTrue(service.computeBlockOverrides(DIMENSION_ID, 0, 0, 80, -64, 320).isEmpty(),
                "an invalid block id from the picker must be dropped");
    }

    @Test
    void blockPickerNilReturnMeansNoOverrides() {
        service.create(DIMENSION_ID, engine.createTable());
        service.setBlockPicker(DIMENSION_ID, luaFunction(
                "nilPicker = function(x, z, surfaceY, minY) end", "nilPicker"));

        assertTrue(service.computeBlockOverrides(DIMENSION_ID, 0, 0, 80, -64, 320).isEmpty(),
                "a picker returning nil must produce no overrides");
    }

    private ILuaValue luaFunction(String script, String globalName) {
        engine.executeStringOrThrow(script, "block_picker_fixture");
        return engine.getGlobalEnvironment().rawget(globalName);
    }
}
