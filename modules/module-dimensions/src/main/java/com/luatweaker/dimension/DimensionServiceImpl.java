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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                    "Dimension '" + dimensionId + "' now uses a chunk terrain callback; "
                            + "SetTerrainGenerator/SetBlockPicker/SetStrataPicker are no longer called");
        }
    }

    @Override
    public void setBiomeBatch(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateDimensionId(dimensionId);
        ILuaValue fn = asFunction(luaFunction, "biome batch provider");
        if (biomeProviders.containsKey(dimensionId)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Dimension '" + dimensionId + "' now uses a batch biome provider; "
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
            throw new IllegalArgumentException("Unknown dimension '" + dimensionId
                    + "'; call Dimensions:Create(\"" + dimensionId + "\", {...}) first");
        }
        TerrainSpec parsed = TerrainSpecParser.parse(dimensionId, spec, config);
        if (terrainGenerators.containsKey(dimensionId) || chunkTerrains.containsKey(dimensionId)
                || blockPickers.containsKey(dimensionId) || strataPickers.containsKey(dimensionId)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Dimension '" + dimensionId + "' now uses a declarative terrain spec; "
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
    public void registerPortal(@NotNull String blockId, @NotNull String targetDimension) {
        validateId("blockId", blockId);
        validateDimensionId(targetDimension);
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
        validateId("blockId", blockId);
        String target = portals.get(blockId);
        if (target == null) {
            return false;
        }
        teleportTo(player, target);
        com.luatweaker.core.service.AppliedEventHelper.fire("PortalUsed", java.util.Map.of(
            "blockId", blockId,
            "targetDimension", target,
            "playerName", player.getName()));
        return true;
    }

    /**
     * Sets a block picker callback: {@code function(x, z, surfaceY, minY) -> {[y] = blockId, ...}}.
     * Returned overrides replace the default column fill at those Y positions
     * (use "minecraft:air" to carve caves).
     */
    public void setBlockPicker(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateDimensionId(dimensionId);
        ILuaValue fn = asFunction(luaFunction, "block picker");
        if (getTerrainMode(dimensionId) != TerrainMode.LEGACY) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Block picker for '" + dimensionId + "' is ignored: the dimension terrain is produced by "
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
        validateDimensionId(dimensionId);
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
        validateDimensionId(dimensionId);
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
        validateDimensionId(dimensionId);
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
        if (!Platform.isInitialized()) {
            throw new IllegalStateException("Platform is not initialized; cannot query entity dimension (unit-test environment?)");
        }
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
        if (spec == null) return null;
        DimensionConfig config = configs.get(dimensionId);
        if (config == null) {
            throw new IllegalStateException("Dimension '" + dimensionId + "' has a terrain spec but no config");
        }
        Object noiseService = com.luatweaker.core.service.LuaServiceRegistry.get("NoiseServiceImpl");
        if (!(noiseService instanceof com.luatweaker.noise.NoiseServiceImpl noise)) {
            throw new IllegalStateException("Terrain spec for '" + dimensionId
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
            throw new IllegalArgumentException("Unknown dimension '" + dimensionId + "' during terrain generation");
        }
        ILuaValue generator = chunkTerrains.get(dimensionId);
        if (generator == null) {
            throw new IllegalStateException("Dimension '" + dimensionId + "' has no chunk terrain callback");
        }
        ILuaValue[] results = engine.callFunctionMulti(generator,
                engine.wrapNumber(chunkX), engine.wrapNumber(chunkZ),
                createChunkContext(cfg, chunkX, chunkZ));

        int minY = cfg.minHeight();
        int maxY = cfg.maxHeight();
        int[] heights = parseChunkHeights(dimensionId, chunkX, chunkZ, results, minY);
        String[] surfaces = parseChunkSurfaces(dimensionId, chunkX, chunkZ, results, cfg.surfaceBlock());
        Map<Integer, Map<Integer, String>> overrides =
                parseChunkOverrides(dimensionId, chunkX, chunkZ, results, minY, maxY);
        return new ChunkTerrainResult(heights, surfaces, overrides);
    }

    private int[] parseChunkHeights(String dimensionId, int chunkX, int chunkZ,
                                    ILuaValue[] results, int minY) {
        ILuaValue heightsValue = results.length > 0 ? results[0] : null;
        if (heightsValue == null || heightsValue.isNil() || !heightsValue.isTable()) {
            throw new IllegalStateException("Chunk terrain callback for '" + dimensionId
                    + "' must return a heights table of " + CHUNK_COLUMNS + " numbers for chunk ("
                    + chunkX + ", " + chunkZ + ")");
        }
        int[] heights = new int[CHUNK_COLUMNS];
        Arrays.fill(heights, minY - 1);
        ILuaTable table = heightsValue.asTable();
        int missing = 0;
        int invalid = 0;
        for (int i = 1; i <= CHUNK_COLUMNS; i++) {
            ILuaValue value = table.rawget(i);
            if (value == null || value.isNil()) {
                missing++;
                continue;
            }
            if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                invalid++;
                continue;
            }
            heights[i - 1] = (int) Math.floor(value.asDouble());
        }
        if (missing > 0 || invalid > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Chunk terrain callback for '" + dimensionId + "' chunk (" + chunkX + ", " + chunkZ
                            + "): " + (missing + invalid) + " column(s) generate as void ("
                            + missing + " missing, " + invalid + " not a finite number)");
        }
        return heights;
    }

    private String[] parseChunkSurfaces(String dimensionId, int chunkX, int chunkZ,
                                        ILuaValue[] results, String defaultBlock) {
        String[] surfaces = new String[CHUNK_COLUMNS];
        Arrays.fill(surfaces, defaultBlock);
        ILuaValue surfacesValue = results.length > 1 ? results[1] : null;
        if (surfacesValue == null || surfacesValue.isNil()) return surfaces;
        if (!surfacesValue.isTable()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Chunk terrain callback for '" + dimensionId + "' chunk (" + chunkX + ", " + chunkZ
                            + ") returned a non-table surfaces value; using the config surface block");
            return surfaces;
        }
        ILuaTable table = surfacesValue.asTable();
        int invalid = 0;
        for (int i = 1; i <= CHUNK_COLUMNS; i++) {
            ILuaValue value = table.rawget(i);
            if (value == null || value.isNil()) continue;
            if (!value.isString() || !RESOURCE_LOCATION.matcher(value.asString()).matches()) {
                invalid++;
                continue;
            }
            surfaces[i - 1] = value.asString();
        }
        if (invalid > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Chunk terrain callback for '" + dimensionId + "' chunk (" + chunkX + ", " + chunkZ
                            + ") returned " + invalid + " invalid surface block id(s); using the config default");
        }
        return surfaces;
    }

    private Map<Integer, Map<Integer, String>> parseChunkOverrides(String dimensionId, int chunkX, int chunkZ,
                                                                   ILuaValue[] results, int minY, int maxY) {
        return parseChunkOverrides(dimensionId, chunkX, chunkZ, results, minY, maxY, MAX_CHUNK_OVERRIDES);
    }

    /** Package-private for tests: the override cap is injected instead of reading the constant. */
    Map<Integer, Map<Integer, String>> parseChunkOverrides(String dimensionId, int chunkX, int chunkZ,
                                                           ILuaValue[] results, int minY, int maxY,
                                                           int maxOverrides) {
        ILuaValue overridesValue = results.length > 2 ? results[2] : null;
        if (overridesValue == null || overridesValue.isNil()) return Map.of();
        if (!overridesValue.isTable()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Chunk terrain callback for '" + dimensionId + "' chunk (" + chunkX + ", " + chunkZ
                            + ") returned a non-table overrides value; ignoring it");
            return Map.of();
        }
        Map<Integer, Map<Integer, String>> overrides = new LinkedHashMap<>();
        int rejected = 0;
        int total = 0;
        for (Map.Entry<ILuaValue, ILuaValue> entry : overridesValue.asTable().asMap().entrySet()) {
            ILuaValue key = entry.getKey();
            ILuaValue value = entry.getValue();
            if (key == null || !key.isNumber() || value == null || !value.isTable()) {
                rejected++;
                continue;
            }
            int column = key.asInt();
            if (column < 1 || column > CHUNK_COLUMNS) {
                rejected++;
                continue;
            }
            Map<Integer, String> columnOverrides = new LinkedHashMap<>();
            for (Map.Entry<ILuaValue, ILuaValue> blockEntry : value.asTable().asMap().entrySet()) {
                ILuaValue yKey = blockEntry.getKey();
                ILuaValue blockValue = blockEntry.getValue();
                if (yKey == null || !yKey.isNumber() || blockValue == null
                        || !blockValue.isString()
                        || !RESOURCE_LOCATION.matcher(blockValue.asString()).matches()) {
                    rejected++;
                    continue;
                }
                int y = yKey.asInt();
                if (y < minY || y >= maxY) {
                    rejected++;
                    continue;
                }
                if (total >= maxOverrides) {
                    throw new IllegalStateException("Chunk terrain callback for '" + dimensionId + "' chunk ("
                            + chunkX + ", " + chunkZ + ") returned more than " + maxOverrides
                            + " block overrides; reduce the overrides or use a block picker");
                }
                columnOverrides.put(y, blockValue.asString());
                total++;
            }
            if (!columnOverrides.isEmpty()) {
                overrides.put(column - 1, Map.copyOf(columnOverrides));
            }
        }
        if (rejected > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Chunk terrain callback for '" + dimensionId + "' chunk (" + chunkX + ", " + chunkZ
                            + ") returned " + rejected + " invalid override entr(y/ies); they were ignored");
        }
        return Map.copyOf(overrides);
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
        if (provider == null) return EMPTY_BIOME_BATCH;
        DimensionConfig cfg = configs.get(dimensionId);
        if (cfg == null) {
            throw new IllegalArgumentException("Unknown dimension '" + dimensionId + "' during biome lookup");
        }
        ILuaValue result = engine.callFunction(provider,
                engine.wrapNumber(chunkX), engine.wrapNumber(chunkZ),
                createChunkContext(cfg, chunkX, chunkZ));
        if (result == null || result.isNil()) return EMPTY_BIOME_BATCH;
        if (!result.isTable()) {
            throw new IllegalStateException("Batch biome provider for '" + dimensionId
                    + "' must return a table of up to " + BIOME_QUART_CELLS + " biome ids (chunk "
                    + chunkX + ", " + chunkZ + ")");
        }
        String[] ids = new String[BIOME_QUART_CELLS];
        ILuaTable table = result.asTable();
        int invalid = 0;
        for (int i = 1; i <= BIOME_QUART_CELLS; i++) {
            ILuaValue value = table.rawget(i);
            if (value == null || value.isNil()) continue;
            if (!value.isString() || !RESOURCE_LOCATION.matcher(value.asString()).matches()) {
                invalid++;
                continue;
            }
            ids[i - 1] = value.asString();
        }
        if (invalid > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Batch biome provider for '" + dimensionId + "' chunk (" + chunkX + ", " + chunkZ
                            + ") returned " + invalid + " invalid biome id(s); falling back to the weighted pick");
        }
        return ids;
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

    private DimensionConfig parseConfig(String dimensionId, ILuaTable t) {
        boolean hasSkyLight = bool(t, "hasSkyLight", true);
        boolean hasCeiling = bool(t, "hasCeiling", false);
        boolean ultraWarm = bool(t, "ultraWarm", false);
        boolean natural = bool(t, "natural", true);
        double coordinateScale = number(t, "coordinateScale", 1.0);
        if (!Double.isFinite(coordinateScale)
                || coordinateScale < DimensionConfig.MIN_COORDINATE_SCALE
                || coordinateScale > DimensionConfig.MAX_COORDINATE_SCALE) {
            throw new IllegalArgumentException("coordinateScale must be between "
                    + DimensionConfig.MIN_COORDINATE_SCALE + " and " + DimensionConfig.MAX_COORDINATE_SCALE
                    + " (vanilla DimensionType range), got: " + coordinateScale);
        }
        boolean bedWorks = bool(t, "bedWorks", true);
        boolean respawnAnchorWorks = bool(t, "respawnAnchorWorks", false);
        boolean piglinSafe = bool(t, "piglinSafe", false);
        boolean hasRaids = bool(t, "hasRaids", false);
        int monsterSpawnLightLevel = intRange(t, "monsterSpawnLightLevel", 7, 0, 15);
        int monsterSpawnBlockLightLimit = intRange(t, "monsterSpawnBlockLightLimit", 0, 0, 15);
        String infiniburn = string(t, "infiniburn", DimensionConfig.DEFAULT_INFINIBURN);
        if (!infiniburn.startsWith("#") || infiniburn.length() < 2
                || !RESOURCE_LOCATION.matcher(infiniburn.substring(1)).matches()) {
            throw new IllegalArgumentException("infiniburn must be a block tag (e.g. '#minecraft:infiniburn_overworld'), got: "
                    + infiniburn);
        }
        String effectsLocation = string(t, "effectsLocation", DimensionConfig.DEFAULT_EFFECTS);
        if (!effectsLocation.contains(":") || !RESOURCE_LOCATION.matcher(effectsLocation).matches()) {
            throw new IllegalArgumentException("effectsLocation must be a namespaced resource location "
                    + "(e.g. 'minecraft:the_nether'), got: " + effectsLocation);
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
        int minHeight = intRange(t, "minHeight", DimensionConfig.DEFAULT_MIN_HEIGHT,
                DimensionConfig.MIN_WORLD_Y, DimensionConfig.MAX_WORLD_TOP - DimensionConfig.MIN_WORLD_HEIGHT);
        int maxHeight = intRange(t, "maxHeight", DimensionConfig.DEFAULT_MAX_HEIGHT,
                DimensionConfig.MIN_WORLD_Y + DimensionConfig.MIN_WORLD_HEIGHT, DimensionConfig.MAX_WORLD_TOP);
        if (minHeight >= maxHeight) {
            throw new IllegalArgumentException("minHeight (" + minHeight + ") must be < maxHeight (" + maxHeight + ")");
        }
        int height = maxHeight - minHeight;
        // Vanilla DimensionType constructor rejects these; validating here turns a broken
        // datapack into a clear Lua error instead of a world-load crash.
        if (minHeight % DimensionConfig.WORLD_SECTION != 0) {
            throw new IllegalArgumentException("minHeight must be a multiple of 16 (vanilla DimensionType), got: "
                    + minHeight);
        }
        if (maxHeight % DimensionConfig.WORLD_SECTION != 0) {
            throw new IllegalArgumentException("maxHeight must be a multiple of 16 (vanilla DimensionType), got: "
                    + maxHeight);
        }
        if (height < DimensionConfig.MIN_WORLD_HEIGHT) {
            throw new IllegalArgumentException("build height must be at least 16, got: " + height);
        }
        if (minHeight + height > DimensionConfig.MAX_WORLD_TOP) {
            throw new IllegalArgumentException("minHeight + height must not exceed "
                    + DimensionConfig.MAX_WORLD_TOP + " (vanilla DimensionType), got: " + (minHeight + height));
        }
        if (seaLevel <= minHeight || seaLevel >= maxHeight) {
            throw new IllegalArgumentException("seaLevel (" + seaLevel + ") must be between minHeight (" + minHeight
                    + ") and maxHeight (" + maxHeight + ")");
        }
        int logicalHeight = intRange(t, "logicalHeight", height, 0, DimensionConfig.MAX_WORLD_HEIGHT);
        if (logicalHeight > height) {
            throw new IllegalArgumentException("logicalHeight (" + logicalHeight
                    + ") must be <= height (" + height + "), as required by DimensionType");
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
        table.rawset("terrainMode", getTerrainMode(cfg.id()).name().toLowerCase(java.util.Locale.ROOT));
        table.rawset("hasChunkTerrain", chunkTerrains.containsKey(cfg.id()));
        table.rawset("hasBiomeBatch", biomeBatches.containsKey(cfg.id()));
        table.rawset("hasTerrainSpec", terrainSpecs.containsKey(cfg.id()));

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
        return LuaTableValues.bool(t, key, def);
    }

    private static double number(ILuaTable t, String key, double def) {
        return LuaTableValues.number(t, key, def);
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
        return LuaTableValues.string(t, key, def);
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
        return LuaTableValues.optionalLong(t, key);
    }

    private static Integer optionalInt(ILuaTable t, String key) {
        return LuaTableValues.optionalInt(t, key);
    }

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

    private static void validateId(String name, String id) {
        if (id == null || !RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " must be a valid resource location (e.g. 'mymod:id'), got: " + id);
        }
    }
}
