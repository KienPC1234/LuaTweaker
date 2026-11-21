package com.luatweaker.spawn;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Renders a parsed {@link SpawnRule} back into a Lua table for
 * {@code SpawnRules:GetRules} diagnostics.
 */
final class SpawnRuleDescriber {

    private SpawnRuleDescriber() {}

    @NotNull
    static ILuaTable describe(@NotNull ILuaEngine engine, @NotNull SpawnRule rule) {
        ILuaTable table = engine.createTable();
        table.rawset("entity", rule.entity());
        if (rule.category() != null) {
            table.rawset("category", rule.category());
        }
        table.rawset("weight", rule.weight());
        table.rawset("minGroup", rule.minGroup());
        table.rawset("maxGroup", rule.maxGroup());
        table.rawset("minY", rule.minY());
        table.rawset("maxY", rule.maxY());
        table.rawset("minLight", rule.minBlockLight());
        table.rawset("maxLight", rule.maxBlockLight());
        table.rawset("minSkyLight", rule.minSkyLight());
        table.rawset("maxSkyLight", rule.maxSkyLight());
        if (rule.requireDay() != null) {
            table.rawset("time", rule.requireDay() ? "day" : "night");
        }
        describeWeather(table, rule);
        table.rawset("biomes", joinIds(engine, rule.biomes()));
        table.rawset("blockBelow", joinIds(engine, rule.blocksBelow()));
        table.rawset("solidGround", rule.requireSolidGround());
        table.rawset("hasCheck", rule.check() != null);
        return table;
    }

    private static void describeWeather(@NotNull ILuaTable table, @NotNull SpawnRule rule) {
        if (Boolean.TRUE.equals(rule.requireThundering())) {
            table.rawset("weather", "thunder");
        } else if (rule.requireRaining() != null) {
            table.rawset("weather", rule.requireRaining() ? "rain" : "clear");
        }
    }

    @NotNull
    private static ILuaTable joinIds(@NotNull ILuaEngine engine, @NotNull List<String> ids) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < ids.size(); i++) {
            table.rawset(i + 1, ids.get(i));
        }
        return table;
    }
}
