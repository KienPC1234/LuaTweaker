package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.noise.NoiseServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies DimensionBenchmark call counting for batch and legacy terrain modes. */
class DimensionBenchmarkTest extends DimensionServiceTestBase {

    private static final String BATCH_ID = "minecraft:batch";
    private static final String LEGACY_ID = "minecraft:legacy";

    private record Report(int chunks, long luaCalls, double callsPerChunk) {
        static Report snapshot(DimensionBenchmark.Report report) {
            return new Report(report.chunks(), report.luaCalls(), report.luaCallsPerChunk());
        }
    }

    @Test
    void batchTerrainCountsOneLuaCallPerChunk() {
        service.create(BATCH_ID, engine.createTable());
        engine.executeStringOrThrow(
                "function benchChunk(cx, cz, ctx)\n"
                        + "    local h = {}\n"
                        + "    for i = 1, 256 do h[i] = 64 end\n"
                        + "    return h\n"
                        + "end", "bench_batch_setup");
        service.setChunkTerrain(BATCH_ID, engine.getGlobalEnvironment().rawget("benchChunk"));

        assertEquals(new Report(9, 9L, 1.0),
                Report.snapshot(DimensionBenchmark.run(service, BATCH_ID, 1)),
                "batch terrain must cost one Lua call per chunk");
    }

    @Test
    void legacyTerrainCountsOneCallPerColumn() {
        service.create(LEGACY_ID, engine.createTable());
        engine.executeStringOrThrow(
                "function legacyGenerator(x, z, baseHeight) return baseHeight end",
                "bench_legacy_setup");
        service.setTerrainGenerator(LEGACY_ID, engine.getGlobalEnvironment().rawget("legacyGenerator"));

        assertEquals(new Report(9, 9L * 256, 256.0),
                Report.snapshot(DimensionBenchmark.run(service, LEGACY_ID, 1)),
                "legacy terrain must cost one Lua call per column");
    }

    @Test
    void benchmarkRejectsUnknownDimension() {
        service.create(BATCH_ID, engine.createTable());

        assertThrows(IllegalArgumentException.class,
                () -> DimensionBenchmark.run(service, "minecraft:unknown", 1),
                "benchmarking an unknown dimension must be rejected");
    }

    @Test
    void benchmarkRejectsRadiusAboveMaximum() {
        service.create(BATCH_ID, engine.createTable());

        assertThrows(IllegalArgumentException.class,
                () -> DimensionBenchmark.run(service, BATCH_ID, DimensionBenchmark.MAX_RADIUS + 1),
                "a benchmark radius above the maximum must be rejected");
    }

    @Test
    void specModeUsesNoLuaCalls() {
        service.create("minecraft:spec", engine.createTable());
        service.setTerrainSpec("minecraft:spec", terrainSpecTable());
        LuaServiceRegistry.register("NoiseServiceImpl", new NoiseServiceImpl(4242L));
        try {
            DimensionBenchmark.Report report = DimensionBenchmark.run(service, "minecraft:spec", 1);
            assertEquals(List.of(0L, 0.0),
                    List.of(report.luaCalls(), report.luaCallsPerChunk()),
                    "spec terrain must be sampled natively without Lua calls");
        } finally {
            LuaServiceRegistry.clear();
        }
    }

    private ILuaTable terrainSpecTable() {
        ILuaTable layer = engine.createTable();
        layer.rawset("kind", "fbm");
        layer.rawset("amp", 20.0);
        layer.rawset("frequency", 0.01);
        ILuaTable layers = engine.createTable();
        layers.rawset(1, layer);
        ILuaTable spec = engine.createTable();
        spec.rawset("height", layers);
        return spec;
    }
}
