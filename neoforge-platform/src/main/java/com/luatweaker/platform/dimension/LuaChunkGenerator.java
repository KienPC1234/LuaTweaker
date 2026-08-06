package com.luatweaker.platform.dimension;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final int MAX_CACHED_CHUNKS = 1024;
    private static final int GEN_DEPTH = 16;
    /** Vanilla per-chunk placement seeds (same constants vanilla features use). */
    private static final long CHUNK_X_SEED = 341873128712L;
    private static final long CHUNK_Z_SEED = 132897987541L;

    private final String dimensionId;
    private final BlockState airState = Blocks.AIR.defaultBlockState();

    /** Per-chunk column cache: chunk key -> 16x16 heights + top block ids. */
    private final Map<Long, ChunkColumnData> columnCache = new ConcurrentHashMap<>();
    private volatile long cachedGenerationVersion = -1;

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

        Heightmap[] heightmaps = new Heightmap[Heightmap.Types.values().length];
        for (Heightmap.Types type : Heightmap.Types.values()) {
            heightmaps[type.ordinal()] = chunk.getOrCreateHeightmapUnprimed(type);
        }

        short[] heights = new short[256];
        String[] topBlocks = new String[256];
        BlockState waterState = resolveBlock(config.waterBlock());

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int worldX = pos.getMinBlockX() + x;
                int worldZ = pos.getMinBlockZ() + z;
                DimensionServiceImpl.SurfaceResult surface =
                        service.computeSurface(dimensionId, worldX, worldZ, config.seaLevel());

                int height = Math.clamp(surface.height(), minY + 1, maxY - 1);
                heights[z * 16 + x] = (short) (height - minY); // offset storage: never negative
                topBlocks[z * 16 + x] = surface.blockId();

                Map<Integer, String> overrides =
                        service.computeBlockOverrides(dimensionId, worldX, worldZ, height, minY, maxY);
                Map<Integer, String> strataOverrides = service.computeStrataOverrides(dimensionId, worldX, worldZ, height);
                for (int y = minY; y <= height; y++) {
                    BlockState state;
                    if (overrides.containsKey(y)) {
                        state = resolveBlock(overrides.get(y));
                    } else {
                        int depth = height - y;
                        String blockId = strataOverrides.containsKey(depth)
                                ? strataOverrides.get(depth)
                                : com.luatweaker.dimension.TerrainColumn.blockAtDepth(config, height, y);
                        state = resolveBlock(blockId);
                    }
                    chunk.setBlockState(new BlockPos(worldX, y, worldZ), state, false);
                    for (Heightmap heightmap : heightmaps) {
                        heightmap.update(x, y, z, state);
                    }
                }
                for (int y = height + 1; y < maxY; y++) {
                    BlockState state;
                    if (overrides.containsKey(y)) {
                        state = resolveBlock(overrides.get(y));
                    } else {
                        state = y <= config.seaLevel() ? waterState : airState;
                    }
                    chunk.setBlockState(new BlockPos(worldX, y, worldZ), state, false);
                    for (Heightmap heightmap : heightmaps) {
                        heightmap.update(x, y, z, state);
                    }
                }
            }
        }

        // Cache the column data for getBaseHeight/getBaseColumn on the same chunk.
        columnCache.put(chunkKey(pos), new ChunkColumnData(heights, topBlocks, service.getGenerationVersion()));

        for (Heightmap.Types type : Heightmap.Types.values()) {
            chunk.setHeightmap(type, heightmaps[type.ordinal()].getRawData());
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
            java.util.Optional<EntityType<?>> type = EntityType.byString(entry.entity());
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
                int y = getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING, region, null) + 1;
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
            java.util.Optional<EntityType<?>> type = EntityType.byString(entry.entity());
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
        ChunkColumnData data = columnData(service, config, x, z);
        return data.heights[columnIndex(x, z)] + config.minHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor levelHeightAccessor,
                                     RandomState randomState) {
        DimensionServiceImpl service = requireService();
        DimensionConfig config = requireConfig(service);
        int minY = levelHeightAccessor.getMinBuildHeight();
        int maxY = minY + levelHeightAccessor.getHeight();

        ChunkColumnData data = columnData(service, config, x, z);
        int index = columnIndex(x, z);
        int height = data.heights[index] + config.minHeight();
        String topBlock = data.topBlocks[index];

        BlockState[] states = new BlockState[maxY - minY];
        BlockState waterState = resolveBlock(config.waterBlock());
        Map<Integer, String> overrides = service.computeBlockOverrides(dimensionId, x, z, height, minY, maxY);
        for (int y = minY; y <= height; y++) {
            states[y - minY] = overrides.containsKey(y)
                    ? resolveBlock(overrides.get(y))
                    : resolveBlock(com.luatweaker.dimension.TerrainColumn.blockAtDepth(config, height, y));
        }
        for (int y = height + 1; y < maxY; y++) {
            states[y - minY] = overrides.containsKey(y)
                    ? resolveBlock(overrides.get(y))
                    : (y <= config.seaLevel() ? waterState : airState);
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public int getGenDepth() {
        return GEN_DEPTH;
    }

    @Override
    public int getSeaLevel() {
        return requireConfig(requireService()).seaLevel();
    }

    @Override
    public int getMinY() {
        return requireConfig(requireService()).minHeight();
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor levelHeightAccessor) {
        return requireConfig(requireService()).seaLevel();
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("Lua dimension: " + dimensionId);
    }

    // ---------------------------------------------------------------------
    // Column cache
    // ---------------------------------------------------------------------

    /** Returns cached (or freshly computed) column data for the chunk containing (x, z). */
    private ChunkColumnData columnData(DimensionServiceImpl service, DimensionConfig config, int x, int z) {
        long version = service.getGenerationVersion();
        if (version != cachedGenerationVersion) {
            columnCache.clear();
            cachedGenerationVersion = version;
        }
        int chunkX = Math.floorDiv(x, 16);
        int chunkZ = Math.floorDiv(z, 16);
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
        short[] heights = new short[256];
        String[] topBlocks = new String[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                DimensionServiceImpl.SurfaceResult surface = service.computeSurface(dimensionId,
                        chunkX * 16 + x, chunkZ * 16 + z, config.seaLevel());
                heights[z * 16 + x] = (short) (Math.clamp(surface.height(),
                                config.minHeight() + 1, config.maxHeight() - 1) - config.minHeight());
                topBlocks[z * 16 + x] = surface.blockId();
            }
        }
        return new ChunkColumnData(heights, topBlocks, version);
    }

    private static long chunkKey(ChunkPos pos) {
        return chunkKey(pos.x, pos.z);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int columnIndex(int x, int z) {
        return (Math.floorMod(z, 16) * 16) + Math.floorMod(x, 16);
    }

    private record ChunkColumnData(short[] heights, String[] topBlocks, long version) {}

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


