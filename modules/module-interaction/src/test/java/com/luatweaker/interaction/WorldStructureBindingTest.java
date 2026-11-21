package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins World:PlaceStructure: the template id, position and rotation argument order, the optional
 * dimension prefix, the rotation default and the false (rejected/unparsable) path.
 */
class WorldStructureBindingTest extends WorldEditFixture {

    @Test
    void placeStructureRecordsTemplateRotationAndDefaultDimension() {
        executeLua(engine, """
                local placed = World:PlaceStructure('mymod:shrine', 100, 64, -200, 90)
                assert(placed == true, 'placeStructure must round-trip the result')
                """, "place_struct_test");

        assertEquals(new WorldEditRecorder.PlaceCall(OVERWORLD, "mymod:shrine",
                        List.of(100, 64, -200, 90)),
                recorder.placeCalls.get(0),
                "PlaceStructure must forward the template id, position and rotation "
                        + "with the overworld default");
    }

    @Test
    void placeStructureAcceptsDimensionPrefixAndDefaultsRotation() {
        recorder.placeResult = false;
        executeLua(engine, """
                local placed = World:PlaceStructure('luatweaker:crystal_realm', 'mymod:shrine',
                        1, 2, 3)
                assert(placed == false, 'placeStructure must round-trip the result')
                """, "place_struct_dim_test");

        assertEquals(new WorldEditRecorder.PlaceCall("luatweaker:crystal_realm", "mymod:shrine",
                        List.of(1, 2, 3, 0)),
                recorder.placeCalls.get(0),
                "a dimension prefix must be parsed and the rotation must default to 0");
    }

    @Test
    void missingPlaceArgumentsReturnFalse() {
        executeLua(engine, """
                assert(World:PlaceStructure() == false, 'too few args must return false')
                assert(World:PlaceStructure('mymod:shrine') == false,
                        'missing position must return false')
                """, "place_struct_missing_test");

        assertTrue(recorder.placeCalls.isEmpty(),
                "an incomplete PlaceStructure call must not reach the platform");
    }

    @Test
    void placeStructureAliasIsRegistered() {
        ILuaValue place = worldFunction("placeStructure");
        assertTrue(place != null && place.isFunction(),
                "World.placeStructure camel-case alias must be registered as a function");
    }
}
