package com.luatweaker.api.structures;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * MVP service for single-template custom structures (EXTENSION_ROADMAP M3, scoped).
 *
 * <p>Scope: each registration binds one NBT template (authored by the mod under
 * {@code luamods/<mod_id>/data/<ns>/structure/<path>.nbt}, served by the
 * virtual pack's physical-file fallback) to placement parameters. The platform provider
 * materializes it as vanilla {@code structure} + {@code template_pool} +
 * {@code structure_set} JSON plus a {@code has_structure} biome tag. Multi-piece
 * jigsaw assembly ({@code RegisterJigsaw}, start pieces, max depth, spawn-condition
 * callbacks) is explicitly OUT of scope for this MVP and is not promised here.
 *
 * <p>Validated-but-reserved fields: {@code spawnChance} and {@code lootTables} are
 * range/shape-validated and stored so scripts fail fast on typos, but the MVP
 * datapack emission does not consume them (placement uses spacing/separation only;
 * chest loot inside a structure comes from the NBT itself). A future phase may add
 * a runtime gate/injection step; until then they are observable via
 * {@link #getRegistrations()} and the service accessors.
 */
@LuaDoc(description = "MVP registry for single-template NBT structures (placement via spacing/separation; no jigsaw assembly API).")
public interface IStructureService {

    @LuaDoc(
        description = "Register a single-template structure. Template NBT must exist under the mod data roots.",
        params = {"structureId: string - structure ID (e.g. 'mymod:ancient_temple')",
                  "config: table - {template, spawnBiomes[], spacing, separation, spawnChance, lootTables}"},
        returnType = "void"
    )
    void register(@NotNull String structureId, @NotNull ILuaTable config);

    @LuaDoc(
        description = "Remove a structure registration. Returns true when an entry was present.",
        params = {"structureId: string - structure ID"},
        returnType = "boolean"
    )
    boolean unregister(@NotNull String structureId);

    @LuaDoc(
        description = "Summary of registrations ({structures: count, ids: [sorted ids]}) for tests/Lua observability.",
        returnType = "table"
    )
    @NotNull
    Map<String, Object> getRegistrations();

    @LuaDoc(description = "Remove every structure registration.", returnType = "void")
    void clearAll();
}
