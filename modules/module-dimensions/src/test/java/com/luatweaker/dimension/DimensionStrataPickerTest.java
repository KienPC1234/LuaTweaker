package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies strata-picker overrides: storage, depth filtering and fallbacks. */
class DimensionStrataPickerTest extends DimensionServiceTestBase {

    private static final String DIMENSION_ID = "minecraft:test";

    @Test
    void strataPickerComputesOverrides() {
        service.create(DIMENSION_ID, engine.createTable());
        engine.executeStringOrThrow(
                "strataPicker = function(x, z, surfaceY)\n"
                        + "    return { [2] = 'minecraft:deepslate', [5] = 'minecraft:netherrack',\n"
                        + "             [0] = 'minecraft:stone', [99999] = 'minecraft:stone' }\n"
                        + "end", "strata_picker_fixture");
        ILuaValue picker = engine.getGlobalEnvironment().rawget("strataPicker");
        service.setStrataPicker(DIMENSION_ID, picker);

        assertEquals(Map.of(2, "minecraft:deepslate", 5, "minecraft:netherrack"),
                service.computeStrataOverrides(DIMENSION_ID, 1, 2, 80),
                "strata overrides must keep positive in-range depths and drop the rest");
    }

    @Test
    void strataPickerRejectsNonFunction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.setStrataPicker(DIMENSION_ID, "nope"),
                "strata picker must be a Lua function");
    }

    @Test
    void strataPickerYieldsNoOverridesWhenUnset() {
        service.create(DIMENSION_ID, engine.createTable());

        assertTrue(service.computeStrataOverrides(DIMENSION_ID, 0, 0, 80).isEmpty(),
                "a dimension without a strata picker must produce no overrides");
    }
}
