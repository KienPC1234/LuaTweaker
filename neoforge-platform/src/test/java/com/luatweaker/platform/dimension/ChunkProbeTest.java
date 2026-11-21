package com.luatweaker.platform.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.core.Holder;
import com.mojang.serialization.MapCodec;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each chunk must use its own terrain instead of repeating chunk zero. */
class ChunkProbeTest {

    private static final String PROBE_ID = "minecraft:probe";

    private record ProbeContext(ILuaEngine engine, DimensionServiceImpl service,
            DimensionConfig config, LuaChunkGenerator generator) {
    }

    private static ProbeContext probeContext() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionServiceImpl service = new DimensionServiceImpl(engine);
        service.create(PROBE_ID, engine.createTable());
        engine.executeStringOrThrow(
                "function probeChunk(cx, cz, ctx)\n"
                + "    local heights = {}\n"
                + "    for z = 0, 15 do\n"
                + "        for x = 0, 15 do\n"
                + "            heights[z * 16 + x + 1] = 60 + cx * 10 + cz * 100\n"
                + "        end\n"
                + "    end\n"
                + "    return heights\n"
                + "end\n",
                "probe_chunk_terrain");
        service.setChunkTerrain(PROBE_ID, engine.getGlobalEnvironment().rawget("probeChunk"));

        DimensionConfig config = service.getConfig(PROBE_ID);
        LuaChunkGenerator generator = new LuaChunkGenerator(new EmptyBiomeSource(), PROBE_ID);
        return new ProbeContext(engine, service, config, generator);
    }

    @Test
    void chunkZeroHasItsOwnBaseHeight() {
        ProbeContext context = probeContext();
        try {
            int relativeBase = -context.config().minHeight();

            assertEquals(60 + relativeBase,
                    context.generator().columnDataForChunk(
                            context.service(), context.config(), 0, 0).heights()[0],
                    "chunk zero must report its own terrain height");
        } finally {
            context.engine().shutdown();
        }
    }

    @Test
    void chunkOneDoesNotReuseChunkZero() {
        ProbeContext context = probeContext();
        try {
            int relativeBase = -context.config().minHeight();

            assertEquals(70 + relativeBase,
                    context.generator().columnDataForChunk(
                            context.service(), context.config(), 1, 0).heights()[0],
                    "chunk (1,0) must not reuse the terrain of chunk (0,0)");
        } finally {
            context.engine().shutdown();
        }
    }

    @Test
    void neighboringChunksDiffer() {
        ProbeContext context = probeContext();
        try {
            short first = context.generator().columnDataForChunk(
                    context.service(), context.config(), 0, 0).heights()[0];
            short second = context.generator().columnDataForChunk(
                    context.service(), context.config(), 1, 0).heights()[0];

            assertNotEquals(first, second,
                    "neighboring chunks must sample different terrain");
        } finally {
            context.engine().shutdown();
        }
    }

    @Test
    void blockLookupResolvesToTheSameChunk() {
        ProbeContext context = probeContext();
        try {
            short[] byChunk = context.generator().columnDataForChunk(
                    context.service(), context.config(), 1, 0).heights();
            short[] byBlock = context.generator().columnDataAtBlock(
                    context.service(), context.config(), 16, 0).heights();

            assertArrayEquals(byChunk, byBlock,
                    "block-coordinate lookup must resolve to the same chunk");
        } finally {
            context.engine().shutdown();
        }
    }

    @Test
    void adjacentChunksInTheSameRowStayDistinct() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        try {
            DimensionServiceImpl service = new DimensionServiceImpl(engine);
            service.create(PROBE_ID, engine.createTable());
            engine.executeStringOrThrow(
                    "function probeChunk(cx, cz, ctx)\n"
                    + "    local heights = {}\n"
                    + "    for i = 1, 256 do heights[i] = 80 + cx end\n"
                    + "    return heights\n"
                    + "end\n",
                    "probe_chunk_terrain_2");
            service.setChunkTerrain(PROBE_ID, engine.getGlobalEnvironment().rawget("probeChunk"));

            DimensionConfig config = service.getConfig(PROBE_ID);
            LuaChunkGenerator generator = new LuaChunkGenerator(new EmptyBiomeSource(), PROBE_ID);
            short previous = generator.columnDataForChunk(service, config, 0, 0).heights()[0];
            for (int chunkX = 1; chunkX <= 16; chunkX++) {
                short current = generator.columnDataForChunk(service, config, chunkX, 0).heights()[0];

                assertTrue(current > previous,
                        "chunk " + chunkX + " reused chunk " + (chunkX - 1)
                                + " terrain: " + current);
                previous = current;
            }
        } finally {
            engine.shutdown();
        }
    }

    /** The generator never asks this stub for a biome; it only satisfies the constructor. */
    static final class EmptyBiomeSource extends BiomeSource {
        @Override
        protected MapCodec<? extends BiomeSource> codec() {
            return null;
        }

        @Override
        protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return Stream.empty();
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
            throw new UnsupportedOperationException("not used by the terrain tests");
        }
    }
}
