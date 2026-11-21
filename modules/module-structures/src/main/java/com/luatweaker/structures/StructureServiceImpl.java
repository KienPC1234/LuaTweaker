package com.luatweaker.structures;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.structures.IStructureService;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validated registry for MVP single-template structures.
 *
 * <p>Parsing helpers live in {@link StructureConfigParser} (with scalar/list
 * readers in {@link StructureScalarReaders}/{@link StructureListReaders});
 * they mirror {@code DimensionServiceImpl} (rawget + typed defaults).
 */
public class StructureServiceImpl implements IStructureService {

    /** One validated registration: template location plus placement parameters. */
    public record StructureEntry(@NotNull String structureId,
                                 @NotNull String template,
                                 @NotNull List<String> spawnBiomes,
                                 int spacing,
                                 int separation,
                                 double spawnChance,
                                 @NotNull Map<String, String> lootTables,
                                 int startHeight,
                                 int size,
                                 int maxDistance,
                                 boolean useExpansionHack,
                                 @NotNull String terrainAdaptation,
                                 @NotNull String heightmap,
                                 @NotNull String step,
                                 int salt) {}

    private final Map<String, StructureEntry> registrations = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull String structureId, @NotNull ILuaTable config) {
        StructureConfigParser.validateId("structureId", structureId);
        if (config == null) {
            throw new IllegalArgumentException("config must be a table for structure '" + structureId + "'");
        }
        StructureEntry entry = StructureConfigParser.parse(structureId, config);
        registrations.put(structureId, entry);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Structure registered: " + structureId + " (template=" + entry.template()
                        + ", biomes=" + entry.spawnBiomes().size()
                        + ", spacing=" + entry.spacing() + ", separation=" + entry.separation() + ")");
    }

    @Override
    public boolean unregister(@NotNull String structureId) {
        StructureConfigParser.validateId("structureId", structureId);
        return registrations.remove(structureId) != null;
    }

    @Override
    @NotNull
    public Map<String, Object> getRegistrations() {
        return Map.of("structures", registrations.size(),
                "ids", new ArrayList<>(new TreeSet<>(registrations.keySet())));
    }

    @Override
    public void clearAll() {
        registrations.clear();
    }

    @Override
    public boolean placeAt(@NotNull String structureId, @NotNull String dimensionId,
                           int x, int y, int z,
                           @NotNull String rotation, @NotNull String mirror) {
        StructureConfigParser.validateId("structureId", structureId);
        StructureConfigParser.validateId("dimensionId", dimensionId);
        StructureEntry entry = registrations.get(structureId);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown structure '" + structureId
                    + "'. Call Structures:Register(\"" + structureId + "\", {...}) first.");
        }
        if (!com.luatweaker.api.pal.Platform.isInitialized()) {
            throw new IllegalStateException("Platform is not initialized; cannot place structures (unit-test environment?)");
        }
        return com.luatweaker.api.pal.Platform.getStructures().placeTemplate(
                entry.template(), dimensionId, x, y, z, rotation, mirror);
    }

    /** Returns the parsed entry for a structure id, or null when unknown. */
    @Nullable
    public StructureEntry getEntry(@NotNull String structureId) {
        return registrations.get(structureId);
    }

    /** Deterministic default placement salt; the canonical implementation lives in the config parser. */
    static int defaultSalt(@NotNull String structureId) {
        return StructureConfigParser.defaultSalt(structureId);
    }

    /** Returns all registered structure ids in sorted order. */
    @NotNull
    public List<String> getStructureIds() {
        return List.copyOf(new TreeSet<>(registrations.keySet()));
    }
}
