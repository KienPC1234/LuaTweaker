package com.luatweaker.api.worldgen;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Canonical {@code GenerationStep.Decoration} serialized names for 1.21.1,
 * in enum declaration order (verified: {@code GenerationStep$Decoration}
 * bytecode in the 1.21.1 mappings jar — {@code RAW_GENERATION} through
 * {@code TOP_LAYER_MODIFICATION}).
 *
 * <p>Vanilla biome JSON {@code features} is a list of per-step feature lists
 * in exactly this order (verified: {@code flower_plain.json} /
 * {@code plains.json} in the 1.21.1 client jar carry one entry per step).
 * Shared here so every module maps step names to ordinals identically.
 */
public final class GenerationSteps {
    /** Serialized step names in vanilla ordinal order. */
    public static final List<String> ORDER = List.of(
            "raw_generation", "lakes", "local_modifications", "underground_structures",
            "surface_structures", "strongholds", "underground_ores", "underground_decoration",
            "fluid_springs", "vegetal_decoration", "top_layer_modification");

    private GenerationSteps() {}

    /** True when the name is one of the 11 verified generation steps. */
    public static boolean isKnownStep(@NotNull String step) {
        return ORDER.contains(step);
    }

    /** Ordinal of a known step (its index in a vanilla biome {@code features} list). */
    public static int ordinalOf(@NotNull String step) {
        int index = ORDER.indexOf(step);
        if (index < 0) {
            throw new IllegalArgumentException(
                "Not a known generation step, got: " + step + " (expected one of " + ORDER + ")");
        }
        return index;
    }
}
