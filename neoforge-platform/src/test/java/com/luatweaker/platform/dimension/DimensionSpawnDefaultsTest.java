package com.luatweaker.platform.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Unsafe spawn values must clamp instead of crashing the server tick. */
class DimensionSpawnDefaultsTest {

    private static DimensionMobSpawner.SpawnRequest parseClampedEntry() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable entry = engine.createTable();
        entry.rawset("entity", "minecraft:cow");
        entry.rawset("x", 0);
        entry.rawset("z", 0);
        entry.rawset("count", 999);
        entry.rawset("spread", 999);
        entry.rawset("maxNearby", 999);
        return DimensionMobSpawner.parseRequest(entry);
    }

    @Test
    void oversizedCountIsClamped() {
        assertEquals(32, parseClampedEntry().count(),
                "a handler cannot spam thousands of mobs per tick");
    }

    @Test
    void oversizedSpreadIsClamped() {
        assertEquals(16, parseClampedEntry().spread(),
                "the spread radius must clamp to a sane area");
    }

    @Test
    void oversizedMaxNearbyIsClamped() {
        assertEquals(32, parseClampedEntry().maxNearby(),
                "the nearby cap must clamp to a sane mob count");
    }

    @Test
    void entryWithoutEntityIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable noEntity = engine.createTable();
        noEntity.rawset("x", 1);
        noEntity.rawset("z", 2);

        assertNull(DimensionMobSpawner.parseRequest(noEntity),
                "an entry without an entity id must not spawn anything");
    }

    @Test
    void entryWithoutXIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable noX = engine.createTable();
        noX.rawset("entity", "minecraft:cow");
        noX.rawset("z", 2);

        assertNull(DimensionMobSpawner.parseRequest(noX),
                "an entry without an x coordinate must not spawn anything");
    }

    @Test
    void entryWithNonStringEntityIsRejected() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable badEntity = engine.createTable();
        badEntity.rawset("entity", 12);
        badEntity.rawset("x", 1);
        badEntity.rawset("z", 2);

        assertNull(DimensionMobSpawner.parseRequest(badEntity),
                "a non-string entity id must not spawn anything");
    }
}
