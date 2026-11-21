package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;

/**
 * Fresh service instance plus typed Lua-table builders shared by the structure
 * service tests.
 *
 * <p>Every builder writes a primitive overload ({@code rawset(String, double)}
 * etc.). Passing a boxed {@code Double} would bind to
 * {@code rawset(String, Object)} and store Java userdata instead of a Lua
 * number, so the value would not be readable by the config parser.
 */
final class StructureTestFixture {

    final ILuaEngine engine;
    final StructureServiceImpl service;

    StructureTestFixture() {
        engine = LuaEngineFactory.create(false);
        service = new StructureServiceImpl();
    }

    ILuaTable stringList(String... values) {
        ILuaTable list = engine.createTable();
        for (int i = 0; i < values.length; i++) {
            list.rawset(i + 1, values[i]);
        }
        return list;
    }

    /** Minimal valid config: template plus one plains biome, every other field defaulted. */
    ILuaTable minimalConfig(String template) {
        ILuaTable config = engine.createTable();
        config.rawset("template", template);
        config.rawset("spawnBiomes", stringList("minecraft:plains"));
        return config;
    }

    /** Config with two spawn biomes, custom spacing/chance and one loot table. */
    ILuaTable fullConfig(String template) {
        ILuaTable config = minimalConfig(template);
        config.rawset("spawnBiomes", stringList("minecraft:desert", "minecraft:badlands"));
        config.rawset("spacing", 32);
        config.rawset("separation", 16);
        config.rawset("spawnChance", 0.8);
        ILuaTable loot = engine.createTable();
        loot.rawset("chest", "mymod:chests/temple_treasure");
        config.rawset("lootTables", loot);
        return config;
    }

    /** Config where every placement field carries a non-default value. */
    ILuaTable customPlacementConfig(String template) {
        ILuaTable config = minimalConfig(template);
        config.rawset("spacing", 64);
        config.rawset("separation", 16);
        config.rawset("salt", 12_345);
        config.rawset("startHeight", 100);
        config.rawset("size", 6);
        config.rawset("maxDistance", 128);
        config.rawset("useExpansionHack", true);
        config.rawset("terrainAdaptation", "bury");
        config.rawset("heightmap", "OCEAN_FLOOR_WG");
        config.rawset("step", "surface_structures");
        return config;
    }

    ILuaTable withString(ILuaTable config, String key, String value) {
        config.rawset(key, value);
        return config;
    }

    ILuaTable withNumber(ILuaTable config, String key, double value) {
        config.rawset(key, value);
        return config;
    }

    ILuaTable withBoolean(ILuaTable config, String key, boolean value) {
        config.rawset(key, value);
        return config;
    }

    ILuaTable withTable(ILuaTable config, String key, ILuaTable value) {
        config.rawset(key, value);
        return config;
    }
}
