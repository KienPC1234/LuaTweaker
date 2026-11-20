package com.luatweaker.platform.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Void columns must not be clamped onto the bottom layer (that made them look solid). */
class LuaChunkGeneratorTest {

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void heightsBelowTheFloorBecomeVoidColumns() {
        assertEquals(-65, LuaChunkGenerator.columnHeight(-64, -64, 320));
        assertEquals(-65, LuaChunkGenerator.columnHeight(-500, -64, 320));
    }

    @Test
    void groundHeightsStayInsideTheBuildHeight() {
        assertEquals(-63, LuaChunkGenerator.columnHeight(-63, -64, 320));
        assertEquals(180, LuaChunkGenerator.columnHeight(180, -64, 320));
        assertEquals(319, LuaChunkGenerator.columnHeight(9_999, -64, 320));
    }

    // ---------- vanilla getBaseHeight contract ----------

    @Test
    void firstFreeHeightIsOneAboveTheSurface() {
        assertEquals(71, LuaChunkGenerator.firstFreeHeight(70, -64, 320, 63, false));
    }

    @Test
    void firstFreeHeightCountsWaterOnlyWhenAsked() {
        assertEquals(64, LuaChunkGenerator.firstFreeHeight(50, -64, 320, 63, true));
        assertEquals(51, LuaChunkGenerator.firstFreeHeight(50, -64, 320, 63, false));
    }

    @Test
    void firstFreeHeightFallsBackToMinYForVoidColumns() {
        assertEquals(-64, LuaChunkGenerator.firstFreeHeight(-65, -64, 320, 63, false));
        assertEquals(64, LuaChunkGenerator.firstFreeHeight(-65, -64, 320, 63, true),
                "a water-filled void column reports the ocean surface");
    }

    @Test
    void firstFreeHeightStaysInsideTheBuildHeight() {
        assertEquals(320, LuaChunkGenerator.firstFreeHeight(319, -64, 320, 63, false));
    }

    // ---------- chunk coordinate regression ----------

    @Test
    void eachChunkUsesItsOwnTerrainInsteadOfRepeatingChunkZero() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        try {
            DimensionServiceImpl service = new DimensionServiceImpl(engine);
            service.create("minecraft:probe", engine.createTable());
            engine.executeStringOrThrow(
                    "function probeChunk(cx, cz, ctx)\n" +
                    "    local heights = {}\n" +
                    "    for z = 0, 15 do\n" +
                    "        for x = 0, 15 do\n" +
                    "            heights[z * 16 + x + 1] = 60 + cx * 10 + cz * 100\n" +
                    "        end\n" +
                    "    end\n" +
                    "    return heights\n" +
                    "end\n",
                    "probe_chunk_terrain");
            service.setChunkTerrain("minecraft:probe", engine.getGlobalEnvironment().rawget("probeChunk"));

            DimensionConfig config = service.getConfig("minecraft:probe");
            LuaChunkGenerator generator = new LuaChunkGenerator(new EmptyBiomeSource(), "minecraft:probe");

            LuaChunkGenerator.ChunkColumnData chunk0 =
                    generator.columnDataForChunk(service, config, 0, 0);
            LuaChunkGenerator.ChunkColumnData chunk1 =
                    generator.columnDataForChunk(service, config, 1, 0);
            LuaChunkGenerator.ChunkColumnData chunk1ViaBlock =
                    generator.columnDataAtBlock(service, config, 16, 0);

            int relativeBase = -config.minHeight();
            assertEquals(60 + relativeBase, chunk0.heights()[0]);
            assertEquals(70 + relativeBase, chunk1.heights()[0],
                    "chunk (1,0) must not reuse the terrain of chunk (0,0)");
            assertNotEquals(chunk0.heights()[0], chunk1.heights()[0]);
            assertArrayEquals(chunk1.heights(), chunk1ViaBlock.heights(),
                    "block-coordinate lookup must resolve to the same chunk");
        } finally {
            engine.shutdown();
        }
    }

    @Test
    void adjacentChunksInTheSameRowStayDistinct() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        try {
            DimensionServiceImpl service = new DimensionServiceImpl(engine);
            service.create("minecraft:probe", engine.createTable());
            engine.executeStringOrThrow(
                    "function probeChunk(cx, cz, ctx)\n" +
                    "    local heights = {}\n" +
                    "    for i = 1, 256 do heights[i] = 80 + cx end\n" +
                    "    return heights\n" +
                    "end\n",
                    "probe_chunk_terrain_2");
            service.setChunkTerrain("minecraft:probe", engine.getGlobalEnvironment().rawget("probeChunk"));

            DimensionConfig config = service.getConfig("minecraft:probe");
            LuaChunkGenerator generator = new LuaChunkGenerator(new EmptyBiomeSource(), "minecraft:probe");
            short previous = generator.columnDataForChunk(service, config, 0, 0).heights()[0];
            for (int chunkX = 1; chunkX <= 16; chunkX++) {
                short current = generator.columnDataForChunk(service, config, chunkX, 0).heights()[0];
                assertTrue(current > previous,
                        "chunk " + chunkX + " reused chunk " + (chunkX - 1) + " terrain: " + current);
                previous = current;
            }
        } finally {
            engine.shutdown();
        }
    }

    /** The generator never asks this stub for a biome; it only satisfies the constructor. */
    private static final class EmptyBiomeSource extends BiomeSource {
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
