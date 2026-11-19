package com.luatweaker.api.biome;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;

/**
 * Runtime biome customization: full biome definitions plus entity spawn
 * entries merged into the biome's datapack JSON (the same file the dimension
 * provider materializes), so entities spawn inside specific biomes.
 *
 * <p>Rule: the biome JSON must exist in the virtual datapack before
 * {@code addSpawn}/{@code removeSpawn} touch it — create it with
 * {@code define} (or pre-seed the file). Unknown ids fail fast instead of
 * silently shadowing vanilla biomes with minimal files.
 */
@LuaDoc(description = "Biome customization: full biome definitions plus add/remove entity spawn entries.")
public interface IBiomesService {

    @LuaDoc(
        description = "Add an entity spawn entry to a biome's spawners (e.g. crystal_golem spawns only in crystal_plains). The biome must already exist in the virtual datapack — create it with Define first; unknown ids fail fast instead of shadowing vanilla biomes.",
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
        description = "Remove an entity spawn entry from a biome. The biome must already exist in the virtual datapack (see Define); unknown ids fail fast instead of writing empty files.",
        params = {"biomeId: string", "category: string", "entity: string"},
        returnType = "void"
    )
    void removeSpawn(@NotNull String biomeId, @NotNull String category, @NotNull String entity);

    @LuaDoc(
        description = "Define a full custom biome JSON (temperature, precipitation, effects, spawners, spawn_costs, carvers, per-step features). All field shapes mirror the vanilla 1.21.1 biome codec (see plains.json in the client jar). Overwrites any previous definition for the id; call before AddSpawn so spawns merge into the full file.",
        params = {"biomeId: string - e.g. 'mymod:crystal_plains'",
                  "config: table - { has_precipitation=true, temperature=0.8, downfall=0.4,"
                      + " effects={ sky_color=0x78A7FF, fog_color=..., water_color=..., water_fog_color=...,"
                      + " foliage_color=..., grass_color=..., grass_color_modifier='none',"
                      + " music={ sound='mymod:music.crystal', min_delay=1200, max_delay=3600 },"
                      + " particle={ type='minecraft:cherry_leaves', probability=0.01 } },"
                  + " spawners={ monster={{ type='mymod:wisp', weight=10, minCount=1, maxCount=2 }} },"
                  + " spawn_costs={ 'mymod:wisp'={ charge=1.0, energy_budget=0.5 } },"
                  + " carvers={ air={ 'minecraft:cave', 'minecraft:canyon' }, liquid={} },"
                  + " features={ vegetal_decoration={ 'mymod:crystal_trees', 'minecraft:flower_plain' } } }"},
        returnType = "void"
    )
    void define(@NotNull String biomeId, @NotNull com.luatweaker.api.vm.ILuaValue config);

    @LuaDoc(
        description = "Returns the spawn entries of a biome as a table {category = {{type=..., weight=..., minCount=..., maxCount=...}, ...}}.",
        params = {"biomeId: string"},
        returnType = "table"
    )
    @NotNull
    Object getSpawns(@NotNull String biomeId);
}
