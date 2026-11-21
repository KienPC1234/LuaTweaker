package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.worldgen.GenerationSteps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a {@code Structures:Register} config table and builds the
 * immutable {@link StructureServiceImpl.StructureEntry}. Section parsers are
 * split per config area so each failure names the exact key at fault.
 */
final class StructureConfigParser {

    /**
     * Vanilla {@code desert_pyramids} structure_set uses spacing 32 / separation 8
     * (verified: data/minecraft/worldgen/structure_set/desert_pyramids.json in the
     * 1.21.1 client jar), so those are the defaults when Lua omits the fields.
     */
    private static final int DEFAULT_SPACING = 32;
    private static final int DEFAULT_SEPARATION = 8;
    private static final double DEFAULT_SPAWN_CHANCE = 1.0;

    /**
     * Jigsaw placement defaults mirror {@code village_plains.json} (verified in
     * the 1.21.1 client jar): start_height 0, size 1 (single-piece template),
     * max_distance_from_center 80, no expansion hack, beard_thin adaptation,
     * surface projection, surface_structures step.
     */
    private static final int DEFAULT_START_HEIGHT = 0;
    private static final int DEFAULT_SIZE = 1;
    private static final int DEFAULT_MAX_DISTANCE = 80;
    private static final boolean DEFAULT_EXPANSION_HACK = false;
    private static final String DEFAULT_TERRAIN_ADAPTATION = "beard_thin";
    private static final String DEFAULT_HEIGHTMAP = "WORLD_SURFACE_WG";
    private static final String DEFAULT_STRUCTURE_STEP = "surface_structures";

    /**
     * Terrain-adaptation values observed across vanilla structure JSONs
     * (beard_thin/beard_box/bury/encapsulate) plus {@code none}, the vanilla
     * codec default. Invalid names fail fast here instead of dying at datapack load.
     */
    private static final Set<String> KNOWN_TERRAIN_ADAPTATIONS = Set.of(
            "none", "beard_thin", "beard_box", "bury", "encapsulate");

    /**
     * Heightmap projections for {@code project_start_to_heightmap}.
     * WORLD_SURFACE_WG is jar-verified in structure data; the other five are the
     * standard Heightmap$Types names (UNVERIFIED in 1.21.1 structure data, but any
     * mismatch still fails loudly at datapack parse, never silently).
     */
    private static final Set<String> KNOWN_HEIGHTMAPS = Set.of(
            "WORLD_SURFACE_WG", "WORLD_SURFACE", "OCEAN_FLOOR_WG", "OCEAN_FLOOR",
            "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES");

    /**
     * Absolute engine build-height bounds. startHeight outside this range can
     * never generate, so it is rejected as a typo instead of silently spawning nothing.
     */
    private static final int MIN_START_HEIGHT = -2032;
    private static final int MAX_START_HEIGHT = 2032;

    private StructureConfigParser() {}

    private record Placement(int spacing, int separation) {}

    private record Appearance(int startHeight, int size, int maxDistance, boolean useExpansionHack,
                              String terrainAdaptation, String heightmap, String step) {}

    @NotNull
    static StructureServiceImpl.StructureEntry parse(String structureId, ILuaTable t) {
        String template = requireTemplate(structureId, t);
        List<String> spawnBiomes = requireSpawnBiomes(structureId, t);
        Placement placement = parsePlacement(structureId, t);
        double spawnChance = parseSpawnChance(t);
        Map<String, String> lootTables = StructureListReaders.stringMap(t, "lootTables");
        // NOTE: spawnChance/lootTables stay reserved (see IStructureService): the
        // vanilla structure_set JSON has no chance field, so rarity is driven by
        // spacing/separation/salt below, and chest loot comes from the NBT itself.
        Appearance appearance = parseAppearance(t);
        int salt = (int) Math.floor(StructureScalarReaders.number(t, "salt", defaultSalt(structureId)));
        return new StructureServiceImpl.StructureEntry(structureId, template, List.copyOf(spawnBiomes),
                placement.spacing(), placement.separation(), spawnChance,
                Collections.unmodifiableMap(lootTables),
                appearance.startHeight(), appearance.size(), appearance.maxDistance(),
                appearance.useExpansionHack(), appearance.terrainAdaptation(),
                appearance.heightmap(), appearance.step(), salt);
    }

    @NotNull
    private static String requireTemplate(String structureId, ILuaTable t) {
        String template = StructureScalarReaders.resourceId(t, "template", null);
        if (template == null) {
            throw new IllegalArgumentException("template is required for structure '" + structureId
                    + "' (e.g. 'mymod:structures/temple')");
        }
        return template;
    }

    @NotNull
    private static List<String> requireSpawnBiomes(String structureId, ILuaTable t) {
        List<String> spawnBiomes = StructureListReaders.stringList(t, "spawnBiomes");
        if (spawnBiomes.isEmpty()) {
            throw new IllegalArgumentException("spawnBiomes must be a non-empty list of biome ids for structure '"
                    + structureId + "'");
        }
        return spawnBiomes;
    }

    @NotNull
    private static Placement parsePlacement(String structureId, ILuaTable t) {
        int spacing = StructureScalarReaders.intRange(t, "spacing", DEFAULT_SPACING, 1, 1024);
        int separation = StructureScalarReaders.intRange(t, "separation", DEFAULT_SEPARATION, 1, 1024);
        if (spacing < separation) {
            throw new IllegalArgumentException("spacing (" + spacing + ") must be >= separation (" + separation
                    + ") for structure '" + structureId + "'");
        }
        return new Placement(spacing, separation);
    }

    private static double parseSpawnChance(ILuaTable t) {
        double spawnChance = StructureScalarReaders.number(t, "spawnChance", DEFAULT_SPAWN_CHANCE);
        if (!Double.isFinite(spawnChance) || spawnChance < 0.0 || spawnChance > 1.0) {
            throw new IllegalArgumentException("spawnChance must be between 0.0 and 1.0, got: " + spawnChance);
        }
        return spawnChance;
    }

    @NotNull
    private static Appearance parseAppearance(ILuaTable t) {
        return new Appearance(
                StructureScalarReaders.intRange(t, "startHeight", DEFAULT_START_HEIGHT,
                        MIN_START_HEIGHT, MAX_START_HEIGHT),
                StructureScalarReaders.intRange(t, "size", DEFAULT_SIZE, 1, Integer.MAX_VALUE),
                StructureScalarReaders.intRange(t, "maxDistance", DEFAULT_MAX_DISTANCE, 1, Integer.MAX_VALUE),
                StructureScalarReaders.bool(t, "useExpansionHack", DEFAULT_EXPANSION_HACK),
                StructureScalarReaders.choice(t, "terrainAdaptation",
                        DEFAULT_TERRAIN_ADAPTATION, KNOWN_TERRAIN_ADAPTATIONS),
                StructureScalarReaders.choice(t, "heightmap", DEFAULT_HEIGHTMAP, KNOWN_HEIGHTMAPS),
                StructureScalarReaders.choice(t, "step", DEFAULT_STRUCTURE_STEP,
                        new LinkedHashSet<>(GenerationSteps.ORDER)));
    }

    /**
     * Deterministic default salt for random_spread placement (moved here from the
     * provider so it is observable and testable; the provider emits it verbatim).
     */
    static int defaultSalt(@NotNull String structureId) {
        return structureId.hashCode() & 0x7fffffff;
    }

    static void validateId(String name, @Nullable String id) {
        if (!StructureScalarReaders.isResourceLocation(id)) {
            throw new IllegalArgumentException(name + " must be a valid resource location (e.g. 'mymod:id'), got: "
                    + id);
        }
    }
}
