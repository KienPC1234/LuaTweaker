package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;

/** Shared biome config builder for define tests. */
final class BiomesDefineFixture {

    private BiomesDefineFixture() {
    }

    static ILuaTable defineConfig(ILuaEngine engine) {
        ILuaTable effects = engine.createTable();
        effects.rawset("sky_color", 5_040_991);
        effects.rawset("fog_color", 12_638_463);
        effects.rawset("water_color", 4_159_204);
        effects.rawset("water_fog_color", 329_011);
        effects.rawset("grass_color_modifier", "swamp");

        ILuaTable music = engine.createTable();
        music.rawset("sound", "mymod:music.crystal");
        music.rawset("min_delay", 1200);
        music.rawset("max_delay", 3600);
        effects.rawset("music", music);

        ILuaTable particle = engine.createTable();
        particle.rawset("type", "minecraft:cherry_leaves");
        particle.rawset("probability", 0.02);
        effects.rawset("particle", particle);

        ILuaTableEntry monsterEntry = new ILuaTableEntry("mymod:wisp", 10, 1, 2);

        ILuaTable spawners = engine.createTable();
        spawners.rawset("monster", monsterEntry.list(engine));

        ILuaTable costs = engine.createTable();
        ILuaTable wispCost = engine.createTable();
        wispCost.rawset("charge", 1.0);
        wispCost.rawset("energy_budget", 0.5);
        costs.rawset("mymod:wisp", wispCost);

        ILuaTable carvers = engine.createTable();
        carvers.rawset("air", stringList(engine, "minecraft:cave", "minecraft:canyon"));

        ILuaTable features = engine.createTable();
        features.rawset("vegetal_decoration", stringList(engine, "mymod:crystal_trees", "minecraft:flower_plain"));
        features.rawset("underground_ores", stringList(engine, "minecraft:ore_coal_upper"));

        ILuaTable config = engine.createTable();
        config.rawset("temperature", 0.9);
        config.rawset("downfall", 0.7);
        config.rawset("effects", effects);
        config.rawset("spawners", spawners);
        config.rawset("spawn_costs", costs);
        config.rawset("carvers", carvers);
        config.rawset("features", features);
        return config;
    }

    static ILuaTable stringList(ILuaEngine engine, String... ids) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < ids.length; i++) {
            table.rawset(i + 1, ids[i]);
        }
        return table;
    }

    private static final class ILuaTableEntry {
        private final String type;
        private final int weight;
        private final int min;
        private final int max;

        ILuaTableEntry(String type, int weight, int min, int max) {
            this.type = type;
            this.weight = weight;
            this.min = min;
            this.max = max;
        }

        ILuaTable list(ILuaEngine engine) {
            ILuaTable entry = engine.createTable();
            entry.rawset("type", type);
            entry.rawset("weight", weight);
            entry.rawset("minCount", min);
            entry.rawset("maxCount", max);
            ILuaTable list = engine.createTable();
            list.rawset(1, entry);
            return list;
        }
    }
}
