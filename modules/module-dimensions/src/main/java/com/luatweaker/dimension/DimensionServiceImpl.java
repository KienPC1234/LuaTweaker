package com.luatweaker.dimension;

import com.luatweaker.api.dimension.IDimensionService;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores parsed dimension configs, Lua terrain/biome callbacks and portal
 * mappings; teleports players through the platform abstraction layer.
 *
 * <p>Thread-safety: chunk generation runs on worker threads; all maps are
 * concurrent and callbacks are invoked through the (synchronized) engine
 * entry points. {@link #getGenerationVersion()} is bumped whenever terrain
 * definitions change so long-lived chunk generators can drop stale caches.</p>
 */
public class DimensionServiceImpl implements IDimensionService {

    /** Minecraft resource-location charset: [a-z0-9_.-] for path, optional [a-z0-9_.-] namespace. */
    private static final java.util.regex.Pattern RESOURCE_LOCATION =
            java.util.regex.Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    private final ILuaEngine engine;
    private final Map<String, DimensionConfig> configs = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> terrainGenerators = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> biomeProviders = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> blockPickers = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> strataPickers = new ConcurrentHashMap<>();
    private final Map<String, SpawnPoint> spawnPoints = new ConcurrentHashMap<>();
    private final Map<String, String> portals = new ConcurrentHashMap<>();
    private final AtomicLong generationVersion = new AtomicLong(1);

    /** Custom spawn position for a dimension (overrides the config defaults). */
    public record SpawnPoint(int x, int z) {}

    public DimensionServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void create(@NotNull String dimensionId, @NotNull ILuaValue config) {
        validateId("dimensionId", dimensionId);
        if (config == null || !config.isTable()) {
            throw new IllegalArgumentException("config must be a table for dimension '" + dimensionId + "'");
        }
        DimensionConfig parsed = parseConfig(dimensionId, config.asTable());
        configs.put(dimensionId, parsed);
        generationVersion.incrementAndGet();
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Dimension registered: " + dimensionId + " (minY=" + parsed.minHeight()
                        + ", maxY=" + parsed.maxHeight() + ", surface=" + parsed.surfaceBlock()
                        + ", biomes=" + parsed.biomes().size() + ")");
    }

    @Override
    public void setTerrainGenerator(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateId("dimensionId", dimensionId);
        ILuaValue fn = asFunction(luaFunction, "terrain generator");
        terrainGenerators.put(dimensionId, fn);
        generationVersion.incrementAndGet();
    }

    @Override
    public void setBiomeProvider(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateId("dimensionId", dimensionId);
        ILuaValue fn = asFunction(luaFunction, "biome provider");
        biomeProviders.put(dimensionId, fn);
        generationVersion.incrementAndGet();
    }

    @Override
    public void registerPortal(@NotNull String blockId, @NotNull String targetDimension) {
        validateId("blockId", blockId);
        validateId("targetDimension", targetDimension);
        portals.put(blockId, targetDimension);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Portal registered: " + blockId + " -> " + targetDimension);
    }

    /** Returns the dimension a portal block leads to, or null. */
    @Nullable
    public String getPortalTarget(@NotNull String blockId) {
        validateId("blockId", blockId);
        return portals.get(blockId);
    }

    /**
     * Sets a block picker callback: {@code function(x, z, surfaceY, minY) -> {[y] = blockId, ...}}.
     * Returned overrides replace the default column fill at those Y positions
     * (use "minecraft:air" to carve caves).
     */
    public void setBlockPicker(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateId("dimensionId", dimensionId);
        ILuaValue fn = asFunction(luaFunction, "block picker");
        blockPickers.put(dimensionId, fn);
        generationVersion.incrementAndGet();
    }

    @Override
    public void setStrataPicker(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateId("dimensionId", dimensionId);
        ILuaValue fn = asFunction(luaFunction, "strata picker");
        strataPickers.put(dimensionId, fn);
        generationVersion.incrementAndGet();
    }

    /**
     * Invokes the strata picker for one column and returns validated depth
     * overrides (depth from the surface -> block id). Empty when no picker is set.
     */
    @NotNull
    public Map<Integer, String> computeStrataOverrides(@NotNull String dimensionId, int x, int z, int surfaceY) {
        ILuaValue picker = strataPickers.get(dimensionId);
        if (picker == null) return Map.of();
        ILuaValue result = engine.callFunction(picker,
                engine.wrapNumber(x), engine.wrapNumber(z), engine.wrapNumber(surfaceY));
        if (result == null || result.isNil() || !result.isTable()) {
            return Map.of();
        }
        Map<Integer, String> overrides = new LinkedHashMap<>();
        for (Map.Entry<ILuaValue, ILuaValue> entry : result.asTable().asMap().entrySet()) {
            ILuaValue depthVal = entry.getKey();
            if (depthVal == null || !depthVal.isNumber()) continue;
            int depth = depthVal.asInt();
            if (depth <= 0 || depth > 512) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Strata picker for '" + dimensionId + "' returned out-of-range depth " + depth + "; ignoring");
                continue;
            }
            ILuaValue blockVal = entry.getValue();
            if (blockVal == null || blockVal.isNil() || !blockVal.isString()) continue;
            String blockId = blockVal.asString();
            if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Strata picker for '" + dimensionId + "' returned invalid block id '" + blockId + "' at depth " + depth);
                continue;
            }
            overrides.put(depth, blockId);
        }
        return overrides;
    }

    /** Returns the strata picker callback for the dimension, or null. */
    @Nullable
    public ILuaValue getStrataPicker(@NotNull String dimensionId) {
        return strataPickers.get(dimensionId);
    }

    /** Returns the block picker callback for the dimension, or null. */
    @Nullable
    public ILuaValue getBlockPicker(@NotNull String dimensionId) {
        return blockPickers.get(dimensionId);
    }

    /**
     * Invokes the block picker for one column and returns validated overrides
     * (Y -> blockId) inside [minY, maxY-1]. Empty when no picker is set.
     */
    @NotNull
    public Map<Integer, String> computeBlockOverrides(@NotNull String dimensionId, int x, int z,
                                                      int surfaceY, int minY, int maxY) {
        ILuaValue picker = blockPickers.get(dimensionId);
        if (picker == null) return Map.of();
        ILuaValue result = engine.callFunction(picker,
                engine.wrapNumber(x), engine.wrapNumber(z),
                engine.wrapNumber(surfaceY), engine.wrapNumber(minY));
        if (result == null || result.isNil()) return Map.of();
        if (!result.isTable()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Block picker for '" + dimensionId + "' returned a non-table; ignoring overrides");
            return Map.of();
        }
        Map<Integer, String> overrides = new LinkedHashMap<>();
        ILuaTable table = result.asTable();
        for (Map.Entry<ILuaValue, ILuaValue> entry : table.asMap().entrySet()) {
            ILuaValue yVal = entry.getKey();
            if (yVal == null || !yVal.isNumber()) continue;
            int y = yVal.asInt();
            ILuaValue blockVal = entry.getValue();
            if (blockVal == null || blockVal.isNil() || !blockVal.isString()) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Block picker for '" + dimensionId + "' returned invalid entry at Y " + y);
                continue;
            }
            String blockId = blockVal.asString();
            if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Block picker for '" + dimensionId + "' returned invalid block id '" + blockId + "' at Y " + y);
                continue;
            }
            if (y < minY || y >= maxY) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Block picker for '" + dimensionId + "' returned out-of-range Y " + y
                                + " (range " + minY + ".." + (maxY - 1) + "); ignoring");
                continue;
            }
            overrides.put(y, blockId);
        }
        return overrides;
    }

    /** Sets a custom spawn position for the dimension. */
    public void setSpawnPoint(@NotNull String dimensionId, int x, int z) {
        validateId("dimensionId", dimensionId);
        spawnPoints.put(dimensionId, new SpawnPoint(x, z));
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Spawn point set for '" + dimensionId + "': (" + x + ", " + z + ")");
    }

    /** Returns the spawn point (runtime override, else config), or null. */
    @Nullable
    public SpawnPoint getSpawnPoint(@NotNull String dimensionId) {
        SpawnPoint override = spawnPoints.get(dimensionId);
        if (override != null) return override;
        DimensionConfig config = configs.get(dimensionId);
        if (config != null && config.spawnX() != null && config.spawnZ() != null) {
            return new SpawnPoint(config.spawnX(), config.spawnZ());
        }
        return null;
    }

    @Override
    public void teleportTo(@NotNull IEntity player, @NotNull String dimensionId) {
        validateId("dimensionId", dimensionId);
        if (configs.get(dimensionId) == null) {
            throw new IllegalArgumentException("Unknown dimension '" + dimensionId
                    + "'. Call Dimensions:Create(\"" + dimensionId + "\", {...}) first.");
        }
        Object raw = player != null ? player.getRawEntity() : null;
        if (raw == null) {
            throw new IllegalArgumentException("teleportTo requires a valid player entity");
        }
        if (!Platform.isInitialized()) {
            throw new IllegalStateException("Platform is not initialized; cannot teleport (unit-test environment?)");
        }
        boolean ok = Platform.getDimension().teleportToDimension(raw, dimensionId);
        if (!ok) {
            throw new IllegalStateException("Dimension level '" + dimensionId
                    + "' is not loaded; the server must create the world first");
        }
    }

    @Override
    @Nullable
    public Object getDimension(@NotNull String dimensionId) {
        validateId("dimensionId", dimensionId);
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null) return null;
        return buildInfoTable(cfg);
    }

    // -------------------------------------------------------------------------
    // Queries for the platform provider / chunk generator
    // -------------------------------------------------------------------------

    /** Returns the parsed config, or null when the dimension is unknown. */
    @Nullable
    public DimensionConfig getConfig(@NotNull String dimensionId) {
        return configs.get(dimensionId);
    }

    /** Returns all registered dimension ids. */
    public List<String> getDimensionIds() {
        return List.copyOf(configs.keySet());
    }

    /** Returns the terrain generator Lua function for the dimension, or null. */
    @Nullable
    public ILuaValue getTerrainGenerator(@NotNull String dimensionId) {
        return terrainGenerators.get(dimensionId);
    }

    /** Returns the biome provider Lua function for the dimension, or null. */
    @Nullable
    public ILuaValue getBiomeProvider(@NotNull String dimensionId) {
        return biomeProviders.get(dimensionId);
    }

    /** blockId -> targetDimension for every registered portal. */
    public Map<String, String> getPortals() {
        return Map.copyOf(portals);
    }

    /** Bumped whenever any terrain definition changes (create/setTerrainGenerator/setBiomeProvider). */
    public long getGenerationVersion() {
        return generationVersion.get();
    }

    /**
     * Invokes the terrain generator callback: {@code function(x, z, baseHeight) -> height, blockId}.
     * Falls back to the config surface block when the callback is absent.
     * Returns the resolved surface height and the top block id.
     */
    public TerrainResult computeTerrain(@NotNull String dimensionId, int x, int z, int baseHeight) {
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null) {
            throw new IllegalArgumentException("Unknown dimension '" + dimensionId + "' during terrain generation");
        }
        ILuaValue generator = terrainGenerators.get(dimensionId);
        if (generator != null) {
            ILuaValue[] results = engine.callFunctionMulti(generator,
                    engine.wrapNumber(x), engine.wrapNumber(z), engine.wrapNumber(baseHeight));
            if (results.length >= 1 && results[0].isNumber()) {
                double height = results[0].asDouble();
                if (Double.isFinite(height)) {
                    String blockId = results.length >= 2 && results[1].isString()
                            ? results[1].asString() : cfg.surfaceBlock();
                    if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
                        throw new IllegalStateException("Terrain generator for '" + dimensionId
                                + "' returned invalid block id '" + blockId + "'");
                    }
                    return new TerrainResult((int) Math.floor(height), blockId);
                }
            }
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Terrain generator for '" + dimensionId + "' returned no valid height at (" + x + ", " + z
                            + "); falling back to baseHeight " + baseHeight);
        }
        return new TerrainResult(baseHeight, cfg.surfaceBlock());
    }

    /** Result of one column computation: surface Y + top block id. */
    public record TerrainResult(int height, @NotNull String blockId) {}

    /**
     * Result of the full per-column surface computation: the final surface
     * height and the top block id. Ground columns fill down to minHeight;
     * the Lua block picker may override any Y (e.g. carve void or place
     * floating structures).
     */
    public record SurfaceResult(int height, @NotNull String blockId) {
        public static SurfaceResult ground(int height, String blockId) {
            return new SurfaceResult(height, blockId);
        }
    }

    /**
     * Computes the final surface for one column: terrain generator callback,
     * fallback to the config surface block when absent. Pure logic - the
     * chunk generator only turns the result into blocks.
     */
    @NotNull
    public SurfaceResult computeSurface(@NotNull String dimensionId, int x, int z, int baseHeight) {
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null) {
            throw new IllegalArgumentException("Unknown dimension '" + dimensionId + "' during terrain generation");
        }
        TerrainResult terrain = computeTerrain(dimensionId, x, z, baseHeight);
        return SurfaceResult.ground(terrain.height(), terrain.blockId());
    }

    /**
     * Single source of truth for biome decisions: the Lua biome provider when
     * set, otherwise the deterministic weighted pick over the configured
     * biomes (grouped into `biomeSize` cells). Returns null when the dimension
     * has no biomes.
     */
    @Nullable
    public String computeBiomeId(@NotNull String dimensionId, int x, int z) {
        String providerResult = computeBiome(dimensionId, x, z);
        if (providerResult != null) {
            return providerResult;
        }
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null || cfg.biomes().isEmpty()) {
            return null;
        }
        int cellSize = cfg.biomeSize();
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        long hash = (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xBF58476D1CE4E5B9L);
        hash ^= hash >>> 32;
        int total = 0;
        for (DimensionConfig.BiomeEntry entry : cfg.biomes()) {
            total += entry.weight();
        }
        int roll = (int) ((hash & 0x7FFFFFFFFFFFFFFFL) % Math.max(1, total));
        for (DimensionConfig.BiomeEntry entry : cfg.biomes()) {
            roll -= entry.weight();
            if (roll < 0) return entry.id();
        }
        return cfg.biomes().get(0).id();
    }

    /**
     * Invokes the biome provider callback: {@code function(x, z) -> biomeId}.
     * Returns null when no provider is set or the callback produced no string.
     */
    @Nullable
    public String computeBiome(@NotNull String dimensionId, int x, int z) {
        ILuaValue provider = biomeProviders.get(dimensionId);
        if (provider == null) return null;
        ILuaValue result = engine.callFunction(provider, engine.wrapNumber(x), engine.wrapNumber(z));
        if (result != null && result.isString()) {
            return result.asString();
        }
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "Biome provider for '" + dimensionId + "' returned no biome id at (" + x + ", " + z + ")");
        return null;
    }

    // -------------------------------------------------------------------------
    // Config parsing
    // -------------------------------------------------------------------------

    private DimensionConfig parseConfig(String dimensionId, ILuaTable t) {
        boolean hasSkyLight = bool(t, "hasSkyLight", true);
        boolean hasCeiling = bool(t, "hasCeiling", false);
        boolean ultraWarm = bool(t, "ultraWarm", false);
        boolean natural = bool(t, "natural", true);
        double coordinateScale = number(t, "coordinateScale", 1.0);
        if (!Double.isFinite(coordinateScale) || coordinateScale <= 0.0) {
            throw new IllegalArgumentException("coordinateScale must be a positive finite number, got: " + coordinateScale);
        }
        boolean bedWorks = bool(t, "bedWorks", true);
        boolean respawnAnchorWorks = bool(t, "respawnAnchorWorks", false);
        boolean piglinSafe = bool(t, "piglinSafe", false);
        boolean hasRaids = bool(t, "hasRaids", false);
        int monsterSpawnLightLevel = intRange(t, "monsterSpawnLightLevel", 7, 0, 15);
        int monsterSpawnBlockLightLimit = intRange(t, "monsterSpawnBlockLightLimit", 0, 0, 15);
        String infiniburn = string(t, "infiniburn", DimensionConfig.DEFAULT_INFINIBURN);
        String effectsLocation = string(t, "effectsLocation", DimensionConfig.DEFAULT_EFFECTS);
        if (!effectsLocation.contains(":")) {
            throw new IllegalArgumentException("effectsLocation must be a resource location (e.g. 'minecraft:the_nether'), got: " + effectsLocation);
        }
        Long fixedTime = optionalLong(t, "fixedTime");

        int skyColor = intColor(t, "skyColor", DimensionConfig.DEFAULT_SKY_COLOR);
        int fogColor = intColor(t, "fogColor", DimensionConfig.DEFAULT_FOG_COLOR);
        double ambientLight = number(t, "ambientLight", DimensionConfig.DEFAULT_AMBIENT_LIGHT);
        if (ambientLight < 0.0 || ambientLight > 1.0) {
            throw new IllegalArgumentException("ambientLight must be between 0.0 and 1.0, got: " + ambientLight);
        }

        String terrain = string(t, "terrain", "custom");
        if (!"custom".equals(terrain)) {
            throw new IllegalArgumentException("terrain must be 'custom' (vanilla generators are not supported), got: " + terrain);
        }

        int seaLevel = intRange(t, "seaLevel", DimensionConfig.DEFAULT_SEA_LEVEL, -1000, 1000);
        int minHeight = intRange(t, "minHeight", DimensionConfig.DEFAULT_MIN_HEIGHT, -2032, 4064);
        int maxHeight = intRange(t, "maxHeight", DimensionConfig.DEFAULT_MAX_HEIGHT, -2032, 4064);
        if (minHeight >= maxHeight) {
            throw new IllegalArgumentException("minHeight (" + minHeight + ") must be < maxHeight (" + maxHeight + ")");
        }
        if (seaLevel <= minHeight || seaLevel >= maxHeight) {
            throw new IllegalArgumentException("seaLevel (" + seaLevel + ") must be between minHeight (" + minHeight
                    + ") and maxHeight (" + maxHeight + ")");
        }
        int logicalHeight = intRange(t, "logicalHeight", maxHeight - minHeight, 0, 4064 - minHeight);
        if (logicalHeight > maxHeight - minHeight) {
            logicalHeight = maxHeight - minHeight;
        }

        String surfaceBlock = resourceId(t, "surfaceBlock", "minecraft:grass_block");
        String subsurfaceBlock = resourceId(t, "subsurfaceBlock", "minecraft:dirt");
        String fillerBlock = resourceId(t, "fillerBlock", "minecraft:stone");
        String waterBlock = resourceId(t, "waterBlock", "minecraft:water");
        boolean hasBedrock = bool(t, "hasBedrock", false);
        int biomeSize = intRange(t, "biomeSize", DimensionConfig.DEFAULT_BIOME_SIZE, 1, 128);
        Integer spawnX = optionalInt(t, "spawnX");
        Integer spawnZ = optionalInt(t, "spawnZ");
        if ((spawnX == null) != (spawnZ == null)) {
            throw new IllegalArgumentException("spawnX and spawnZ must be set together (or both omitted)");
        }

        List<DimensionConfig.BiomeEntry> biomes = parseBiomes(t.rawget("biomes"));
        List<DimensionConfig.SpawnEntry> spawnEntities = parseSpawnEntities(t.rawget("spawnEntities"));

        return new DimensionConfig(
                dimensionId,
                hasSkyLight, hasCeiling, ultraWarm, natural, coordinateScale, bedWorks, respawnAnchorWorks,
                piglinSafe, hasRaids, monsterSpawnLightLevel, monsterSpawnBlockLightLimit,
                infiniburn, effectsLocation, fixedTime,
                skyColor, fogColor, ambientLight,
                seaLevel, minHeight, maxHeight, logicalHeight,
                surfaceBlock, subsurfaceBlock, fillerBlock, waterBlock, hasBedrock, biomeSize, spawnX, spawnZ,
                biomes, spawnEntities
        );
    }

    private List<DimensionConfig.BiomeEntry> parseBiomes(ILuaValue value) {
        List<DimensionConfig.BiomeEntry> out = new ArrayList<>();
        if (value == null || value.isNil()) return out;
        if (!value.isTable()) {
            throw new IllegalArgumentException("biomes must be a table of {id=..., weight=...} entries");
        }
        ILuaTable table = value.asTable();
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue entry = table.rawget(i);
            if (entry == null || !entry.isTable()) {
                throw new IllegalArgumentException("biomes[" + i + "] must be a table {id=..., weight=...}");
            }
            ILuaTable entryTable = entry.asTable();
            ILuaValue idVal = entryTable.rawget("id");
            if (idVal == null || idVal.isNil() || !idVal.isString()) {
                throw new IllegalArgumentException("biomes[" + i + "] is missing a string 'id'");
            }
            String id = idVal.asString();
            validateId("biome id", id);
            int weight = intRange(entryTable, "weight", 1, 1, Integer.MAX_VALUE);
            out.add(new DimensionConfig.BiomeEntry(id, weight));
        }
        return List.copyOf(out);
    }

    private List<DimensionConfig.SpawnEntry> parseSpawnEntities(ILuaValue value) {
        List<DimensionConfig.SpawnEntry> out = new ArrayList<>();
        if (value == null || value.isNil()) return out;
        if (!value.isTable()) {
            throw new IllegalArgumentException("spawnEntities must be a table of {entity=..., weight=..., minGroup=..., maxGroup=...} entries");
        }
        ILuaTable table = value.asTable();
        for (int i = 1; i <= table.length(); i++) {
            ILuaValue entry = table.rawget(i);
            if (entry == null || !entry.isTable()) {
                throw new IllegalArgumentException("spawnEntities[" + i + "] must be a table {entity=..., weight=...}");
            }
            ILuaTable entryTable = entry.asTable();
            ILuaValue entityVal = entryTable.rawget("entity");
            if (entityVal == null || entityVal.isNil() || !entityVal.isString()) {
                throw new IllegalArgumentException("spawnEntities[" + i + "] is missing a string 'entity'");
            }
            String entity = entityVal.asString();
            validateId("entity id", entity);
            int weight = intRange(entryTable, "weight", 1, 1, Integer.MAX_VALUE);
            int minGroup = intRange(entryTable, "minGroup", 1, 1, 64);
            int maxGroup = intRange(entryTable, "maxGroup", minGroup, minGroup, 64);
            out.add(new DimensionConfig.SpawnEntry(entity, weight, minGroup, maxGroup));
        }
        return List.copyOf(out);
    }

    // Info table for getDimension
    // -------------------------------------------------------------------------

    private ILuaTable buildInfoTable(DimensionConfig cfg) {
        ILuaTable table = engine.createTable();
        table.rawset("id", cfg.id());
        table.rawset("hasSkyLight", cfg.hasSkyLight());
        table.rawset("hasCeiling", cfg.hasCeiling());
        table.rawset("ultraWarm", cfg.ultraWarm());
        table.rawset("natural", cfg.natural());
        table.rawset("coordinateScale", cfg.coordinateScale());
        table.rawset("bedWorks", cfg.bedWorks());
        table.rawset("respawnAnchorWorks", cfg.respawnAnchorWorks());
        table.rawset("piglinSafe", cfg.piglinSafe());
        table.rawset("hasRaids", cfg.hasRaids());
        table.rawset("monsterSpawnLightLevel", cfg.monsterSpawnLightLevel());
        table.rawset("monsterSpawnBlockLightLimit", cfg.monsterSpawnBlockLightLimit());
        table.rawset("infiniburn", cfg.infiniburn());
        table.rawset("effectsLocation", cfg.effectsLocation());
        if (cfg.fixedTime() != null) {
            table.rawset("fixedTime", cfg.fixedTime());
        }
        table.rawset("skyColor", cfg.skyColor());
        table.rawset("fogColor", cfg.fogColor());
        table.rawset("ambientLight", cfg.ambientLight());
        table.rawset("seaLevel", cfg.seaLevel());
        table.rawset("minHeight", cfg.minHeight());
        table.rawset("maxHeight", cfg.maxHeight());
        table.rawset("logicalHeight", cfg.logicalHeight());
        table.rawset("surfaceBlock", cfg.surfaceBlock());
        table.rawset("subsurfaceBlock", cfg.subsurfaceBlock());
        table.rawset("fillerBlock", cfg.fillerBlock());
        table.rawset("waterBlock", cfg.waterBlock());
        table.rawset("hasBedrock", cfg.hasBedrock());
        table.rawset("biomeSize", cfg.biomeSize());
        if (cfg.spawnX() != null && cfg.spawnZ() != null) {
            table.rawset("spawnX", cfg.spawnX());
            table.rawset("spawnZ", cfg.spawnZ());
        }
        SpawnPoint spawnOverride = spawnPoints.get(cfg.id());
        if (spawnOverride != null) {
            table.rawset("spawnX", spawnOverride.x());
            table.rawset("spawnZ", spawnOverride.z());
        }
        table.rawset("hasTerrainGenerator", terrainGenerators.containsKey(cfg.id()));
        table.rawset("hasBiomeProvider", biomeProviders.containsKey(cfg.id()));
        table.rawset("hasBlockPicker", blockPickers.containsKey(cfg.id()));

        ILuaTable biomesTable = engine.createTable();
        for (int i = 0; i < cfg.biomes().size(); i++) {
            DimensionConfig.BiomeEntry b = cfg.biomes().get(i);
            ILuaTable entry = engine.createTable();
            entry.rawset("id", b.id());
            entry.rawset("weight", b.weight());
            biomesTable.rawset(i + 1, entry);
        }
        table.rawset("biomes", biomesTable);

        ILuaTable spawnTable = engine.createTable();
        for (int i = 0; i < cfg.spawnEntities().size(); i++) {
            DimensionConfig.SpawnEntry s = cfg.spawnEntities().get(i);
            ILuaTable entry = engine.createTable();
            entry.rawset("entity", s.entity());
            entry.rawset("weight", s.weight());
            entry.rawset("minGroup", s.minGroup());
            entry.rawset("maxGroup", s.maxGroup());
            spawnTable.rawset(i + 1, entry);
        }
        table.rawset("spawnEntities", spawnTable);

        ILuaTable portalsTable = engine.createTable();
        Map<String, String> portalsForDimension = new LinkedHashMap<>();
        for (Map.Entry<String, String> p : portals.entrySet()) {
            if (p.getValue().equals(cfg.id())) {
                portalsForDimension.put(p.getKey(), p.getValue());
            }
        }
        for (Map.Entry<String, String> p : portalsForDimension.entrySet()) {
            portalsTable.rawset(p.getKey(), p.getValue());
        }
        table.rawset("portals", portalsTable);
        return table;
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private static boolean bool(ILuaTable t, String key, boolean def) {
        ILuaValue v = t.rawget(key);
        return v != null && !v.isNil() ? v.asBoolean() : def;
    }

    private static double number(ILuaTable t, String key, double def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return def;
        if (!v.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number, got: " + describe(v));
        }
        return v.asDouble();
    }

    private static int intRange(ILuaTable t, String key, int def, int min, int max) {
        double raw = number(t, key, def);
        if (raw < min || raw > max) {
            throw new IllegalArgumentException(key + " must be between " + min + " and " + max + ", got: " + raw);
        }
        return (int) Math.floor(raw);
    }

    private static int intColor(ILuaTable t, String key, int def) {
        int value = intRange(t, key, def, 0, 0xFFFFFF);
        if (!DimensionConfig.isColor(value)) {
            throw new IllegalArgumentException(key + " must be an RGB color 0x000000-0xFFFFFF, got: " + value);
        }
        return value;
    }

    private static String string(ILuaTable t, String key, String def) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return def;
        if (!v.isString()) {
            throw new IllegalArgumentException(key + " must be a string, got: " + describe(v));
        }
        return v.asString();
    }

    private static String resourceId(ILuaTable t, String key, String def) {
        String value = string(t, key, def);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        if (!RESOURCE_LOCATION.matcher(value).matches()) {
            throw new IllegalArgumentException(key + " must be a valid resource location (e.g. 'mymod:block'), got: " + value);
        }
        return value;
    }

    private static Long optionalLong(ILuaTable t, String key) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return null;
        if (!v.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number or nil, got: " + describe(v));
        }
        return (long) Math.floor(v.asDouble());
    }

    private static Integer optionalInt(ILuaTable t, String key) {
        ILuaValue v = t.rawget(key);
        if (v == null || v.isNil()) return null;
        if (!v.isNumber()) {
            throw new IllegalArgumentException(key + " must be a number or nil, got: " + describe(v));
        }
        return (int) Math.floor(v.asDouble());
    }

    private static ILuaValue asFunction(Object luaFunction, String what) {
        if (!(luaFunction instanceof ILuaValue value) || !value.isFunction()) {
            throw new IllegalArgumentException(what + " must be a Lua function, got: "
                    + (luaFunction != null ? luaFunction.getClass().getSimpleName() : "nil"));
        }
        return value;
    }

    private static String describe(ILuaValue v) {
        return v == null ? "nil" : v.getClass().getSimpleName();
    }

    private static void validateId(String name, String id) {
        if (id == null || !RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " must be a valid resource location (e.g. 'mymod:id'), got: " + id);
        }
    }
}
