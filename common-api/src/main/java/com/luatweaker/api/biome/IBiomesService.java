package com.luatweaker.api.biome;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;

/**
 * Runtime biome customization: entity spawn entries are merged into the
 * biome's datapack JSON (the same file the dimension provider materializes),
 * so entities spawn inside specific biomes.
 */
@LuaDoc(description = "Biome customization: add/remove entity spawn entries inside biomes.")
public interface IBiomesService {

    @LuaDoc(
        description = "Add an entity spawn entry to a biome's spawners (e.g. crystal_golem spawns only in crystal_plains).",
        params = {"biomeId: string - e.g. 'luatweaker:crystal_plains'",
                  "category: string - 'monster', 'creature', 'ambient', 'water_creature', 'misc' (default 'monster')",
                  "entity: string - entity id",
                  "weight: number (default 10)",
                  "minCount: number (default 1)",
                  "maxCount: number (default 3)"},
        returnType = "void"
    )
    void addSpawn(@NotNull String biomeId, @NotNull String category, @NotNull String entity,
                  int weight, int minCount, int maxCount);

    @LuaDoc(
        description = "Remove an entity spawn entry from a biome.",
        params = {"biomeId: string", "category: string", "entity: string"},
        returnType = "void"
    )
    void removeSpawn(@NotNull String biomeId, @NotNull String category, @NotNull String entity);

    @LuaDoc(
        description = "Returns the spawn entries of a biome as a table {category = {{type=..., weight=..., minCount=..., maxCount=...}, ...}}.",
        params = {"biomeId: string"},
        returnType = "table"
    )
    @NotNull
    Object getSpawns(@NotNull String biomeId);
}
