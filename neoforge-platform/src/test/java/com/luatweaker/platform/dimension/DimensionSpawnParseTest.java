package com.luatweaker.platform.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression for the server crash {@code LuaException: number has no integer representation}:
 * spawn handlers return world positions as Lua floats, and {@code ILuaValue#asInt()} rejects
 * non-integral values.
 */
class DimensionSpawnParseTest {

    private static final String ENTITY_KEY = "entity";
    private static final String ZOMBIE_ID = "minecraft:zombie";

    static DimensionMobSpawner.SpawnRequest parseFullEntry() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable entry = engine.createTable();
        entry.rawset(ENTITY_KEY, ZOMBIE_ID);
        entry.rawset("x", 10.9);
        entry.rawset("z", -3.2);
        entry.rawset("count", 5);
        entry.rawset("spread", 3);
        entry.rawset("maxNearby", 8);
        return DimensionMobSpawner.parseRequest(entry);
    }

    @Test
    void parseRequestReadsTheEntry() {
        assertNotNull(parseFullEntry(),
                "a complete spawn entry must parse into a request");
    }

    @Test
    void parseRequestReadsTheEntity() {
        assertEquals(ZOMBIE_ID, parseFullEntry().entity(),
                "the request must carry the entity id from the Lua entry");
    }

    @Test
    void parseRequestFloorsPositiveX() {
        assertEquals(10, parseFullEntry().x(),
                "fractional Lua positions must floor to block coordinates");
    }

    @Test
    void parseRequestFloorsNegativeZ() {
        assertEquals(-4, parseFullEntry().z(),
                "negative fractional positions must floor away from zero");
    }

    @Test
    void parseRequestLeavesYEmpty() {
        assertNull(parseFullEntry().y(),
                "y is optional and defaults to the entity's surface position");
    }

    @Test
    void parseRequestReadsTheCount() {
        assertEquals(5, parseFullEntry().count(),
                "the request must carry the mob count from the Lua entry");
    }

    @Test
    void parseRequestReadsTheSpread() {
        assertEquals(3, parseFullEntry().spread(),
                "the request must carry the spread radius from the Lua entry");
    }

    @Test
    void parseRequestReadsMaxNearby() {
        assertEquals(8, parseFullEntry().maxNearby(),
                "the request must carry the nearby cap from the Lua entry");
    }
}
