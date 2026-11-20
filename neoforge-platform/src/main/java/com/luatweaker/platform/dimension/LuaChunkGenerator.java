package com.luatweaker.platform.dimension;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.dimension.DensityField;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.dimension.DimensionServiceImpl.TerrainMode;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final long CHUNK_X_SEED = 341873128712L;
    private static final long CHUNK_Z_SEED = 132897987541L;

    private final String dimensionId;
    private final BlockState airState = Blocks.AIR.defaultBlockState();

    /** Per-chunk column cache: chunk key -> 16x16 heights + top block ids. */
    private final Map<Long, ChunkColumnData> columnCache = new ConcurrentHashMap<>();
    private volatile long cachedGenerationVersion = -1;
    private volatile DimensionServiceImpl cachedService;

    /** Resolved block states by id, so lookups happen once per block id. */
    private final Map<String, BlockState> resolvedBlocks = new ConcurrentHashMap<>();

    public LuaChunkGenerator(BiomeSource biomeSource, @NotNull String dimensionId) {
        super(biomeSource);
        this.dimensionId = dimensionId;
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

        BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int column = z * 16 + x;
                int worldX = pos.getMinBlockX() + x;
                int worldZ = pos.getMinBlockZ() + z;
                int height = columns.heights[column] + configMinY;

                Map<Integer, String> overrides = usePickers
                        ? service.computeBlockOverrides(dimensionId, worldX, worldZ, height, minY, maxY)
                        : columns.overrides.getOrDefault(column, Map.of());
                Map<Integer, String> strataOverrides = usePickers
                        ? service.computeStrataOverrides(dimensionId, worldX, worldZ, height)
                        : Map.of();
                String topBlock = columns.topBlocks[column];
                float slope = columns.slopes != null ? columns.slopes[column] : 0.0f;
                for (int y = minY; y <= height; y++) {
                    String blockId;
                    if (overrides.containsKey(y)) {
                        // Explicit overrides win even inside a density cave (ore veins, lava pockets).
                        blockId = overrides.get(y);
                    } else {
                        if (columns.density != null && !columns.density.isSolid(x, y, z)) {
                            // Density says air: the chunk starts as air, so skip the write.
                            continue;
                        }
                        String ruleBlock = columns.sampler != null
                                ? columns.sampler.surfaceBlockAt(height - y, y, slope)
                                : null;
                        if (ruleBlock != null) {
                            blockId = ruleBlock;
                        } else if (y == height) {
                            blockId = topBlock;
                        } else if (strataOverrides.containsKey(height - y)) {
                            blockId = strataOverrides.get(height - y);
                        } else {
                            blockId = com.luatweaker.dimension.TerrainColumn.blockAtDepth(config, height, y);
                        }
                    }
                    BlockState state = resolveBlock(blockId);
                    // A fresh ProtoChunk starts as air, and ProtoChunk#setBlockState already
                    // updates the worldgen heightmaps (WORLD_SURFACE_WG/OCEAN_FLOOR_WG).
                    // Writing air here was pure overhead: ~600k heightmap updates per chunk.
                    if (!state.isAir()) {
                        chunk.setBlockState(mutPos.set(worldX, y, worldZ), state, false);
                    }
                }
                for (int y = Math.max(height + 1, minY); y < maxY; y++) {
                    BlockState state;
                    if (overrides.containsKey(y)) {
                        state = resolveBlock(overrides.get(y));
                    } else {
                        state = (fillWater && y <= seaLevel) ? waterState : airState;
                    }
                    if (!state.isAir()) {
                        chunk.setBlockState(mutPos.set(worldX, y, worldZ), state, false);
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

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
        if (config.spawnEntities().isEmpty()) return;

        ChunkPos center = region.getCenter();
        Random random = new Random(center.x * CHUNK_X_SEED ^ center.z * CHUNK_Z_SEED);
        for (DimensionConfig.SpawnEntry entry : config.spawnEntities()) {
            java.util.Optional<EntityType<?>> type =
                    com.luatweaker.platform.interaction.EntityInteractionHelper.resolveEntityType(entry.entity());
            if (type.isEmpty()) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "spawnEntities for dimension '" + dimensionId + "' references unknown entity '"
                                + entry.entity() + "'");
                continue;
            }
            int groupSize = entry.minGroup() + random.nextInt(entry.maxGroup() - entry.minGroup() + 1);
            for (int i = 0; i < groupSize; i++) {
                int x = center.getMinBlockX() + random.nextInt(16);
                int z = center.getMinBlockZ() + random.nextInt(16);
                int y = getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, region, null);
                // Floating-island dimensions report no ground for void columns; spawning at
                // minY there drops mobs out of the world and wastes entity ticks.
                if (y <= config.minHeight() + 1 || y >= config.maxHeight()) continue;
                // OCEAN_FLOOR excludes water; skip columns where the first free block is still
                // submerged so land mobs do not spawn inside an ocean.
                if (!region.getFluidState(new BlockPos(x, y, z)).isEmpty()) continue;
                Entity entity = type.get().create(region.getLevel());
                if (entity == null) continue;
                entity.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
                region.addFreshEntity(entity);
            }
        }
    }

    /**
     * Natural mob spawning: the configured spawnEntities become the spawn
     * list for every category they belong to, so the dimension is alive
     * everywhere (not just during worldgen).
     */
    @Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(
            Holder<Biome> biome, StructureManager structures, MobCategory category, BlockPos pos) {
        DimensionServiceImpl service = currentService();
        DimensionConfig config = service != null ? service.getConfig(dimensionId) : null;
        if (config == null || config.spawnEntities().isEmpty()) {
            return WeightedRandomList.create();
        }
        List<MobSpawnSettings.SpawnerData> spawners = new ArrayList<>();
        for (DimensionConfig.SpawnEntry entry : config.spawnEntities()) {
            java.util.Optional<EntityType<?>> type =
                    com.luatweaker.platform.interaction.EntityInteractionHelper.resolveEntityType(entry.entity());
            if (type.isEmpty() || type.get().getCategory() != category) continue;
            spawners.add(new MobSpawnSettings.SpawnerData(type.get(), entry.weight(),
                    entry.minGroup(), entry.maxGroup()));
        }
        return WeightedRandomList.create(spawners);
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
        String topBlock = data.topBlocks[index];

        BlockState[] states = new BlockState[maxY - minY];
        BlockState waterState = resolveBlock(config.waterBlock());
        Map<Integer, String> overrides = data.mode == TerrainMode.LEGACY
                ? service.computeBlockOverrides(dimensionId, x, z, height, minY, maxY)
                : data.overrides.getOrDefault(index, Map.of());
        float slope = data.slopes != null ? data.slopes[index] : 0.0f;
        int seaLevel = data.sampler != null ? data.sampler.baseHeight() : config.seaLevel();
        boolean fillWater = data.sampler == null || data.sampler.waterEnabled();
        int localX = Math.floorMod(x, 16);
        int localZ = Math.floorMod(z, 16);
        for (int y = minY; y <= height; y++) {
            String blockId;
            if (overrides.containsKey(y)) {
                blockId = overrides.get(y);
            } else {
                if (data.density != null && !data.density.isSolid(localX, y, localZ)) {
                    states[y - minY] = airState;
                    continue;
                }
                String ruleBlock = data.sampler != null
                        ? data.sampler.surfaceBlockAt(height - y, y, slope)
                        : null;
                if (ruleBlock != null) {
                    blockId = ruleBlock;
                } else if (y == height) {
                    blockId = topBlock;
                } else {
                    blockId = com.luatweaker.dimension.TerrainColumn.blockAtDepth(config, height, y);
                }
            }
            states[y - minY] = resolveBlock(blockId);
        }
        for (int y = height + 1; y < maxY; y++) {
            states[y - minY] = overrides.containsKey(y)
                    ? resolveBlock(overrides.get(y))
                    : ((fillWater && y <= seaLevel) ? waterState : airState);
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
        if (service != cachedService || version != cachedGenerationVersion) {
            columnCache.clear();
            cachedService = service;
            cachedGenerationVersion = version;
        }
        long key = chunkKey(chunkX, chunkZ);
        ChunkColumnData data = columnCache.get(key);
        if (data == null) {
            data = computeChunk(service, config, chunkX, chunkZ, version);
            ChunkColumnData existing = columnCache.putIfAbsent(key, data);
            if (existing != null) data = existing;
            if (columnCache.size() > MAX_CACHED_CHUNKS) {
                columnCache.clear();
            }
        }
        return data;
    }

    private ChunkColumnData computeChunk(DimensionServiceImpl service, DimensionConfig config,
                                         int chunkX, int chunkZ, long version) {
        int minY = config.minHeight();
        int maxY = config.maxHeight();
        TerrainMode mode = service.getTerrainMode(dimensionId);
        if (mode == TerrainMode.SPEC) {
            TerrainSampler sampler = service.createTerrainSampler(dimensionId);
            if (sampler != null) {
                if (sampler.hasDensity()) {
                    return computeDensityChunk(sampler, config, chunkX, chunkZ, minY, maxY, version);
                }
                return computeHeightChunk(sampler, config, chunkX, chunkZ, minY, maxY, version);
            }
        }
        if (mode == TerrainMode.CHUNK && service.hasChunkTerrain(dimensionId)) {
            DimensionServiceImpl.ChunkTerrainResult result =
                    service.computeChunkTerrain(dimensionId, chunkX, chunkZ);
            short[] heights = new short[DimensionServiceImpl.CHUNK_COLUMNS];
            for (int i = 0; i < heights.length; i++) {
                heights[i] = (short) (columnHeight(result.heights()[i], minY, maxY) - minY);
            }
            return new ChunkColumnData(heights, result.surfaces(), result.overrides(), null, null, null,
                    TerrainMode.CHUNK, version);
        }
        short[] heights = new short[DimensionServiceImpl.CHUNK_COLUMNS];
        String[] topBlocks = new String[DimensionServiceImpl.CHUNK_COLUMNS];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                DimensionServiceImpl.SurfaceResult surface = service.computeSurface(dimensionId,
                        chunkX * 16 + x, chunkZ * 16 + z, config.seaLevel());
                heights[z * 16 + x] = (short) (columnHeight(surface.height(), minY, maxY) - minY);
                topBlocks[z * 16 + x] = surface.blockId();
            }
        }
        return new ChunkColumnData(heights, topBlocks, Map.of(), null, null, null,
                TerrainMode.LEGACY, version);
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
            int height = columnHeight(rawHeights[i], minY, maxY);
            heights[i] = (short) (height - minY);
            String ruleBlock = sampler.surfaceBlockAt(0, height, slopes[i]);
            topBlocks[i] = ruleBlock != null ? ruleBlock : config.surfaceBlock();
        }
        return new ChunkColumnData(heights, topBlocks, Map.of(), slopes, sampler, null,
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
                int height = columnHeight(topSolid[i], minY, maxY);
                heights[i] = (short) (height - minY);
                String ruleBlock = height < minY ? null : sampler.surfaceBlockAt(0, height, slopes[i]);
                topBlocks[i] = ruleBlock != null ? ruleBlock : config.surfaceBlock();
            }
        }
        return new ChunkColumnData(heights, topBlocks, Map.of(), slopes, sampler, density,
                TerrainMode.SPEC, version);
    }

    /**
     * Resolves a Lua surface height to a block Y. Values below the build floor are treated as
     * "no ground" ({@code minY - 1}) instead of being clamped onto the bottom layer: the clamped
     * block made void columns look solid to {@code Level#getHeight}, so teleport/spawn checks
     * accepted a column at the very bottom of a floating-island dimension.
     */
    static int columnHeight(int rawHeight, int minY, int maxY) {
        if (rawHeight < minY + 1) {
            return minY - 1;
        }
        return Math.clamp(rawHeight, minY + 1, maxY - 1);
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
                           DimensionServiceImpl.TerrainMode mode, long version) {}

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

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


