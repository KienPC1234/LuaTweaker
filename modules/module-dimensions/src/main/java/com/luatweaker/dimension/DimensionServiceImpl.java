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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores parsed dimension configs, Lua terrain/biome callbacks and portal
 * mappings; teleports players through the platform abstraction layer.
 *
 * <p>Terrain has three modes per dimension, selected by the last terrain setter called:
 * {@link TerrainMode#LEGACY} (per-column Lua generator plus pickers),
 * {@link TerrainMode#CHUNK} (one batch callback per chunk) and
 * {@link TerrainMode#SPEC} (declarative spec evaluated natively).</p>
 *
 * <p>Thread-safety: chunk generation runs on worker threads; all maps are
 * concurrent and callbacks are invoked through the (synchronized) engine
 * entry points. {@link #getGenerationVersion()} is bumped whenever terrain
 * definitions change so long-lived chunk generators can drop stale caches.</p>
 */
public class DimensionServiceImpl implements IDimensionService {

    /** Minecraft resource-location charset: [a-z0-9_.-] for path, optional [a-z0-9_.-] namespace. */
    private static final java.util.regex.Pattern RESOURCE_LOCATION = LuaTableValues.RESOURCE_LOCATION;

    private final ILuaEngine engine;
    private final Map<String, DimensionConfig> configs = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> terrainGenerators = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> biomeProviders = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> blockPickers = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> strataPickers = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> chunkTerrains = new ConcurrentHashMap<>();
    private final Map<String, ILuaValue> biomeBatches = new ConcurrentHashMap<>();
    private final Map<String, TerrainSpec> terrainSpecs = new ConcurrentHashMap<>();
    private final Map<String, TerrainSpec> caveSpecs = new ConcurrentHashMap<>();
    private final Map<String, TerrainMode> terrainModes = new ConcurrentHashMap<>();
    private final Map<String, SpawnPoint> spawnPoints = new ConcurrentHashMap<>();
    private final Map<String, String> portals = new ConcurrentHashMap<>();
    private final AtomicLong generationVersion = new AtomicLong(1);

    /** Columns in one chunk (16 x 16); a batch terrain callback returns exactly this many heights. */
    public static final int CHUNK_COLUMNS = 256;
    /** Cells in the 4 x 4 quart grid a batch biome callback returns. */
    public static final int BIOME_QUART_CELLS = 16;
    /**
     * Upper bound on sparse block overrides returned by one batch terrain callback.
     * A full 16 x 320 x 16 volume is 81 920 entries; the cap leaves room for taller
     * dimensions while still rejecting runaway tables before they exhaust the heap.
     */
    public static final int MAX_CHUNK_OVERRIDES = 262_144;

    /** Shared empty result for dimensions without a batch biome provider. */
    private static final String[] EMPTY_BIOME_BATCH = new String[0];

    private static final String DIMENSION_PREFIX = "Dimension '";
    private static final String UNKNOWN_DIMENSION_PREFIX = "Unknown dimension '";
    private static final String PARAM_BLOCK_ID = "blockId";
    private static final String BLOCK_PICKER_PREFIX = "Block picker for '";
    static final String CHUNK_CALLBACK_PREFIX = "Chunk terrain callback for '";
    static final String CHUNK_AT = "' chunk (";
    private static final String WEIGHT_KEY = "weight";

    /**
     * How a dimension's terrain is produced. The last terrain setter called wins,
     * so switching a dimension back to the per-column API is always possible.
     */
    public enum TerrainMode { LEGACY, CHUNK, SPEC }

    /** Custom spawn position for a dimension (overrides the config defaults). */
    public record SpawnPoint(int x, int z) {}

    /**
     * One chunk produced by the batch terrain callback: raw (absolute) surface Y
     * per column, the top block id per column and sparse column overrides.
     */
    public record ChunkTerrainResult(@NotNull int[] heights,
                                     @NotNull String[] surfaces,
                                     @NotNull Map<Integer, Map<Integer, String>> overrides) {}

    public DimensionServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void create(@NotNull String dimensionId, @NotNull ILuaValue config) {
        validateDimensionId(dimensionId);
        if (config == null || !config.isTable()) {
            throw new IllegalArgumentException("config must be a table for dimension '" + dimensionId + "'");
        }
        DimensionConfig parsed = DimensionConfigParser.parse(dimensionId, config.asTable());
        configs.put(dimensionId, parsed);
        generationVersion.incrementAndGet();
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Dimension registered: " + dimensionId + " (minY=" + parsed.minHeight()
                        + ", maxY=" + parsed.maxHeight() + ", surface=" + parsed.surfaceBlock()
                        + ", biomes=" + parsed.biomes().size() + ")");
    }

    @Override
    public void setTerrainGenerator(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateDimensionId(dimensionId);
        ILuaValue fn = asFunction(luaFunction, "terrain generator");
        terrainGenerators.put(dimensionId, fn);
        terrainModes.put(dimensionId, TerrainMode.LEGACY);
        generationVersion.incrementAndGet();
    }

    @Override
    public void setChunkTerrain(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateDimensionId(dimensionId);
        ILuaValue fn = asFunction(luaFunction, "chunk terrain generator");
        chunkTerrains.put(dimensionId, fn);
        terrainModes.put(dimensionId, TerrainMode.CHUNK);
        generationVersion.incrementAndGet();
        if (terrainGenerators.containsKey(dimensionId) || blockPickers.containsKey(dimensionId)
                || strataPickers.containsKey(dimensionId)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    DIMENSION_PREFIX + dimensionId + "' now uses a chunk terrain callback; "
                            + "SetTerrainGenerator/SetBlockPicker/SetStrataPicker are no longer called");
        }
    }

    @Override
    public void setBiomeBatch(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateDimensionId(dimensionId);
        ILuaValue fn = asFunction(luaFunction, "biome batch provider");
        if (biomeProviders.containsKey(dimensionId)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    DIMENSION_PREFIX + dimensionId + "' now uses a batch biome provider; "
                            + "SetBiomeProvider is no longer called");
        }
        biomeBatches.put(dimensionId, fn);
        biomeProviders.remove(dimensionId);
        generationVersion.incrementAndGet();
    }

    @Override
    public void setTerrainSpec(@NotNull String dimensionId, @NotNull ILuaValue spec) {
        validateDimensionId(dimensionId);
        DimensionConfig config = configs.get(dimensionId);
        if (config == null) {
            throw new IllegalArgumentException(UNKNOWN_DIMENSION_PREFIX + dimensionId
                    + "'; call Dimensions:Create(\"" + dimensionId + "\", {...}) first");
        }
        TerrainSpec parsed = TerrainSpecParser.parse(dimensionId, spec, config);
        if (terrainGenerators.containsKey(dimensionId) || chunkTerrains.containsKey(dimensionId)
                || blockPickers.containsKey(dimensionId) || strataPickers.containsKey(dimensionId)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    DIMENSION_PREFIX + dimensionId + "' now uses a declarative terrain spec; "
                            + "the Lua terrain generator and per-column pickers are no longer called");
        }
        terrainSpecs.put(dimensionId, parsed);
        terrainModes.put(dimensionId, TerrainMode.SPEC);
        generationVersion.incrementAndGet();
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Terrain spec registered for '" + dimensionId + "': " + parsed.heightLayers().size()
                        + " height layer(s), " + parsed.spline().size() + " spline point(s), "
                        + parsed.surfaceRules().size() + " surface rule(s)"
                        + (parsed.density() != null
                            ? ", 3D density (" + parsed.density().layers().size() + " layer(s))"
                            : ""));
    }

    @Override
    public void setBiomeProvider(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateDimensionId(dimensionId);
        ILuaValue fn = asFunction(luaFunction, "biome provider");
        biomeProviders.put(dimensionId, fn);
        biomeBatches.remove(dimensionId);
        generationVersion.incrementAndGet();
    }

    @Override
    public void setCaveSpec(@NotNull String dimensionId, @NotNull ILuaValue spec) {
        validateDimensionId(dimensionId);
        DimensionConfig config = configs.get(dimensionId);
        if (config == null) {
            throw new IllegalArgumentException(UNKNOWN_DIMENSION_PREFIX + dimensionId
                    + "'; call Dimensions:Create(\"" + dimensionId + "\", {...}) first");
        }
        TerrainSpec parsed = TerrainSpecParser.parse(dimensionId, spec, config);
        if (parsed.density() == null) {
            throw new IllegalArgumentException("cave spec for '" + dimensionId
                    + "' needs a 'density' section with 3D layers");
        }
        caveSpecs.put(dimensionId, parsed);
        generationVersion.incrementAndGet();
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Cave spec registered for '" + dimensionId + "': "
                        + parsed.density().layers().size() + " 3D layer(s), threshold "
                        + parsed.density().threshold() + ", cells "
                        + parsed.density().cellSize() + "x" + parsed.density().cellHeight());
    }

    @Override
    public void registerPortal(@NotNull String blockId, @NotNull String targetDimension) {
        BiomeJson.validateId(PARAM_BLOCK_ID, blockId);
        validateDimensionId(targetDimension);
        portals.put(blockId, targetDimension);
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Portal registered: " + blockId + " -> " + targetDimension);
    }

    /** Returns the dimension a portal block leads to, or null. */
    @Nullable
    @Override
    public String getPortalTarget(@NotNull String blockId) {
        BiomeJson.validateId(PARAM_BLOCK_ID, blockId);
        return portals.get(blockId);
    }

    /**
     * Runtime portal consumer: when a player uses a block, the platform
     * block-use path calls this with the used block id. A registered portal
     * source teleports through {@link #teleportTo} (the same code path as the
     * Lua {@code Dimensions:TeleportTo} call); an unregistered id is a no-op
     * returning false.
     *
     * <p>No nether-style frame validation is performed: no frame-check helper
     * exists in the codebase, so any use of the portal block teleports. Mod
     * authors wanting frame gates can check block state in Lua instead.
     */
    public boolean tryUsePortal(@NotNull IEntity player, @NotNull String blockId) {
        if (player == null) {
            throw new IllegalArgumentException("tryUsePortal requires a valid player entity");
        }
        BiomeJson.validateId(PARAM_BLOCK_ID, blockId);
        String target = portals.get(blockId);
        if (target == null) {
            return false;
        }
        teleportTo(player, target);
        com.luatweaker.core.service.AppliedEventHelper.fire("PortalUsed", Map.of(
            PARAM_BLOCK_ID, blockId,
            "targetDimension", target,
            "playerName", player.getName()));
        return true;
    }

    /**
     * Sets a block picker callback: {@code function(x, z, surfaceY, minY) -> {[y] = blockId, ...}}.
     * Returned overrides replace the default column fill at those Y positions
     * (use "minecraft:air" to carve caves).
     */
    @Override
    public void setBlockPicker(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateDimensionId(dimensionId);
        ILuaValue fn = asFunction(luaFunction, "block picker");
        if (getTerrainMode(dimensionId) != TerrainMode.LEGACY) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    BLOCK_PICKER_PREFIX + dimensionId + "' is ignored: the dimension terrain is produced by "
                            + "a chunk terrain callback or a terrain spec");
        }
        blockPickers.put(dimensionId, fn);
        generationVersion.incrementAndGet();
    }

    @Override
    public void setStrataPicker(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateDimensionId(dimensionId);
        ILuaValue fn = asFunction(luaFunction, "strata picker");
        if (getTerrainMode(dimensionId) != TerrainMode.LEGACY) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Strata picker for '" + dimensionId + "' is ignored: the dimension terrain is produced by "
                            + "a chunk terrain callback or a terrain spec");
        }
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
        if (picker == null) {
            return Map.of();
        }
        ILuaValue result = engine.callFunction(picker,
                engine.wrapNumber(x), engine.wrapNumber(z), engine.wrapNumber(surfaceY));
        if (result == null || result.isNil() || !result.isTable()) {
            return Map.of();
        }
        return collectStrataOverrides(result.asTable(), dimensionId);
    }

    @NotNull
    private static Map<Integer, String> collectStrataOverrides(@NotNull ILuaTable table,
            @NotNull String dimensionId) {
        Map<Integer, String> overrides = new ConcurrentHashMap<>();
        for (Map.Entry<ILuaValue, ILuaValue> entry : table.asMap().entrySet()) {
            Integer depth = strataDepth(entry.getKey(), dimensionId);
            String blockId = depth == null ? null : strataBlock(entry.getValue(), dimensionId, depth);
            if (depth != null && blockId != null) {
                overrides.put(depth, blockId);
            }
        }
        return overrides;
    }

    @Nullable
    private static Integer strataDepth(@Nullable ILuaValue depthVal, @NotNull String dimensionId) {
        if (depthVal == null || !depthVal.isNumber()) {
            return null;
        }
        int depth = depthVal.asInt();
        if (depth <= 0 || depth > 512) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Strata picker for '" + dimensionId + "' returned out-of-range depth " + depth + "; ignoring");
            return null;
        }
        return depth;
    }

    @Nullable
    private static String strataBlock(@Nullable ILuaValue blockVal, @NotNull String dimensionId, int depth) {
        if (blockVal == null || blockVal.isNil() || !blockVal.isString()) {
            return null;
        }
        String blockId = blockVal.asString();
        if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Strata picker for '" + dimensionId + "' returned invalid block id '" + blockId + "' at depth " + depth);
            return null;
        }
        return blockId;
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
        if (picker == null) {
            return Map.of();
        }
        ILuaValue result = engine.callFunction(picker,
                engine.wrapNumber(x), engine.wrapNumber(z),
                engine.wrapNumber(surfaceY), engine.wrapNumber(minY));
        if (result == null || result.isNil()) {
            return Map.of();
        }
        if (!result.isTable()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    BLOCK_PICKER_PREFIX + dimensionId + "' returned a non-table; ignoring overrides");
            return Map.of();
        }
        Map<Integer, String> overrides = new ConcurrentHashMap<>();
        ILuaTable table = result.asTable();
        for (Map.Entry<ILuaValue, ILuaValue> entry : table.asMap().entrySet()) {
            putBlockOverride(overrides, entry, dimensionId, minY, maxY);
        }
        return overrides;
    }

    private void putBlockOverride(@NotNull Map<Integer, String> overrides,
            @NotNull Map.Entry<ILuaValue, ILuaValue> entry,
            @NotNull String dimensionId, int minY, int maxY) {
        Integer y = blockOverrideY(entry.getKey());
        String blockId = y == null ? null : blockOverrideId(entry.getValue(), dimensionId, y);
        if (y == null || blockId == null) {
            return;
        }
        if (y < minY || y >= maxY) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    BLOCK_PICKER_PREFIX + dimensionId + "' returned out-of-range Y " + y
                            + " (range " + minY + ".." + (maxY - 1) + "); ignoring");
            return;
        }
        overrides.put(y, blockId);
    }

    @Nullable
    private static Integer blockOverrideY(@Nullable ILuaValue yVal) {
        if (yVal == null || !yVal.isNumber()) {
            return null;
        }
        return yVal.asInt();
    }

    @Nullable
    private String blockOverrideId(@Nullable ILuaValue blockVal, @NotNull String dimensionId, int y) {
        if (blockVal == null || blockVal.isNil() || !blockVal.isString()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    BLOCK_PICKER_PREFIX + dimensionId + "' returned invalid entry at Y " + y);
            return null;
        }
        String blockId = blockVal.asString();
        if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    BLOCK_PICKER_PREFIX + dimensionId + "' returned invalid block id '" + blockId + "' at Y " + y);
            return null;
        }
        return blockId;
    }

    /** Sets a custom spawn position for the dimension. */
    @Override
    public void setSpawnPoint(@NotNull String dimensionId, int x, int z) {
        validateDimensionId(dimensionId);
        spawnPoints.put(dimensionId, new SpawnPoint(x, z));
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Spawn point set for '" + dimensionId + "': (" + x + ", " + z + ")");
    }

    /** Returns the spawn point (runtime override, else config), or null. */
    @Nullable
    public SpawnPoint getSpawnPoint(@NotNull String dimensionId) {
        SpawnPoint override = spawnPoints.get(dimensionId);
        if (override != null) {
            return override;
        }
        DimensionConfig config = configs.get(dimensionId);
        if (config != null && config.spawnX() != null && config.spawnZ() != null) {
            return new SpawnPoint(config.spawnX(), config.spawnZ());
        }
        return null;
    }

    @Override
    public void teleportTo(@NotNull IEntity player, @NotNull String dimensionId) {
        validateDimensionId(dimensionId);
        requireRegisteredDimension(dimensionId);
        Object raw = player != null ? player.getRawEntity() : null;
        if (raw == null) {
            throw new IllegalArgumentException("teleportTo requires a valid player entity");
        }
        requirePlatformReady("teleport");
        boolean ok = Platform.getDimension().teleportToDimension(raw, dimensionId);
        if (!ok) {
            throw new IllegalStateException("Dimension level '" + dimensionId
                    + "' is not loaded; the server must create the world first");
        }
    }

    private void requireRegisteredDimension(@NotNull String dimensionId) {
        if (configs.get(dimensionId) == null) {
            throw new IllegalArgumentException(UNKNOWN_DIMENSION_PREFIX + dimensionId
                    + "'. Call Dimensions:Create(\"" + dimensionId + "\", {...}) first.");
        }
    }

    private static void requirePlatformReady(@NotNull String action) {
        if (!Platform.isInitialized()) {
            throw new IllegalStateException("Platform is not initialized; cannot " + action + " (unit-test environment?)");
        }
    }

    @Override
    @Nullable
    public Object getDimension(@NotNull String dimensionId) {
        validateDimensionId(dimensionId);
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null) {
            return null;
        }
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

    /** Returns all registered dimension ids, sorted for deterministic order. */
    @Override
    public @NotNull List<String> getDimensionIds() {
        List<String> ids = new ArrayList<>(configs.keySet());
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    @Override
    public @Nullable String getEntityDimension(@NotNull IEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("getEntityDimension requires a valid entity");
        }
        Object raw = entity.getRawEntity();
        if (raw == null) {
            throw new IllegalArgumentException("getEntityDimension requires a valid entity");
        }
        requirePlatformReady("query entity dimension");
        return Platform.getDimension().getEntityDimensionId(raw);
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
        DimensionConfig cfg = requireTerrainConfig(dimensionId);
        ILuaValue generator = terrainGenerators.get(dimensionId);
        if (generator == null) {
            return new TerrainResult(baseHeight, cfg.surfaceBlock());
        }
        TerrainResult generated = resolveGeneratedTerrain(generator, dimensionId, x, z, baseHeight, cfg);
        if (generated != null) {
            return generated;
        }
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "Terrain generator for '" + dimensionId + "' returned no valid height at (" + x + ", " + z
                        + "); falling back to baseHeight " + baseHeight);
        return new TerrainResult(baseHeight, cfg.surfaceBlock());
    }

    private DimensionConfig requireTerrainConfig(@NotNull String dimensionId) {
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null) {
            throw new IllegalArgumentException(UNKNOWN_DIMENSION_PREFIX + dimensionId + "' during terrain generation");
        }
        return cfg;
    }

    @Nullable
    private TerrainResult resolveGeneratedTerrain(@NotNull ILuaValue generator, @NotNull String dimensionId,
            int x, int z, int baseHeight, @NotNull DimensionConfig cfg) {
        ILuaValue[] results = engine.callFunctionMulti(generator,
                engine.wrapNumber(x), engine.wrapNumber(z), engine.wrapNumber(baseHeight));
        if (results.length < 1 || !results[0].isNumber()) {
            return null;
        }
        double height = results[0].asDouble();
        if (!Double.isFinite(height)) {
            return null;
        }
        String blockId = results.length >= 2 && results[1].isString()
                ? results[1].asString() : cfg.surfaceBlock();
        if (!RESOURCE_LOCATION.matcher(blockId).matches()) {
            throw new IllegalStateException("Terrain generator for '" + dimensionId
                    + "' returned invalid block id '" + blockId + "'");
        }
        return new TerrainResult((int) Math.floor(height), blockId);
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
        requireTerrainConfig(dimensionId);
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
            if (roll < 0) {
                return entry.id();
            }
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
        if (provider == null) {
            return null;
        }
        ILuaValue result = engine.callFunction(provider, engine.wrapNumber(x), engine.wrapNumber(z));
        if (result != null && result.isString()) {
            return result.asString();
        }
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "Biome provider for '" + dimensionId + "' returned no biome id at (" + x + ", " + z + ")");
        return null;
    }

    // -------------------------------------------------------------------------
    // Batch terrain / biome (one Lua call per chunk)
    // -------------------------------------------------------------------------

    /** Effective terrain mode; {@link TerrainMode#LEGACY} when nothing else was registered. */
    @NotNull
    public TerrainMode getTerrainMode(@NotNull String dimensionId) {
        return terrainModes.getOrDefault(dimensionId, TerrainMode.LEGACY);
    }

    /** True when a batch chunk terrain callback is registered (regardless of the active mode). */
    public boolean hasChunkTerrain(@NotNull String dimensionId) {
        return chunkTerrains.containsKey(dimensionId);
    }

    /** True when a batch biome provider is registered. */
    public boolean hasBiomeBatch(@NotNull String dimensionId) {
        return biomeBatches.containsKey(dimensionId);
    }

    /** True when a declarative terrain spec is registered (regardless of the active mode). */
    public boolean hasTerrainSpec(@NotNull String dimensionId) {
        return terrainSpecs.containsKey(dimensionId);
    }

    /** Returns the registered declarative terrain spec, or null. */
    @Nullable
    public TerrainSpec getTerrainSpec(@NotNull String dimensionId) {
        return terrainSpecs.get(dimensionId);
    }

    /** True when a cave spec is registered for the dimension. */
    public boolean hasCaveSpec(@NotNull String dimensionId) {
        return caveSpecs.containsKey(dimensionId);
    }

    /** Returns the registered cave spec, or null. */
    @Nullable
    public TerrainSpec getCaveSpec(@NotNull String dimensionId) {
        return caveSpecs.get(dimensionId);
    }

    // -------------------------------------------------------------------------
    // World queries (same pipeline as generation, no Minecraft access)
    // -------------------------------------------------------------------------

    /** Upper bound for {@link #findSurface}: 64 blocks is a 9 x 9 chunk scan. */
    public static final int MAX_SURFACE_SEARCH = 64;

    /** One chunk of queried columns: absolute heights, top block ids and slopes. */
    private record ChunkColumns(@NotNull int[] heights, @NotNull String[] surfaces,
                                @NotNull float[] slopes) {}

    /**
     * Surface Y of one world column, or {@code minY - 1} for a void column.
     *
     * <p>SPEC dimensions sample one column (or one chunk density field); CHUNK dimensions run
     * the chunk callback once per call, so bulk reads should use {@link #previewChunk}.</p>
     */
    @Override
    public int getColumnHeight(@NotNull String dimensionId, int x, int z) {
        DimensionConfig cfg = requireConfig(dimensionId);
        TerrainMode mode = getTerrainMode(dimensionId);
        if (mode == TerrainMode.SPEC) {
            TerrainSampler sampler = createTerrainSampler(dimensionId);
            if (sampler == null) {
                throw new IllegalStateException(DIMENSION_PREFIX + dimensionId
                        + "' is in spec mode but has no terrain spec");
            }
            if (sampler.hasDensity()) {
                DensityField field = sampler.sampleDensity(
                        Math.floorDiv(x, 16), Math.floorDiv(z, 16), cfg.minHeight(), cfg.maxHeight());
                return TerrainColumn.clampHeight(
                        field.topSolid(Math.floorMod(x, 16), Math.floorMod(z, 16)),
                        cfg.minHeight(), cfg.maxHeight());
            }
            return TerrainColumn.clampHeight(sampler.heightAt(x, z), cfg.minHeight(), cfg.maxHeight());
        }
        if (mode == TerrainMode.CHUNK) {
            ChunkTerrainResult result = computeChunkTerrain(
                    dimensionId, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            return TerrainColumn.clampHeight(result.heights()[columnIndex(x, z)],
                    cfg.minHeight(), cfg.maxHeight());
        }
        return TerrainColumn.clampHeight(
                computeSurface(dimensionId, x, z, cfg.seaLevel()).height(),
                cfg.minHeight(), cfg.maxHeight());
    }

    /** Top block id of a world column, or null when the column has no ground. */
    @Nullable
    @Override
    public String getSurfaceBlock(@NotNull String dimensionId, int x, int z) {
        DimensionConfig cfg = requireConfig(dimensionId);
        TerrainMode mode = getTerrainMode(dimensionId);
        if (mode == TerrainMode.SPEC) {
            return surfaceBlockSpec(cfg, dimensionId, x, z);
        }
        if (mode == TerrainMode.CHUNK) {
            return surfaceBlockChunk(cfg, dimensionId, x, z);
        }
        SurfaceResult surface = computeSurface(dimensionId, x, z, cfg.seaLevel());
        int top = TerrainColumn.clampHeight(surface.height(), cfg.minHeight(), cfg.maxHeight());
        return top <= cfg.minHeight() ? null : surface.blockId();
    }

    @Nullable
    private String surfaceBlockSpec(@NotNull DimensionConfig cfg, @NotNull String dimensionId, int x, int z) {
        TerrainSampler sampler = createTerrainSampler(dimensionId);
        if (sampler == null) {
            throw new IllegalStateException(DIMENSION_PREFIX + dimensionId
                    + "' is in spec mode but has no terrain spec");
        }
        if (sampler.hasDensity()) {
            // One density sample for the chunk beats sampling the column and its neighbours.
            ChunkColumns columns = sampleChunkColumns(cfg, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            int index = columnIndex(x, z);
            return columns.heights()[index] <= cfg.minHeight() ? null : columns.surfaces()[index];
        }
        int height = TerrainColumn.clampHeight(sampler.heightAt(x, z), cfg.minHeight(), cfg.maxHeight());
        if (height <= cfg.minHeight()) {
            return null;
        }
        String rule = sampler.surfaceBlockAt(0, height, getSlope(dimensionId, x, z));
        return rule != null ? rule : cfg.surfaceBlock();
    }

    @Nullable
    private String surfaceBlockChunk(@NotNull DimensionConfig cfg, @NotNull String dimensionId, int x, int z) {
        ChunkTerrainResult result = computeChunkTerrain(
                dimensionId, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        int index = columnIndex(x, z);
        int top = TerrainColumn.clampHeight(result.heights()[index],
                cfg.minHeight(), cfg.maxHeight());
        return top <= cfg.minHeight() ? null : result.surfaces()[index];
    }

    /**
     * Local slope in blocks per block: the larger of the central X and Z height differences.
     * Void columns report 0.
     */
    @Override
    public double getSlope(@NotNull String dimensionId, int x, int z) {
        DimensionConfig cfg = requireConfig(dimensionId);
        if (getTerrainMode(dimensionId) == TerrainMode.SPEC) {
            OptionalDouble slope = specSlope(cfg, dimensionId, x, z);
            if (slope.isPresent()) {
                return slope.getAsDouble();
            }
        }
        int center = getColumnHeight(dimensionId, x, z);
        if (center <= cfg.minHeight()) {
            return 0.0;
        }
        int left = getColumnHeight(dimensionId, x - 1, z);
        int right = getColumnHeight(dimensionId, x + 1, z);
        int down = getColumnHeight(dimensionId, x, z - 1);
        int up = getColumnHeight(dimensionId, x, z + 1);
        double slopeX = (right - left) * 0.5;
        double slopeZ = (up - down) * 0.5;
        return Math.max(Math.abs(slopeX), Math.abs(slopeZ));
    }

    @NotNull
    private OptionalDouble specSlope(@NotNull DimensionConfig cfg,
            @NotNull String dimensionId, int x, int z) {
        TerrainSampler sampler = createTerrainSampler(dimensionId);
        if (sampler == null || !sampler.hasDensity()) {
            return OptionalDouble.empty();
        }
        // Clamped in-chunk neighbours: sampling the border would cost another field.
        DensityField field = sampler.sampleDensity(
                Math.floorDiv(x, 16), Math.floorDiv(z, 16), cfg.minHeight(), cfg.maxHeight());
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        int center = field.topSolid(localX, localZ);
        if (center <= cfg.minHeight()) {
            return OptionalDouble.of(0.0);
        }
        int left = field.topSolid(Math.max(0, localX - 1), localZ);
        int right = field.topSolid(Math.min(15, localX + 1), localZ);
        int down = field.topSolid(localX, Math.max(0, localZ - 1));
        int up = field.topSolid(localX, Math.min(15, localZ + 1));
        return OptionalDouble.of(
                Math.max(Math.abs((right - left) * 0.5), Math.abs((up - down) * 0.5)));
    }

    /** Biome id the biome source resolves at a world position (never null when biomes exist). */
    @NotNull
    @Override
    public String getBiomeAt(@NotNull String dimensionId, int x, int z) {
        requireConfig(dimensionId);
        if (hasBiomeBatch(dimensionId)) {
            String[] batch = computeBiomeBatch(
                    dimensionId, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            int index = Math.floorMod(z, 16) / 4 * 4 + Math.floorMod(x, 16) / 4;
            if (index < batch.length && batch[index] != null) {
                return batch[index];
            }
        }
        String biome = computeBiomeId(dimensionId, x, z);
        if (biome == null) {
            throw new IllegalStateException(DIMENSION_PREFIX + dimensionId
                    + "' has no biomes; configure biomes in Dimensions:Create");
        }
        return biome;
    }

    /**
     * Samples one chunk without touching Minecraft: heights, top blocks, slopes and the
     * 4 x 4 biome quart grid. Terrain and biome pipelines run once each.
     */
    @NotNull
    @Override
    public ILuaTable previewChunk(@NotNull String dimensionId, int chunkX, int chunkZ) {
        DimensionConfig cfg = requireConfig(dimensionId);
        ChunkColumns columns = sampleChunkColumns(cfg, chunkX, chunkZ);

        ILuaTable heightsTable = engine.createTable();
        ILuaTable surfacesTable = engine.createTable();
        ILuaTable slopesTable = engine.createTable();
        copyColumnTables(columns, heightsTable, surfacesTable, slopesTable);

        ILuaTable biomesTable = engine.createTable();
        String[] batch = hasBiomeBatch(dimensionId)
                ? computeBiomeBatch(dimensionId, chunkX, chunkZ) : new String[0];
        copyBiomeQuart(dimensionId, chunkX, chunkZ, batch, biomesTable);

        ILuaTable out = engine.createTable();
        out.rawset("chunkX", chunkX);
        out.rawset("chunkZ", chunkZ);
        out.rawset("heights", heightsTable);
        out.rawset("surfaces", surfacesTable);
        out.rawset("slopes", slopesTable);
        out.rawset("biomes", biomesTable);
        return out;
    }

    private void copyColumnTables(@NotNull ChunkColumns columns, @NotNull ILuaTable heightsTable,
            @NotNull ILuaTable surfacesTable, @NotNull ILuaTable slopesTable) {
        for (int i = 0; i < CHUNK_COLUMNS; i++) {
            heightsTable.rawset(i + 1, columns.heights()[i]);
            slopesTable.rawset(i + 1, columns.slopes()[i]);
            if (columns.surfaces()[i] != null) {
                surfacesTable.rawset(i + 1, columns.surfaces()[i]);
            }
        }
    }

    private void copyBiomeQuart(@NotNull String dimensionId, int chunkX, int chunkZ,
            @NotNull String[] batch, @NotNull ILuaTable biomesTable) {
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                int index = qz * 4 + qx;
                String biome = index < batch.length ? batch[index] : null;
                if (biome == null) {
                    biome = computeBiomeId(dimensionId, baseX + qx * 4, baseZ + qz * 4);
                }
                if (biome != null) {
                    biomesTable.rawset(index + 1, biome);
                }
            }
        }
    }

    /**
     * Nearest buildable surface within {@code radius} blocks: not void, and not underwater
     * when the dimension fills water. Chunks are scanned once each, by ring, so the nearest
     * ring wins.
     */
    @Nullable
    @Override
    public ILuaTable findSurface(@NotNull String dimensionId, int x, int z, int radius) {
        DimensionConfig cfg = requireConfig(dimensionId);
        if (radius < 0 || radius > MAX_SURFACE_SEARCH) {
            throw new IllegalArgumentException("findSurface radius must be between 0 and "
                    + MAX_SURFACE_SEARCH + ", got: " + radius);
        }
        SurfaceHit hit = scanRingsForSurface(cfg, x, z, radius);
        if (hit == null) {
            return null;
        }

        ILuaTable out = engine.createTable();
        out.rawset("x", hit.x());
        out.rawset("y", hit.y());
        out.rawset("z", hit.z());
        out.rawset("block", hit.block());
        out.rawset("biome", getBiomeAt(dimensionId, hit.x(), hit.z()));
        return out;
    }

    /** Nearest buildable column hit, or null when no ring produced a candidate. */
    private record SurfaceHit(int x, int y, int z, @NotNull String block) {
    }

    @Nullable
    private SurfaceHit scanRingsForSurface(@NotNull DimensionConfig cfg, int x, int z, int radius) {
        int seaLevel = effectiveSeaLevel(cfg);
        boolean fillsWater = waterEnabled(cfg);
        int chunkRadius = (radius + 15) / 16;
        int centerChunkX = Math.floorDiv(x, 16);
        int centerChunkZ = Math.floorDiv(z, 16);

        for (int ring = 0; ring <= chunkRadius; ring++) {
            SurfaceHit hit = scanRing(cfg, x, z, radius, seaLevel, fillsWater,
                    centerChunkX, centerChunkZ, ring);
            if (hit != null) {
                return hit; // the nearest ring already produced a candidate
            }
        }
        return null;
    }

    @Nullable
    private SurfaceHit scanRing(@NotNull DimensionConfig cfg, int x, int z, int radius,
            int seaLevel, boolean fillsWater, int centerChunkX, int centerChunkZ, int ring) {
        long maxDistance = (long) radius * radius;
        long bestDistance = Long.MAX_VALUE;
        Optional<SurfaceHit> best = Optional.empty();
        for (int cx = centerChunkX - ring; cx <= centerChunkX + ring; cx++) {
            for (int cz = centerChunkZ - ring; cz <= centerChunkZ + ring; cz++) {
                if (Math.max(Math.abs(cx - centerChunkX), Math.abs(cz - centerChunkZ)) != ring) {
                    continue; // only the current ring
                }
                ChunkColumns columns = sampleChunkColumns(cfg, cx, cz);
                Optional<SurfaceHit> hit = scanChunkBest(cfg, columns, x, z, cx, cz,
                        seaLevel, fillsWater, maxDistance, bestDistance);
                if (hit.isPresent()) {
                    best = hit;
                    bestDistance = distanceOf(x, z, hit.get().x(), hit.get().z());
                }
            }
        }
        return best.orElse(null);
    }

    @NotNull
    private Optional<SurfaceHit> scanChunkBest(@NotNull DimensionConfig cfg, @NotNull ChunkColumns columns,
            int x, int z, int cx, int cz, int seaLevel, boolean fillsWater,
            long maxDistance, long bestDistance) {
        Optional<SurfaceHit> best = Optional.empty();
        long nearest = bestDistance;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = cx * 16 + lx;
                int worldZ = cz * 16 + lz;
                long distance = distanceOf(x, z, worldX, worldZ);
                if (distance > maxDistance || distance >= nearest) {
                    continue;
                }
                SurfaceHit hit = checkColumn(cfg, columns, lx, lz, worldX, worldZ,
                        seaLevel, fillsWater);
                if (hit != null) {
                    best = Optional.of(hit);
                    nearest = distance;
                }
            }
        }
        return best;
    }

    private static long distanceOf(int x, int z, int worldX, int worldZ) {
        long dx = worldX - x;
        long dz = worldZ - z;
        return dx * dx + dz * dz;
    }

    @Nullable
    private SurfaceHit checkColumn(@NotNull DimensionConfig cfg, @NotNull ChunkColumns columns,
            int lx, int lz, int worldX, int worldZ,
            int seaLevel, boolean fillsWater) {
        int height = columns.heights()[lz * 16 + lx];
        if (height <= cfg.minHeight()) {
            return null;
        }
        if (fillsWater && height < seaLevel) {
            return null;
        }
        String block = columns.surfaces()[lz * 16 + lx];
        if (block == null) {
            return null;
        }
        return new SurfaceHit(worldX, height, worldZ, block);
    }

    /** Sea level including a terrain spec override. */
    private int effectiveSeaLevel(@NotNull DimensionConfig cfg) {
        TerrainSpec spec = terrainSpecs.get(cfg.id());
        if (spec != null && spec.seaLevel() != null) {
            return spec.seaLevel();
        }
        return cfg.seaLevel();
    }

    /** False only for spec dimensions that opt out of water fill. */
    private boolean waterEnabled(@NotNull DimensionConfig cfg) {
        TerrainSpec spec = terrainSpecs.get(cfg.id());
        return spec == null || spec.water();
    }

    /**
     * Fills the 256 columns of a chunk through the active pipeline. Used by previews and the
     * spawn scan so they cost one chunk evaluation instead of 256 column queries.
     */
    @NotNull
    private ChunkColumns sampleChunkColumns(@NotNull DimensionConfig cfg, int chunkX, int chunkZ) {
        int[] heights = new int[CHUNK_COLUMNS];
        String[] surfaces = new String[CHUNK_COLUMNS];
        float[] slopes;
        TerrainMode mode = getTerrainMode(cfg.id());

        if (mode == TerrainMode.SPEC) {
            slopes = fillSpecColumns(cfg, chunkX, chunkZ, heights, surfaces);
        } else if (mode == TerrainMode.CHUNK) {
            slopes = fillChunkCallbackColumns(cfg, chunkX, chunkZ, heights, surfaces);
        } else {
            fillLegacyColumns(cfg, chunkX, chunkZ, heights, surfaces, cfg.minHeight());
            slopes = inChunkSlopes(heights);
        }
        return new ChunkColumns(heights, surfaces, slopes);
    }

    @NotNull
    private float[] fillSpecColumns(@NotNull DimensionConfig cfg, int chunkX, int chunkZ,
            @NotNull int[] heights, @NotNull String... surfaces) {
        int minY = cfg.minHeight();
        int maxY = cfg.maxHeight();
        TerrainSampler sampler = createTerrainSampler(cfg.id());
        if (sampler == null) {
            throw new IllegalStateException(DIMENSION_PREFIX + cfg.id()
                    + "' is in spec mode but has no terrain spec");
        }
        if (sampler.hasDensity()) {
            fillDensityColumns(sampler, cfg, chunkX, chunkZ, heights, surfaces, minY, maxY);
            return inChunkSlopes(heights);
        }
        int[] rawHeights = new int[CHUNK_COLUMNS];
        float[] slopes = new float[CHUNK_COLUMNS];
        sampler.sampleChunk(chunkX, chunkZ, rawHeights, slopes);
        for (int i = 0; i < CHUNK_COLUMNS; i++) {
            int top = TerrainColumn.clampHeight(rawHeights[i], minY, maxY);
            heights[i] = top;
            if (top > minY) {
                surfaces[i] = surfaceFor(sampler, cfg, top, slopes[i]);
            }
        }
        return slopes;
    }

    private void fillDensityColumns(@NotNull TerrainSampler sampler, @NotNull DimensionConfig cfg,
            int chunkX, int chunkZ, @NotNull int[] heights, @NotNull String[] surfaces,
            int minY, int maxY) {
        DensityField field = sampler.sampleDensity(chunkX, chunkZ, minY, maxY);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = z * 16 + x;
                int top = TerrainColumn.clampHeight(field.topSolid(x, z), minY, maxY);
                heights[i] = top;
                if (top > minY) {
                    surfaces[i] = surfaceFor(sampler, cfg, top, 0.0);
                }
            }
        }
    }

    @NotNull
    private float[] fillChunkCallbackColumns(@NotNull DimensionConfig cfg, int chunkX, int chunkZ,
            @NotNull int[] heights, @NotNull String... surfaces) {
        int minY = cfg.minHeight();
        int maxY = cfg.maxHeight();
        ChunkTerrainResult result = computeChunkTerrain(cfg.id(), chunkX, chunkZ);
        for (int i = 0; i < CHUNK_COLUMNS; i++) {
            int top = TerrainColumn.clampHeight(result.heights()[i], minY, maxY);
            heights[i] = top;
            if (top > minY) {
                surfaces[i] = result.surfaces()[i];
            }
        }
        return inChunkSlopes(heights);
    }

    private void fillLegacyColumns(@NotNull DimensionConfig cfg, int chunkX, int chunkZ,
            @NotNull int[] heights, @NotNull String[] surfaces, int minY) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                SurfaceResult surface = computeSurface(
                        cfg.id(), chunkX * 16 + x, chunkZ * 16 + z, cfg.seaLevel());
                int top = TerrainColumn.clampHeight(surface.height(), minY, cfg.maxHeight());
                heights[z * 16 + x] = top;
                if (top > minY) {
                    surfaces[z * 16 + x] = surface.blockId();
                }
            }
        }
    }

    @NotNull
    private static String surfaceFor(@NotNull TerrainSampler sampler, @NotNull DimensionConfig cfg,
                                     int y, double slope) {
        String rule = sampler.surfaceBlockAt(0, y, slope);
        return rule != null ? rule : cfg.surfaceBlock();
    }

    /**
     * Slopes from the in-chunk neighbours (clamped at the chunk border). Preview/scan only:
     * the generator computes border slopes from the 18 x 18 grid.
     */
    @NotNull
    private static float[] inChunkSlopes(@NotNull int... heights) {
        float[] slopes = new float[CHUNK_COLUMNS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int left = heights[z * 16 + Math.max(0, x - 1)];
                int right = heights[z * 16 + Math.min(15, x + 1)];
                int down = heights[Math.max(0, z - 1) * 16 + x];
                int up = heights[Math.min(15, z + 1) * 16 + x];
                slopes[z * 16 + x] = (float) Math.max(
                        Math.abs((right - left) * 0.5), Math.abs((up - down) * 0.5));
            }
        }
        return slopes;
    }

    private static int columnIndex(int x, int z) {
        return Math.floorMod(z, 16) * 16 + Math.floorMod(x, 16);
    }

    private DimensionConfig requireConfig(@NotNull String dimensionId) {
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null) {
            throw new IllegalArgumentException(UNKNOWN_DIMENSION_PREFIX + dimensionId + "'");
        }
        return cfg;
    }

    /**
     * Builds a native sampler for the dimension's terrain spec.
     *
     * <p>The sampler snapshots the current noise seed, so a later {@code Noise:SetSeed} takes
     * effect on the next chunk (a new sampler is built for every chunk cache miss).</p>
     *
     * @return null when the dimension has no terrain spec
     * @throws IllegalStateException when the noise module is not registered
     */
    @Nullable
    public TerrainSampler createTerrainSampler(@NotNull String dimensionId) {
        TerrainSpec spec = terrainSpecs.get(dimensionId);
        if (spec == null) {
            return null;
        }
        return createSampler(dimensionId, spec, "terrain spec");
    }

    /**
     * Builds a native sampler for the dimension's cave spec.
     *
     * @return null when the dimension has no cave spec
     * @throws IllegalStateException when the config or the noise module is missing
     */
    @Nullable
    public TerrainSampler createCaveSampler(@NotNull String dimensionId) {
        TerrainSpec spec = caveSpecs.get(dimensionId);
        if (spec == null) {
            return null;
        }
        return createSampler(dimensionId, spec, "cave spec");
    }

    @NotNull
    private TerrainSampler createSampler(@NotNull String dimensionId, @NotNull TerrainSpec spec,
                                         @NotNull String what) {
        DimensionConfig config = configs.get(dimensionId);
        if (config == null) {
            throw new IllegalStateException(DIMENSION_PREFIX + dimensionId + "' has a " + what + " but no config");
        }
        Object noiseService = com.luatweaker.core.service.LuaServiceRegistry.get("NoiseServiceImpl");
        if (!(noiseService instanceof com.luatweaker.noise.NoiseServiceImpl noise)) {
            throw new IllegalStateException("The " + what + " for '" + dimensionId
                    + "' needs the Noise module; NoiseServiceImpl is not registered");
        }
        return new TerrainSampler(spec, noise.createSampler(), config);
    }

    /**
     * Runs the batch terrain callback once for a chunk.
     *
     * <p>Contract: {@code function(chunkX, chunkZ, ctx) -> heights, surfaces, overrides}.
     * Missing or non-numeric heights become void columns (documented, logged once per
     * chunk); a missing heights table aborts chunk generation loudly instead of silently
     * producing a flat world.</p>
     */
    @NotNull
    public ChunkTerrainResult computeChunkTerrain(@NotNull String dimensionId, int chunkX, int chunkZ) {
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null) {
            throw new IllegalArgumentException(UNKNOWN_DIMENSION_PREFIX + dimensionId + "' during terrain generation");
        }
        ILuaValue generator = chunkTerrains.get(dimensionId);
        if (generator == null) {
            throw new IllegalStateException(DIMENSION_PREFIX + dimensionId + "' has no chunk terrain callback");
        }
        ILuaValue[] results = engine.callFunctionMulti(generator,
                engine.wrapNumber(chunkX), engine.wrapNumber(chunkZ),
                createChunkContext(cfg, chunkX, chunkZ));

        int minY = cfg.minHeight();
        int maxY = cfg.maxHeight();
        int[] heights = ChunkCallbackParser.parseHeights(dimensionId, chunkX, chunkZ, results, minY);
        String[] surfaces = ChunkCallbackParser.parseSurfaces(dimensionId, chunkX, chunkZ, results, cfg.surfaceBlock());
        Map<Integer, Map<Integer, String>> overrides =
                ChunkOverridesParser.parseOverrides(dimensionId, chunkX, chunkZ, results, minY, maxY, MAX_CHUNK_OVERRIDES);
        return new ChunkTerrainResult(heights, surfaces, overrides);
    }

    /** Package-private for tests: the override cap is injected instead of reading the constant. */
    Map<Integer, Map<Integer, String>> parseChunkOverrides(String dimensionId, int chunkX, int chunkZ,
                                                           ILuaValue[] results, int minY, int maxY,
                                                           int maxOverrides) {
        return ChunkOverridesParser.parseOverrides(dimensionId, chunkX, chunkZ, results,
                minY, maxY, maxOverrides);
    }

    /**
     * Runs the batch biome provider once for a chunk.
     *
     * <p>Contract: {@code function(chunkX, chunkZ, ctx) -> table} with up to 16 biome ids
     * for the 4 x 4 quart grid ({@code index = qz * 4 + qx + 1}); nil entries fall back to
     * the weighted pick. Returns an empty array when no batch provider is registered.</p>
     */
    @NotNull
    public String[] computeBiomeBatch(@NotNull String dimensionId, int chunkX, int chunkZ) {
        ILuaValue provider = biomeBatches.get(dimensionId);
        if (provider == null) {
            return EMPTY_BIOME_BATCH;
        }
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null) {
            throw new IllegalArgumentException(UNKNOWN_DIMENSION_PREFIX + dimensionId + "' during biome lookup");
        }
        ILuaValue result = engine.callFunction(provider,
                engine.wrapNumber(chunkX), engine.wrapNumber(chunkZ),
                createChunkContext(cfg, chunkX, chunkZ));
        if (result == null || result.isNil()) {
            return EMPTY_BIOME_BATCH;
        }
        if (!result.isTable()) {
            throw new IllegalStateException("Batch biome provider for '" + dimensionId
                    + "' must return a table of up to " + BIOME_QUART_CELLS + " biome ids (chunk "
                    + chunkX + ", " + chunkZ + ")");
        }
        String[] ids = new String[BIOME_QUART_CELLS];
        ILuaTable table = result.asTable();
        int invalid = fillBatchIds(table, ids);
        if (invalid > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Batch biome provider for '" + dimensionId + CHUNK_AT + chunkX + ", " + chunkZ
                            + ") returned " + invalid + " invalid biome id(s); falling back to the weighted pick");
        }
        return ids;
    }

    private int fillBatchIds(@NotNull ILuaTable table, @NotNull String... ids) {
        int invalid = 0;
        for (int i = 1; i <= BIOME_QUART_CELLS; i++) {
            ILuaValue value = table.rawget(i);
            String id = parseBiomeBatchEntry(value);
            if (id == null) {
                if (value != null && !value.isNil()) {
                    invalid++;
                }
                continue;
            }
            ids[i - 1] = id;
        }
        return invalid;
    }

    @Nullable
    private String parseBiomeBatchEntry(@Nullable ILuaValue value) {
        if (value == null || value.isNil()) {
            return null;
        }
        if (!value.isString() || !RESOURCE_LOCATION.matcher(value.asString()).matches()) {
            return null;
        }
        return value.asString();
    }

    private ILuaTable createChunkContext(DimensionConfig cfg, int chunkX, int chunkZ) {
        ILuaTable ctx = engine.createTable();
        ctx.rawset("chunkX", (double) chunkX);
        ctx.rawset("chunkZ", (double) chunkZ);
        ctx.rawset("minX", (double) (chunkX * 16));
        ctx.rawset("minZ", (double) (chunkZ * 16));
        ctx.rawset("minY", (double) cfg.minHeight());
        ctx.rawset("maxY", (double) cfg.maxHeight());
        ctx.rawset("seaLevel", (double) cfg.seaLevel());
        ctx.rawset("biomeSize", (double) cfg.biomeSize());
        return ctx;
    }

    // -------------------------------------------------------------------------
    // Config parsing
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Config parsing (delegated to DimensionConfigParser)
    // -------------------------------------------------------------------------

    // Info table for getDimension
    // -------------------------------------------------------------------------

    private ILuaTable buildInfoTable(DimensionConfig cfg) {
        ILuaTable table = engine.createTable();
        putDimensionFlags(table, cfg);
        putWorldShape(table, cfg);
        putSpawnPoint(table, cfg);
        putCapabilities(table, cfg);
        putEcologyTables(table, cfg);
        return table;
    }

    private void putDimensionFlags(@NotNull ILuaTable table, @NotNull DimensionConfig cfg) {
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
    }

    private void putWorldShape(@NotNull ILuaTable table, @NotNull DimensionConfig cfg) {
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
    }

    private void putSpawnPoint(@NotNull ILuaTable table, @NotNull DimensionConfig cfg) {
        if (cfg.spawnX() != null && cfg.spawnZ() != null) {
            table.rawset("spawnX", cfg.spawnX());
            table.rawset("spawnZ", cfg.spawnZ());
        }
        SpawnPoint spawnOverride = spawnPoints.get(cfg.id());
        if (spawnOverride != null) {
            table.rawset("spawnX", spawnOverride.x());
            table.rawset("spawnZ", spawnOverride.z());
        }
    }

    private void putCapabilities(@NotNull ILuaTable table, @NotNull DimensionConfig cfg) {
        table.rawset("hasTerrainGenerator", terrainGenerators.containsKey(cfg.id()));
        table.rawset("hasBiomeProvider", biomeProviders.containsKey(cfg.id()));
        table.rawset("hasBlockPicker", blockPickers.containsKey(cfg.id()));
        table.rawset("terrainMode", getTerrainMode(cfg.id()).name().toLowerCase(Locale.ROOT));
        table.rawset("hasChunkTerrain", chunkTerrains.containsKey(cfg.id()));
        table.rawset("hasBiomeBatch", biomeBatches.containsKey(cfg.id()));
        table.rawset("hasTerrainSpec", terrainSpecs.containsKey(cfg.id()));
        table.rawset("hasCaveSpec", caveSpecs.containsKey(cfg.id()));
    }

    private void putEcologyTables(@NotNull ILuaTable table, @NotNull DimensionConfig cfg) {
        ILuaTable biomesTable = engine.createTable();
        for (int i = 0; i < cfg.biomes().size(); i++) {
            DimensionConfig.BiomeEntry b = cfg.biomes().get(i);
            ILuaTable entry = engine.createTable();
            entry.rawset("id", b.id());
            entry.rawset(WEIGHT_KEY, b.weight());
            biomesTable.rawset(i + 1, entry);
        }
        table.rawset("biomes", biomesTable);

        ILuaTable spawnTable = engine.createTable();
        for (int i = 0; i < cfg.spawnEntities().size(); i++) {
            DimensionConfig.SpawnEntry s = cfg.spawnEntities().get(i);
            ILuaTable entry = engine.createTable();
            entry.rawset("entity", s.entity());
            entry.rawset(WEIGHT_KEY, s.weight());
            entry.rawset("minGroup", s.minGroup());
            entry.rawset("maxGroup", s.maxGroup());
            spawnTable.rawset(i + 1, entry);
        }
        table.rawset("spawnEntities", spawnTable);

        ILuaTable portalsTable = engine.createTable();
        for (Map.Entry<String, String> p : portals.entrySet()) {
            if (p.getValue().equals(cfg.id())) {
                portalsTable.rawset(p.getKey(), p.getValue());
            }
        }
        table.rawset("portals", portalsTable);
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    private static ILuaValue asFunction(Object luaFunction, String what) {
        if (!(luaFunction instanceof ILuaValue value) || !value.isFunction()) {
            throw new IllegalArgumentException(what + " must be a Lua function, got: "
                    + (luaFunction != null ? luaFunction.getClass().getSimpleName() : "nil"));
        }
        return value;
    }

    private static void validateDimensionId(String id) {
        if (id == null || id.indexOf(':') <= 0 || !RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException("dimensionId must be a namespaced resource location "
                    + "(e.g. 'mymod:skylands'), got: " + id);
        }
    }
}
