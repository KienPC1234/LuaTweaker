package com.luatweaker.platform.dimension;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.dimension.DensityField;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.dimension.DimensionServiceImpl.TerrainMode;
import com.luatweaker.dimension.TerrainColumn;
import com.luatweaker.platform.spawn.SpawnRuleEvaluator;
import com.luatweaker.dimension.TerrainSampler;
import com.luatweaker.dimension.TerrainSpec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Chunk generator driven by Lua callbacks.
 *
 * <p>For every column the terrain generator callback
 * {@code function(x, z, baseHeight) -> height, blockId} produces the surface
 * height and top block; the column is filled with surface/subsurface/filler
 * blocks from the dimension config and water up to sea level. All custom
 * worldgen (caves, ores, trees, lakes, buildings, floating islands) is done
 * by the mod author in Lua through the block/strata pickers; this class only
 * executes those callbacks. Heightmaps are computed during the same pass
 * (mirroring vanilla's noise generator).</p>
 *
 * <p>The dimension service is resolved live from {@link com.luatweaker.core.service.LuaServiceRegistry}
 * so a {@code /lt reload} that re-registers callbacks is honored without
 * recreating this generator; a per-chunk column cache is invalidated when the
 * service's generation version changes.</p>
 */
public class LuaChunkGenerator extends ChunkGenerator {

    public static final MapCodec<LuaChunkGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(LuaChunkGenerator::getBiomeSource),
            Codec.STRING.fieldOf("dimension_id").forGetter(LuaChunkGenerator::getDimensionId)
    ).apply(instance, LuaChunkGenerator::new));

    // Engine-level tuning, not per-mod config: DimensionConfig carries only
    // terrain/visual/spawn data (no cache or seed fields), so the column-cache
    // bound and placement seeds live here as named constants.
    private static final int MAX_CACHED_CHUNKS = 1024;
    /** Vanilla per-chunk placement seeds (same constants vanilla features use). */
    private static final long CHUNK_X_SEED = 341_873_128_712L;
    private static final long CHUNK_Z_SEED = 132_897_987_541L;
    /** Caves never break the top blocks, so the column top (and heightmaps) stay solid. */
    private static final int CAVE_SURFACE_GUARD = 3;
    /** Caves never remove the blocks just above minHeight, so the world floor stays intact. */
    private static final int CAVE_FLOOR_GUARD = 3;

    private final String dimensionId;
    private final BlockState airState = Blocks.AIR.defaultBlockState();

    /** Per-chunk column cache: chunk key -> 16x16 heights + top block ids. */
    private final Map<Long, ChunkColumnData> columnCache = new ConcurrentHashMap<>();
    // Atomic counterparts of the former volatile fields: same read/write visibility
    // guarantees, without the AvoidUsingVolatile finding.
    private final AtomicLong cachedGenerationVersion = new AtomicLong(-1);
    private final AtomicReference<DimensionServiceImpl> cachedService = new AtomicReference<>();

    /** Resolved block states by id, so lookups happen once per block id. */
    private final Map<String, BlockState> resolvedBlocks = new ConcurrentHashMap<>();

    /** Declarative spawn rules resolved for this dimension; rebuilt when their version changes. */
    private final AtomicLong cachedSpawnRulesVersion = new AtomicLong(-1);
    private final AtomicReference<List<SpawnRuleEvaluator.Resolved>> cachedSpawnRules =
            new AtomicReference<>(List.of());

    /** This dimension's level key, cached: getMobsAt runs on every natural spawn attempt. */
    private final ResourceKey<Level> dimensionKey;

    public LuaChunkGenerator(BiomeSource biomeSource, @NotNull String dimensionId) {
        super(biomeSource);
        this.dimensionId = dimensionId;
        this.dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimensionId));
    }

    public String getDimensionId() {
        return dimensionId;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MAP_CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup,
                                                    RandomState randomState, long seed) {
        // Lua dimensions own their content. Vanilla structure sets (villages, mineshafts...)
        // are filtered by biome and would otherwise generate into terrain they do not fit;
        // mods place structures explicitly through the World/Structure APIs instead.
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, getBiomeSource(), Stream.empty());
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess chunk) {
        // No structure starts exist for Lua dimensions (see createState); skip the 17 x 17
        // neighbour scan vanilla performs for structure references.
    }

    // ---------------------------------------------------------------------
    // Worldgen entry points
    // ---------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        DimensionServiceImpl service = requireService();
        DimensionConfig config = requireConfig(service);
        ChunkPos pos = chunk.getPos();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight();
        int configMinY = config.minHeight();

        // One column pass for the whole chunk, shared with getBaseHeight/getBaseColumn.
        // Calling the Lua terrain callback again for those paths made a single chunk run
        // up to 768 Lua calls instead of 256.
        ChunkColumnData columns = columnDataForChunk(service, config, pos.x, pos.z);
        BlockState waterState = resolveBlock(config.waterBlock());
        // A batch chunk callback already owns every column, so the per-column pickers
        // (another 512 Lua calls per chunk) must not run on top of it.
        boolean usePickers = columns.mode == TerrainMode.LEGACY;
        int seaLevel = columns.sampler != null ? columns.sampler.baseHeight() : config.seaLevel();
        boolean fillWater = columns.sampler == null || columns.sampler.waterEnabled();

        ColumnFill fill = new ColumnFill(chunk, service, config, columns, pos,
                new BlockPos.MutableBlockPos(), waterState, usePickers, seaLevel, fillWater,
                minY, maxY, configMinY);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                fillColumn(fill, x, z);
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    /** Fills one column's solid band and its air/water band above the surface. */
    private void fillColumn(ColumnFill fill, int x, int z) {
        ChunkColumnData columns = fill.columns;
        int column = z * 16 + x;
        int worldX = fill.pos.getMinBlockX() + x;
        int worldZ = fill.pos.getMinBlockZ() + z;
        int height = columns.heights[column] + fill.configMinY;

        Map<Integer, String> overrides = fill.usePickers
                ? fill.service.computeBlockOverrides(dimensionId, worldX, worldZ, height, fill.minY, fill.maxY)
                : columns.overrides.getOrDefault(column, Map.of());
        Map<Integer, String> strataOverrides = fill.usePickers
                ? fill.service.computeStrataOverrides(dimensionId, worldX, worldZ, height)
                : Map.of();
        for (int y = fill.minY; y <= height; y++) {
            String blockId = solidBlockIdAt(columns, fill.config, x, y, z, height, fill.minY,
                    overrides, strataOverrides);
            if (blockId == null) {
                // Density/cave says air: the chunk starts as air, so skip the write.
                continue;
            }
            BlockState state = resolveBlock(blockId);
            // A fresh ProtoChunk starts as air, and ProtoChunk#setBlockState already
            // updates the worldgen heightmaps (WORLD_SURFACE_WG/OCEAN_FLOOR_WG).
            // Writing air here was pure overhead: ~600k heightmap updates per chunk.
            if (!state.isAir()) {
                fill.chunk.setBlockState(fill.mutPos.set(worldX, y, worldZ), state, false);
            }
        }
        for (int y = Math.max(height + 1, fill.minY); y < fill.maxY; y++) {
            BlockState state = openBlockState(overrides, y, fill.fillWater, fill.seaLevel, fill.waterState);
            if (!state.isAir()) {
                fill.chunk.setBlockState(fill.mutPos.set(worldX, y, worldZ), state, false);
            }
        }
    }

    /**
     * Resolves the solid block id at a position, or null when density/caves carve it to air.
     * Shared by the chunk write pass and {@link #getBaseColumn}.
     */
    private static @Nullable String solidBlockIdAt(ChunkColumnData columns, DimensionConfig config,
                                                   int localX, int y, int localZ, int height, int minY,
                                                   Map<Integer, String> overrides,
                                                   Map<Integer, String> strataOverrides) {
        if (overrides.containsKey(y)) {
            // Explicit overrides win even inside a density cave (ore veins, lava pockets).
            return overrides.get(y);
        }
        if (columns.density != null && !columns.density.isSolid(localX, y, localZ)) {
            return null;
        }
        if (isCaveCarved(columns.caves, localX, y, localZ, height, minY)) {
            return null;
        }
        int index = Math.floorMod(localZ, 16) * 16 + Math.floorMod(localX, 16);
        float slope = columns.slopes != null ? columns.slopes[index] : 0.0f;
        String ruleBlock = columns.sampler != null
                ? columns.sampler.surfaceBlockAt(height - y, y, slope)
                : null;
        if (ruleBlock != null) {
            return ruleBlock;
        }
        if (y == height) {
            return columns.topBlocks[index];
        }
        if (strataOverrides.containsKey(height - y)) {
            return strataOverrides.get(height - y);
        }
        return TerrainColumn.blockAtDepth(config, height, y);
    }

    private BlockState openBlockState(Map<Integer, String> overrides, int y, boolean fillWater,
                                      int seaLevel, BlockState waterState) {
        if (overrides.containsKey(y)) {
            return resolveBlock(overrides.get(y));
        }
        return fillWater && y <= seaLevel ? waterState : airState;
    }

    /** Shared immutable inputs of one {@link #fillFromNoise} pass. */
    private record ColumnFill(ChunkAccess chunk, DimensionServiceImpl service, DimensionConfig config,
                              ChunkColumnData columns, ChunkPos pos, BlockPos.MutableBlockPos mutPos,
                              BlockState waterState, boolean usePickers, int seaLevel, boolean fillWater,
                              int minY, int maxY, int configMinY) {}

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures,
                             RandomState randomState, ChunkAccess chunk) {
        // Surface blocks are placed in fillFromNoise so the column data stays
        // consistent between the NOISE and SURFACE chunk statuses. Structures
        // are not built by the engine: mods place their own builds through the
        // block picker callbacks.
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomes, StructureManager structures,
                             ChunkAccess chunk, GenerationStep.Carving step) {
        // No vanilla carvers; terrain shapes come entirely from the Lua generator.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        DimensionServiceImpl service = requireService();
        DimensionConfig config = requireConfig(service);
        if (config.spawnEntities().isEmpty()) { return; }

        ChunkPos center = region.getCenter();
        Random random = new Random(center.x * CHUNK_X_SEED ^ center.z * CHUNK_Z_SEED);
        for (DimensionConfig.SpawnEntry entry : config.spawnEntities()) {
            spawnOriginalEntry(region, config, entry, center, random);
        }
    }

    private void spawnOriginalEntry(WorldGenRegion region, DimensionConfig config,
                                    DimensionConfig.SpawnEntry entry, ChunkPos center, Random random) {
        java.util.Optional<EntityType<?>> type =
                com.luatweaker.platform.interaction.EntityInteractionHelper.resolveEntityType(entry.entity());
        if (type.isEmpty()) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "spawnEntities for dimension '" + dimensionId + "' references unknown entity '"
                            + entry.entity() + "'");
            return;
        }
        // Vanilla spawnOriginalMobs only seeds passive animals at worldgen; monsters and
        // water mobs are left to the natural spawner (getMobsAt) and its caps.
        if (type.get().getCategory() != MobCategory.CREATURE) {
            return;
        }
        int groupSize = entry.minGroup() + random.nextInt(entry.maxGroup() - entry.minGroup() + 1);
        for (int i = 0; i < groupSize; i++) {
            spawnOriginalMob(region, config, type.get(), center, random);
        }
    }

    private void spawnOriginalMob(WorldGenRegion region, DimensionConfig config, EntityType<?> type,
                                  ChunkPos center, Random random) {
        int x = center.getMinBlockX() + random.nextInt(16);
        int z = center.getMinBlockZ() + random.nextInt(16);
        int y = getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, region, null);
        // Floating-island dimensions report no ground for void columns; spawning at
        // minY there drops mobs out of the world and wastes entity ticks.
        if (y <= config.minHeight() + 1 || y >= config.maxHeight()) { return; }
        // OCEAN_FLOOR excludes water; skip columns where the first free block is still
        // submerged so land mobs do not spawn inside an ocean.
        if (!region.getFluidState(new BlockPos(x, y, z)).isEmpty()) { return; }
        Entity entity = type.create(region.getLevel());
        if (entity == null) { return; }
        entity.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
        region.addFreshEntity(entity);
    }

    /**
     * Natural spawning.
     *
     * <p>When the dimension has declarative {@code SpawnRules:AddRule} entries, the biome's
     * default spawn list is replaced by those rules and each rule is evaluated at the exact
     * candidate position (light, Y, biome, block below, time, weather). Otherwise the
     * dimension config's {@code spawnEntities} table keeps working as before.</p>
     */
    @Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(
            Holder<Biome> biome, StructureManager structures, MobCategory category, BlockPos pos) {
        List<SpawnRuleEvaluator.Resolved> rules = spawnRules();
        if (!rules.isEmpty()) {
            return spawnersFromRules(biome, category, pos, rules);
        }
        return spawnersFromConfig(category);
    }

    private WeightedRandomList<MobSpawnSettings.SpawnerData> spawnersFromRules(
            Holder<Biome> biome, MobCategory category, BlockPos pos,
            List<SpawnRuleEvaluator.Resolved> rules) {
        ServerLevel level = currentLevel();
        com.luatweaker.spawn.SpawnRuleServiceImpl spawnService = currentSpawnRules();
        if (level == null || spawnService == null) {
            return WeightedRandomList.create();
        }
        List<MobSpawnSettings.SpawnerData> fromRules = new ArrayList<>();
        SpawnRuleEvaluator.Attempt attempt = SpawnRuleEvaluator.attemptAt(level, pos);
        for (SpawnRuleEvaluator.Resolved rule : rules) {
            if (SpawnRuleEvaluator.matches(rule, category, level, biome, pos, dimensionId,
                    spawnService, attempt)) {
                fromRules.add(new MobSpawnSettings.SpawnerData(rule.type(),
                        rule.rule().weight(), rule.rule().minGroup(), rule.rule().maxGroup()));
            }
        }
        return WeightedRandomList.create(fromRules);
    }

    private WeightedRandomList<MobSpawnSettings.SpawnerData> spawnersFromConfig(MobCategory category) {
        DimensionServiceImpl service = currentService();
        DimensionConfig config = service != null ? service.getConfig(dimensionId) : null;
        if (config == null || config.spawnEntities().isEmpty()) {
            return WeightedRandomList.create();
        }
        List<MobSpawnSettings.SpawnerData> spawners = new ArrayList<>();
        for (DimensionConfig.SpawnEntry entry : config.spawnEntities()) {
            java.util.Optional<EntityType<?>> type =
                    com.luatweaker.platform.interaction.EntityInteractionHelper.resolveEntityType(entry.entity());
            if (type.isEmpty() || type.get().getCategory() != category) { continue; }
            spawners.add(new MobSpawnSettings.SpawnerData(type.get(), entry.weight(),
                    entry.minGroup(), entry.maxGroup()));
        }
        return WeightedRandomList.create(spawners);
    }

    /** Resolved declarative spawn rules, rebuilt only when the spawn service version changes. */
    @NotNull
    private List<SpawnRuleEvaluator.Resolved> spawnRules() {
        com.luatweaker.spawn.SpawnRuleServiceImpl spawnService = currentSpawnRules();
        if (spawnService == null) { return List.of(); }
        long version = spawnService.getRulesVersion();
        if (version != cachedSpawnRulesVersion.get()) {
            cachedSpawnRules.set(SpawnRuleEvaluator.resolve(dimensionId, spawnService.rulesFor(dimensionId)));
            cachedSpawnRulesVersion.set(version);
        }
        return cachedSpawnRules.get();
    }

    @Nullable
    private static com.luatweaker.spawn.SpawnRuleServiceImpl currentSpawnRules() {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("SpawnRuleServiceImpl");
        return service instanceof com.luatweaker.spawn.SpawnRuleServiceImpl spawn ? spawn : null;
    }

    /** The ServerLevel of this dimension, or null before the server exists. */
    @Nullable
    private ServerLevel currentLevel() {
        net.minecraft.server.MinecraftServer server =
                net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getLevel(dimensionKey) : null;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type,
                             LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
        DimensionServiceImpl service = requireService();
        DimensionConfig config = requireConfig(service);
        ChunkColumnData data = columnDataAtBlock(service, config, x, z);
        int topSolid = data.heights[columnIndex(x, z)] + config.minHeight();
        int seaLevel = data.sampler != null ? data.sampler.baseHeight() : config.seaLevel();
        boolean fillsWater = data.sampler == null || data.sampler.waterEnabled();
        // Vanilla contract: getBaseHeight returns the first *air* Y above the surface; the
        // heightmap itself stores topSolid + 1. Water counts for WORLD_SURFACE/MOTION_BLOCKING.
        boolean countsWater = type == Heightmap.Types.WORLD_SURFACE_WG
                || type == Heightmap.Types.WORLD_SURFACE
                || type == Heightmap.Types.MOTION_BLOCKING
                || type == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        int maxY = levelHeightAccessor.getMinBuildHeight() + levelHeightAccessor.getHeight();
        return firstFreeHeight(topSolid, config.minHeight(), maxY, seaLevel,
                fillsWater && countsWater);
    }

    /**
     * Converts a top solid Y into vanilla's first-free (air) height.
     *
     * <p>Void columns report {@code minY} when fluids are not counted (OCEAN_FLOOR), and the
     * water surface when they are (a void column in a water-filling dimension is an ocean).</p>
     */
    static int firstFreeHeight(int topSolid, int minY, int maxY, int seaLevel, boolean countsWater) {
        int surface = topSolid;
        if (countsWater && surface < seaLevel) {
            surface = seaLevel;
        }
        if (surface < minY) {
            return minY;
        }
        return Math.min(maxY, surface + 1);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor levelHeightAccessor,
                                     RandomState randomState) {
        DimensionServiceImpl service = requireService();
        DimensionConfig config = requireConfig(service);
        int minY = levelHeightAccessor.getMinBuildHeight();
        int maxY = minY + levelHeightAccessor.getHeight();

        ChunkColumnData data = columnDataAtBlock(service, config, x, z);
        int index = columnIndex(x, z);
        int height = data.heights[index] + config.minHeight();

        BlockState[] states = new BlockState[maxY - minY];
        BlockState waterState = resolveBlock(config.waterBlock());
        Map<Integer, String> overrides = data.mode == TerrainMode.LEGACY
                ? service.computeBlockOverrides(dimensionId, x, z, height, minY, maxY)
                : data.overrides.getOrDefault(index, Map.of());
        int seaLevel = data.sampler != null ? data.sampler.baseHeight() : config.seaLevel();
        boolean fillWater = data.sampler == null || data.sampler.waterEnabled();
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        for (int y = minY; y <= height; y++) {
            String blockId = solidBlockIdAt(data, config, localX, y, localZ, height, minY,
                    overrides, Map.of());
            states[y - minY] = blockId == null ? airState : resolveBlock(blockId);
        }
        for (int y = height + 1; y < maxY; y++) {
            states[y - minY] = openBlockState(overrides, y, fillWater, seaLevel, waterState);
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public int getGenDepth() {
        DimensionConfig config = requireConfig(requireService());
        // Vanilla returns the build height in blocks (overworld 384), not a section count.
        return config.maxHeight() - config.minHeight();
    }

    @Override
    public int getSeaLevel() {
        DimensionServiceImpl service = requireService();
        DimensionConfig config = requireConfig(service);
        if (service.getTerrainMode(dimensionId) == TerrainMode.SPEC) {
            TerrainSpec spec = service.getTerrainSpec(dimensionId);
            if (spec != null && spec.seaLevel() != null) {
                return spec.seaLevel();
            }
        }
        return config.seaLevel();
    }

    @Override
    public int getMinY() {
        return requireConfig(requireService()).minHeight();
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor levelHeightAccessor) {
        return getSeaLevel();
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Lua dimension: " + dimensionId);
    }

    // ---------------------------------------------------------------------
    // Column cache
    // ---------------------------------------------------------------------

    /** Returns cached (or freshly computed) column data for the chunk containing block (x, z). */
    ChunkColumnData columnDataAtBlock(DimensionServiceImpl service, DimensionConfig config, int x, int z) {
        return columnDataForChunk(service, config, Math.floorDiv(x, 16), Math.floorDiv(z, 16));
    }

    /**
     * Returns cached column data for an explicit chunk coordinate.
     *
     * <p>Chunk coordinates never divide by 16 again: passing block coordinates here would make
     * every group of 16 chunks reuse one chunk's terrain (the whole area looked flat).</p>
     */
    ChunkColumnData columnDataForChunk(DimensionServiceImpl service, DimensionConfig config,
                                       int chunkX, int chunkZ) {
        long version = service.getGenerationVersion();
        if (!java.util.Objects.equals(service, cachedService.get()) || version != cachedGenerationVersion.get()) {
            columnCache.clear();
            cachedService.set(service);
            cachedGenerationVersion.set(version);
        }
        long key = chunkKey(chunkX, chunkZ);
        ChunkColumnData data = columnCache.get(key);
        if (data == null) {
            data = computeChunk(service, config, chunkX, chunkZ, version);
            ChunkColumnData existing = columnCache.putIfAbsent(key, data);
            if (existing != null) { data = existing; }
            if (columnCache.size() > MAX_CACHED_CHUNKS) {
                columnCache.clear();
            }
        }
        return data;
    }

    /**
     * Computes the column data for a chunk and attaches the optional cave field: carving is a
     * separate pass so it works in every terrain mode (legacy, chunk and spec).
     */
    private ChunkColumnData computeChunk(DimensionServiceImpl service, DimensionConfig config,
                                         int chunkX, int chunkZ, long version) {
        ChunkColumnData base = computeBaseChunk(service, config, chunkX, chunkZ, version);
        TerrainSampler caveSampler = service.createCaveSampler(dimensionId);
        if (caveSampler == null) {
            return base;
        }
        DensityField caves = caveSampler.sampleDensity(chunkX, chunkZ,
                config.minHeight(), config.maxHeight());
        return new ChunkColumnData(base.heights(), base.topBlocks(), base.overrides(), base.slopes(),
                base.sampler(), base.density(), caves, base.mode(), base.version());
    }

    private ChunkColumnData computeBaseChunk(DimensionServiceImpl service, DimensionConfig config,
                                             int chunkX, int chunkZ, long version) {
        int minY = config.minHeight();
        int maxY = config.maxHeight();
        TerrainMode mode = service.getTerrainMode(dimensionId);
        if (mode == TerrainMode.SPEC) {
            ChunkColumnData spec = computeSpecChunk(service, config, chunkX, chunkZ, minY, maxY, version);
            if (spec != null) {
                return spec;
            }
        }
        if (mode == TerrainMode.CHUNK && service.hasChunkTerrain(dimensionId)) {
            DimensionServiceImpl.ChunkTerrainResult result =
                    service.computeChunkTerrain(dimensionId, chunkX, chunkZ);
            short[] heights = new short[DimensionServiceImpl.CHUNK_COLUMNS];
            for (int i = 0; i < heights.length; i++) {
                heights[i] = (short) (TerrainColumn.clampHeight(result.heights()[i], minY, maxY) - minY);
            }
            return new ChunkColumnData(heights, result.surfaces(), result.overrides(), null, null, null, null,
                    TerrainMode.CHUNK, version);
        }
        short[] heights = new short[DimensionServiceImpl.CHUNK_COLUMNS];
        String[] topBlocks = new String[DimensionServiceImpl.CHUNK_COLUMNS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                DimensionServiceImpl.SurfaceResult surface = service.computeSurface(dimensionId,
                        chunkX * 16 + x, chunkZ * 16 + z, config.seaLevel());
                heights[z * 16 + x] = (short) (TerrainColumn.clampHeight(surface.height(), minY, maxY) - minY);
                topBlocks[z * 16 + x] = surface.blockId();
            }
        }
        return new ChunkColumnData(heights, topBlocks, Map.of(), null, null, null, null,
                TerrainMode.LEGACY, version);
    }

    /** Native terrain path for {@link TerrainMode#SPEC}; null when no sampler is configured. */
    @Nullable
    private ChunkColumnData computeSpecChunk(DimensionServiceImpl service, DimensionConfig config,
                                             int chunkX, int chunkZ, int minY, int maxY, long version) {
        TerrainSampler sampler = service.createTerrainSampler(dimensionId);
        if (sampler == null) {
            return null;
        }
        if (sampler.hasDensity()) {
            return computeDensityChunk(sampler, config, chunkX, chunkZ, minY, maxY, version);
        }
        return computeHeightChunk(sampler, config, chunkX, chunkZ, minY, maxY, version);
    }

    /** Native height/spline path: one height sample per column plus an 18x18 slope grid. */
    private ChunkColumnData computeHeightChunk(TerrainSampler sampler, DimensionConfig config,
                                               int chunkX, int chunkZ, int minY, int maxY, long version) {
        int[] rawHeights = new int[DimensionServiceImpl.CHUNK_COLUMNS];
        float[] slopes = new float[DimensionServiceImpl.CHUNK_COLUMNS];
        sampler.sampleChunk(chunkX, chunkZ, rawHeights, slopes);
        short[] heights = new short[DimensionServiceImpl.CHUNK_COLUMNS];
        String[] topBlocks = new String[DimensionServiceImpl.CHUNK_COLUMNS];
        for (int i = 0; i < heights.length; i++) {
            int height = TerrainColumn.clampHeight(rawHeights[i], minY, maxY);
            heights[i] = (short) (height - minY);
            String ruleBlock = sampler.surfaceBlockAt(0, height, slopes[i]);
            topBlocks[i] = ruleBlock != null ? ruleBlock : config.surfaceBlock();
        }
        return new ChunkColumnData(heights, topBlocks, Map.of(), slopes, sampler, null, null,
                TerrainMode.SPEC, version);
    }

    /** Native 3D density path: top solid block per column, slopes from neighbouring columns. */
    private ChunkColumnData computeDensityChunk(TerrainSampler sampler, DimensionConfig config,
                                                int chunkX, int chunkZ, int minY, int maxY, long version) {
        DensityField density = sampler.sampleDensity(chunkX, chunkZ, minY, maxY);
        int[] topSolid = new int[DimensionServiceImpl.CHUNK_COLUMNS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                topSolid[z * 16 + x] = density.topSolid(x, z);
            }
        }
        short[] heights = new short[DimensionServiceImpl.CHUNK_COLUMNS];
        String[] topBlocks = new String[DimensionServiceImpl.CHUNK_COLUMNS];
        float[] slopes = new float[DimensionServiceImpl.CHUNK_COLUMNS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = z * 16 + x;
                // Slopes use the clamped in-chunk neighbours: sampling a density border would
                // cost another 18x18 column scan for a value only surface rules consume.
                int left = topSolid[z * 16 + Math.max(0, x - 1)];
                int right = topSolid[z * 16 + Math.min(15, x + 1)];
                int down = topSolid[Math.max(0, z - 1) * 16 + x];
                int up = topSolid[Math.min(15, z + 1) * 16 + x];
                slopes[i] = (float) Math.max(Math.abs((right - left) * 0.5), Math.abs((up - down) * 0.5));
                int height = TerrainColumn.clampHeight(topSolid[i], minY, maxY);
                heights[i] = (short) (height - minY);
                String ruleBlock = height < minY ? null : sampler.surfaceBlockAt(0, height, slopes[i]);
                topBlocks[i] = ruleBlock != null ? ruleBlock : config.surfaceBlock();
            }
        }
        return new ChunkColumnData(heights, topBlocks, Map.of(), slopes, sampler, density, null,
                TerrainMode.SPEC, version);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int columnIndex(int x, int z) {
        return (Math.floorMod(z, 16) * 16) + Math.floorMod(x, 16);
    }

    record ChunkColumnData(short[] heights, String[] topBlocks,
                           Map<Integer, Map<Integer, String>> overrides,
                           float[] slopes, TerrainSampler sampler, DensityField density,
                           DensityField caves, DimensionServiceImpl.TerrainMode mode, long version) {}

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * True when the cave field carves this block. The top and bottom guards keep the column
     * surface solid (heightmaps stay valid) and the world floor intact.
     */
    static boolean isCaveCarved(@Nullable DensityField caves, int localX, int y, int localZ,
                                int surfaceY, int minY) {
        if (caves == null) { return false; }
        if (y >= surfaceY - CAVE_SURFACE_GUARD || y < minY + CAVE_FLOOR_GUARD) { return false; }
        return caves.isSolid(localX, y, localZ);
    }

    private BlockState resolveBlock(String blockId) {
        return resolvedBlocks.computeIfAbsent(blockId, id -> {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "Block '" + id + "' (dimension '" + dimensionId + "') is not registered; using air");
                return airState;
            }
            return BuiltInRegistries.BLOCK.get(location).defaultBlockState();
        });
    }

    private DimensionServiceImpl requireService() {
        DimensionServiceImpl service = currentService();
        if (service == null) {
            throw new IllegalStateException("Dimension service is not registered; dimensions cannot be generated");
        }
        return service;
    }

    private DimensionServiceImpl currentService() {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        return service instanceof DimensionServiceImpl dim ? dim : null;
    }

    private DimensionConfig requireConfig(DimensionServiceImpl service) {
        DimensionConfig config = service.getConfig(dimensionId);
        if (config == null) {
            throw new IllegalStateException("Dimension '" + dimensionId
                    + "' is not registered; call Dimensions:Create(\"" + dimensionId + "\", {...}) first");
        }
        return config;
    }
}


