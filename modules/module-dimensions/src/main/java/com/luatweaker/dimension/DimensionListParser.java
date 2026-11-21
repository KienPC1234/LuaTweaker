package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the entry-list sections of a dimension config
 * ({@code biomes}, {@code spawnEntities}).
 */
final class DimensionListParser {

    private DimensionListParser() {
    }

    static List<DimensionConfig.BiomeEntry> parseBiomes(@Nullable ILuaValue value) {
        List<DimensionConfig.BiomeEntry> out = new ArrayList<>();
        if (value == null || value.isNil()) {
            return out;
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException("biomes must be a table of {id=..., weight=...} entries");
        }
        ILuaTable table = value.asTable();
        for (int i = 1; i <= table.length(); i++) {
            out.add(parseBiomeEntry(table.rawget(i), i));
        }
        return List.copyOf(out);
    }

    private static DimensionConfig.BiomeEntry parseBiomeEntry(@Nullable ILuaValue entry, int index) {
        if (entry == null || !entry.isTable()) {
            throw new IllegalArgumentException("biomes[" + index + "] must be a table {id=..., weight=...}");
        }
        ILuaTable entryTable = entry.asTable();
        ILuaValue idVal = entryTable.rawget("id");
        if (idVal == null || idVal.isNil() || !idVal.isString()) {
            throw new IllegalArgumentException("biomes[" + index + "] is missing a string 'id'");
        }
        String id = idVal.asString();
        BiomeJson.validateId("biome id", id);
        int weight = LuaTableValues.intRange(entryTable, "weight", 1, 1, Integer.MAX_VALUE);
        return new DimensionConfig.BiomeEntry(id, weight);
    }

    static List<DimensionConfig.SpawnEntry> parseSpawnEntities(@Nullable ILuaValue value) {
        List<DimensionConfig.SpawnEntry> out = new ArrayList<>();
        if (value == null || value.isNil()) {
            return out;
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException("spawnEntities must be a table of {entity=..., weight=..., minGroup=..., maxGroup=...} entries");
        }
        ILuaTable table = value.asTable();
        for (int i = 1; i <= table.length(); i++) {
            out.add(parseSpawnEntry(table.rawget(i), i));
        }
        return List.copyOf(out);
    }

    private static DimensionConfig.SpawnEntry parseSpawnEntry(@Nullable ILuaValue entry, int index) {
        if (entry == null || !entry.isTable()) {
            throw new IllegalArgumentException("spawnEntities[" + index + "] must be a table {entity=..., weight=...}");
        }
        ILuaTable entryTable = entry.asTable();
        ILuaValue entityVal = entryTable.rawget("entity");
        if (entityVal == null || entityVal.isNil() || !entityVal.isString()) {
            throw new IllegalArgumentException("spawnEntities[" + index + "] is missing a string 'entity'");
        }
        String entity = entityVal.asString();
        BiomeJson.validateId("entity id", entity);
        int weight = LuaTableValues.intRange(entryTable, "weight", 1, 1, Integer.MAX_VALUE);
        int minGroup = LuaTableValues.intRange(entryTable, "minGroup", 1, 1, 64);
        int maxGroup = LuaTableValues.intRange(entryTable, "maxGroup", minGroup, minGroup, 64);
        return new DimensionConfig.SpawnEntry(entity, weight, minGroup, maxGroup);
    }
}
