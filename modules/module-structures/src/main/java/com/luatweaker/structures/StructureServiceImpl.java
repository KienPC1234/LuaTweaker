package com.luatweaker.structures;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.structures.IStructureService;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validated registry for MVP single-template structures.
 *
 * <p>Parsing helpers mirror {@code DimensionServiceImpl} (rawget + typed defaults).
 */
public class StructureServiceImpl implements IStructureService {

    /** Minecraft resource-location charset: [a-z0-9_.-] for namespace, path may also contain '/'. */
    private static final java.util.regex.Pattern RESOURCE_LOCATION =
            java.util.regex.Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    /**
     * Vanilla {@code desert_pyramids} structure_set uses spacing 32 / separation 8
     * (verified: data/minecraft/worldgen/structure_set/desert_pyramids.json in the
     * 1.21.1 client jar), so those are the defaults when Lua omits the fields.
     */
    private static final int DEFAULT_SPACING = 32;
    private static final int DEFAULT_SEPARATION = 8;
    private static final double DEFAULT_SPAWN_CHANCE = 1.0;

    /** One validated registration: template location plus placement parameters. */
    public record StructureEntry(@NotNull String structureId,
                                 @NotNull String template,
                                 @NotNull List<String> spawnBiomes,
                                 int spacing,
                                 int separation,
                                 double spawnChance,
                                 @NotNull Map<String, String> lootTables) {}

    private final Map<String, StructureEntry> registrations = new ConcurrentHashMap<>();

    @Override
    public void register(@NotNull String structureId, @NotNull ILuaTable config) {
        validateId("structureId", structureId);
        if (config == null) {
            throw new IllegalArgumentException("config must be a table for structure '" + structureId + "'");
        }
        StructureEntry entry = parseEntry(structureId, config);
        registrations.put(structureId, entry);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Structure registered: " + structureId + " (template=" + entry.template()
                        + ", biomes=" + entry.spawnBiomes().size()
                        + ", spacing=" + entry.spacing() + ", separation=" + entry.separation() + ")");
    }

    @Override
    public boolean unregister(@NotNull String structureId) {
        validateId("structureId", structureId);
        return registrations.remove(structureId) != null;
    }

    @Override
    @NotNull
    public Map<String, Object> getRegistrations() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("structures", registrations.size());
        summary.put("ids", new ArrayList<>(new TreeSet<>(registrations.keySet())));
        return summary;
    }

    @Override
    public void clearAll() {
        registrations.clear();
    }

    /** Returns the parsed entry for a structure id, or null when unknown. */
    @Nullable
    public StructureEntry getEntry(@NotNull String structureId) {
        return registrations.get(structureId);
    }

    /** Returns all registered structure ids in sorted order. */
    @NotNull
    public List<String> getStructureIds() {
        return List.copyOf(new TreeSet<>(registrations.keySet()));
    }

    // -------------------------------------------------------------------------
    // Config parsing
    // -------------------------------------------------------------------------

    private StructureEntry parseEntry(String structureId, ILuaTable t) {
        String template = resourceId(t, "template", null);
        if (template == null) {
            throw new IllegalArgumentException("template is required for structure '" + structureId
                    + "' (e.g. 'mymod:structures/temple')");
        }
        List<String> spawnBiomes = stringList(t, "spawnBiomes");
        if (spawnBiomes.isEmpty()) {
            throw new IllegalArgumentException("spawnBiomes must be a non-empty list of biome ids for structure '"
                    + structureId + "'");
        }
        int spacing = intRange(t, "spacing", DEFAULT_SPACING, 1, 1024);
        int separation = intRange(t, "separation", DEFAULT_SEPARATION, 1, 1024);
        if (spacing < separation) {
            throw new IllegalArgumentException("spacing (" + spacing + ") must be >= separation (" + separation
                    + ") for structure '" + structureId + "'");
        }
        double spawnChance = number(t, "spawnChance", DEFAULT_SPAWN_CHANCE);
        if (!Double.isFinite(spawnChance) || spawnChance < 0.0 || spawnChance > 1.0) {
            throw new IllegalArgumentException("spawnChance must be between 0.0 and 1.0, got: " + spawnChance);
        }
        Map<String, String> lootTables = stringMap(t, "lootTables");
        return new StructureEntry(structureId, template, List.copyOf(spawnBiomes),
                spacing, separation, spawnChance, Collections.unmodifiableMap(lootTables));
    }

    private static List<String> stringList(ILuaTable t, String key) {
        ILuaValue value = t.rawget(key);
        if (value == null || value.isNil()) {
            return List.of();
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException(key + " must be a list of resource-location strings");
        }
        ILuaTable table = value.asTable();
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue item = table.rawget(i);
            if (item == null || item.isNil() || !item.isString()) {
                throw new IllegalArgumentException(key + "[" + i + "] must be a resource-location string");
            }
            String id = item.asString();
            if (!RESOURCE_LOCATION.matcher(id).matches()) {
                throw new IllegalArgumentException(key + "[" + i + "] must be a valid resource location, got: " + id);
            }
            out.add(id);
        }
        return out;
    }

    private static Map<String, String> stringMap(ILuaTable t, String key) {
        ILuaValue value = t.rawget(key);
        if (value == null || value.isNil()) {
            return Map.of();
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException(key + " must be a table of name -> loot-table id strings");
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<ILuaValue, ILuaValue> entry : value.asTable().asMap().entrySet()) {
            ILuaValue mapKey = entry.getKey();
            ILuaValue mapValue = entry.getValue();
            if (mapKey == null || mapKey.isNil() || !mapKey.isString()) {
                throw new IllegalArgumentException(key + " keys must be strings");
            }
            if (mapValue == null || mapValue.isNil() || !mapValue.isString()) {
                throw new IllegalArgumentException(key + "['" + mapKey.asString() + "'] must be a string");
            }
            String tableId = mapValue.asString();
            if (!RESOURCE_LOCATION.matcher(tableId).matches()) {
                throw new IllegalArgumentException(key + "['" + mapKey.asString()
                        + "'] must be a valid resource location, got: " + tableId);
            }
            out.put(mapKey.asString(), tableId);
        }
        return out;
    }

    private static double number(ILuaTable t, String key, double def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return def;
        }
        if (!v.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number, got: " + describe(v));
        }
        return v.asDouble();
    }

    private static int intRange(ILuaTable t, String key, int def, int min, int max) {
        double raw = number(t, key, def);
        if (!Double.isFinite(raw) || raw < min || raw > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max + ", got: " + raw);
        }
        return (int) Math.floor(raw);
    }

    private static String resourceId(ILuaTable t, String key, @Nullable String def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) {
            return def;
        }
        if (!v.isString()) {
            throw new IllegalArgumentException(key + " must be a string, got: " + describe(v));
        }
        String value = v.asString();
        if (!RESOURCE_LOCATION.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " must be a valid resource location (e.g. 'mymod:id'), got: "
                    + value);
        }
        return value;
    }

    private static String describe(@Nullable ILuaValue v) {
        return v == null ? "nil" : v.getClass().getSimpleName();
    }

    private static void validateId(String name, @Nullable String id) {
        if (id == null || !RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " must be a valid resource location (e.g. 'mymod:id'), got: "
                    + id);
        }
    }
}
