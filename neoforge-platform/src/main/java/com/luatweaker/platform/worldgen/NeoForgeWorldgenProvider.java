package com.luatweaker.platform.worldgen;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Generates datapack JSON (configured/placed features + NeoForge biome modifiers)
 * for every pending worldgen entry. Runs after each mod-load pass; JSON structures
 * verified against the vanilla 1.21.1 datapack.
 */
public class NeoForgeWorldgenProvider {
    private static final String JSON_EXTENSION = ".json";


    private final WorldgenServiceImpl worldgenService;
    private final IDatapackService datapackService;

    /** Vanilla placed-feature ids per vanilla ore block (verified in 1.21.1 data). */
    private static final Map<String, List<String>> VANILLA_ORE_FEATURES = Map.ofEntries(
            Map.entry("minecraft:coal_ore", List.of("minecraft:ore_coal_upper", "minecraft:ore_coal_lower")),
            Map.entry("minecraft:iron_ore", List.of("minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small")),
            Map.entry("minecraft:gold_ore", List.of("minecraft:ore_gold", "minecraft:ore_gold_lower", "minecraft:ore_gold_extra")),
            Map.entry("minecraft:redstone_ore", List.of("minecraft:ore_redstone", "minecraft:ore_redstone_lower")),
            Map.entry("minecraft:lapis_ore", List.of("minecraft:ore_lapis", "minecraft:ore_lapis_buried")),
            Map.entry("minecraft:diamond_ore", List.of("minecraft:ore_diamond", "minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried")),
            Map.entry("minecraft:emerald_ore", List.of("minecraft:ore_emerald")),
            Map.entry("minecraft:copper_ore", List.of("minecraft:ore_copper", "minecraft:ore_copper_large")),
            Map.entry("minecraft:nether_quartz_ore", List.of("minecraft:ore_quartz_nether")),
            Map.entry("minecraft:nether_gold_ore", List.of("minecraft:ore_gold_nether")),
            Map.entry("minecraft:ancient_debris", List.of("minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small"))
    );

    public NeoForgeWorldgenProvider(@NotNull WorldgenServiceImpl worldgenService, @NotNull IDatapackService datapackService) {
        this.worldgenService = worldgenService;
        this.datapackService = datapackService;
    }

    public void applyAll() {
        int ores = applyOres();
        int vegetation = applyVegetation();
        int removals = applyRemovals();
        int biomeModifierFiles = applyBiomeModifierFiles();
        int rawFiles = applyRawFiles();
        int mixedVeins = applyMixedVeins();

        LuaTweakerLog.get().info(LogStage.SYSTEM,
            "[WorldgenProvider] Applied: " + ores + " ore entries, "
            + vegetation + " vegetation entries, "
            + removals + " removals, "
            + biomeModifierFiles + " biome-modifier/custom-feature files, "
            + rawFiles + " raw passthrough files, "
            + mixedVeins + " mixed veins");
        com.luatweaker.core.service.AppliedEventHelper.fire("WorldgenApplied", Map.of(
            "ores", ores,
            "vegetation", vegetation,
            "removals", removals,
            "biomeModifiers", biomeModifierFiles,
            "raw", rawFiles,
            "mixedVeins", mixedVeins));
    }

    /**
     * Materializes {@code Worldgen:AddMixedVein} registrations: one custom
     * configured feature ({@code luatweaker:mixed_vein}, registered by the
     * platform) + placed feature + biome modifier per vein.
     */
    private int applyMixedVeins() {
        Map<String, String> files = worldgenService.buildMixedVeinFiles();
        for (Map.Entry<String, String> file : files.entrySet()) {
            datapackService.addData(file.getKey(), file.getValue());
        }
        return worldgenService.getPendingMixedVeins().size();
    }

    /**
     * Materializes raw passthrough JSON (validated at registration, emitted
     * verbatim). Lets Lua authors use any vanilla configured/placed feature
     * type without waiting for a dedicated builder.
     */
    private int applyRawFiles() {
        Map<String, String> files = worldgenService.buildRawFiles();
        for (Map.Entry<String, String> file : files.entrySet()) {
            datapackService.addData(file.getKey(), file.getValue());
        }
        return files.size();
    }

    /**
     * Materializes {@code Worldgen:ModifyBiome} and {@code Worldgen:AddFeature}
     * registrations. Previously these were validated and stored but never
     * emitted, so the Lua calls silently did nothing in-game.
     */
    private int applyBiomeModifierFiles() {
        Map<String, String> files = worldgenService.buildBiomeModifierFiles();
        for (Map.Entry<String, String> file : files.entrySet()) {
            datapackService.addData(file.getKey(), file.getValue());
        }
        return files.size();
    }

    private int applyOres() {
        List<WorldgenServiceImpl.OreEntry> ores = worldgenService.getPendingOres();
        int emitted = 0;
        for (int i = 0; i < ores.size(); i++) {
            WorldgenServiceImpl.OreEntry ore = ores.get(i);
            if (!isSupportedDimension(ore.dimension())) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "[WorldgenProvider] Skipped ore '" + ore.blockId() + "': unsupported dimension '" + ore.dimension()
                        + "' (supported: minecraft:overworld, minecraft:the_nether, minecraft:the_end)");
                continue;
            }
            String featureId = ore.blockId().replace(":", "_") + "_ore_" + i;
            datapackService.addData("luatweaker/worldgen/configured_feature/" + featureId + JSON_EXTENSION,
                    WorldgenJson.configuredOre(ore));
            datapackService.addData("luatweaker/worldgen/placed_feature/" + featureId + JSON_EXTENSION,
                    WorldgenJson.placedOre(featureId, ore));
            datapackService.addData("luatweaker/neoforge/biome_modifier/add_" + featureId + JSON_EXTENSION,
                    WorldgenJson.addFeatureModifier(featureId, ore.dimension(), ore.biomes(), "underground_ores"));
            emitted++;
        }
        return emitted;
    }

    private int applyVegetation() {
        List<WorldgenServiceImpl.VegetationEntry> entries = worldgenService.getPendingVegetation();
        int emitted = 0;
        for (int i = 0; i < entries.size(); i++) {
            WorldgenServiceImpl.VegetationEntry veg = entries.get(i);
            String baseFeatureId = veg.blockId().replace(":", "_") + "_veg_" + i;
            String configuredId = "luatweaker:" + baseFeatureId;
            datapackService.addData("luatweaker/worldgen/configured_feature/" + baseFeatureId + JSON_EXTENSION,
                    buildConfiguredFlowerFeature(veg));
            // One placed feature PER biome: multiple 'biome' placements would be
            // AND-ed (never spawn); a single placed feature per biome ORs correctly.
            for (int j = 0; j < veg.biomes().size(); j++) {
                String placedId = baseFeatureId + "_" + j;
                String biomeId = veg.biomes().get(j);
                datapackService.addData("luatweaker/worldgen/placed_feature/" + placedId + JSON_EXTENSION,
                        WorldgenJson.placedFlower(configuredId, veg));
                // Per-biome scoping lives here (bare minecraft:biome placement carries
                // no parameters in vanilla) - one add_features modifier per placed feature.
                datapackService.addData("luatweaker/neoforge/biome_modifier/veg_" + placedId + JSON_EXTENSION,
                        WorldgenJson.addFeatureModifier(placedId, List.of(biomeId), "vegetal_decoration"));
                emitted++;
            }
        }
        return emitted;
    }

    private int applyRemovals() {
        List<WorldgenServiceImpl.OreRemovalEntry> removals = worldgenService.getPendingRemovals();
        int applied = 0;
        for (int i = 0; i < removals.size(); i++) {
            WorldgenServiceImpl.OreRemovalEntry removal = removals.get(i);
            List<String> features = VANILLA_ORE_FEATURES.get(removal.blockId());
            if (features == null) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "[WorldgenProvider] Cannot remove '" + removal.blockId()
                        + "': no known vanilla ore features for this block (supported: " + VANILLA_ORE_FEATURES.keySet() + ")");
                continue;
            }
            if (!isSupportedDimension(removal.dimension())) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "[WorldgenProvider] Skipped removal of '" + removal.blockId() + "': unsupported dimension '"
                        + removal.dimension() + "'");
                continue;
            }
            datapackService.addData("luatweaker/neoforge/biome_modifier/remove_" + removal.blockId().replace(":", "_") + "_" + i + JSON_EXTENSION,
                    WorldgenJson.removeFeaturesModifier(features, removal.dimension()));
            applied++;
        }
        return applied;
    }

    private boolean isSupportedDimension(String dimension) {
        return "minecraft:overworld".equals(dimension)
                || "minecraft:the_nether".equals(dimension)
                || "minecraft:the_end".equals(dimension);
    }

    @NotNull
    private String buildConfiguredFlowerFeature(WorldgenServiceImpl.VegetationEntry veg) {
        // Delegate to the service builder so flower AND patch kinds keep their
        // verified shapes (flower_plain vs patch_grass in the 1.21.1 client jar).
        // A local copy here once hardcoded the flower numbers for every kind,
        // silently turning AddVegetationPatch into flower distribution.
        return worldgenService.buildVegetationConfiguredJson(veg);
    }
}

