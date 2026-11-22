package com.luatweaker.common;

import com.luatweaker.dimension.DimensionServiceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Samples grand_wilds terrain and derives flatness/slope statistics. */
final class GrandWildsSampler {

    private static final int CHUNK_EXTENT = 64;
    private static final int CHUNK_STEP = 16;
    private static final int SEA_LEVEL = 62;
    private static final int PEAK_MIN = 122;
    private static final int STEEP_STEP = 4;
    private static final int CLIFF_STEP = 8;

    private GrandWildsSampler() {
    }

    record TerrainSample(int minHeight, int maxHeight, int medianHeight,
            double oceanFraction, double peakFraction, double steepFraction, double cliffFraction) {
    }

    private record SlopeCounts(int steepSteps, int cliffSteps, int pairs) {
    }

    private record HeightSummary(int minHeight, int maxHeight, int medianHeight,
            int oceanColumns, int peakColumns, int columns, List<Integer> samples) {
    }

    static TerrainSample sample(DimensionServiceImpl dimensions) {
        List<int[]> chunks = scanChunkHeights(dimensions);
        SlopeCounts slopes = countSlopes(chunks);
        HeightSummary heights = summarizeHeights(chunks);
        return new TerrainSample(heights.minHeight(), heights.maxHeight(), heights.medianHeight(),
                (double) heights.oceanColumns() / heights.columns(),
                (double) heights.peakColumns() / heights.columns(),
                (double) slopes.steepSteps() / slopes.pairs(),
                (double) slopes.cliffSteps() / slopes.pairs());
    }

    private static List<int[]> scanChunkHeights(DimensionServiceImpl dimensions) {
        List<int[]> chunks = new ArrayList<>();
        // Sample one chunk every 16 chunks across +/-1024 blocks: at this continental scale a
        // tight grid can land inside a single basin and misreport the whole world.
        for (int chunkX = -CHUNK_EXTENT; chunkX <= CHUNK_EXTENT; chunkX += CHUNK_STEP) {
            for (int chunkZ = -CHUNK_EXTENT; chunkZ <= CHUNK_EXTENT; chunkZ += CHUNK_STEP) {
                chunks.add(dimensions.computeChunkTerrain(
                        AutonomousModsFixture.GRAND_WILDS, chunkX, chunkZ).heights());
            }
        }
        return chunks;
    }

    private static SlopeCounts countSlopes(List<int[]> chunks) {
        int steepSteps = 0;
        int cliffSteps = 0;
        int pairs = 0;
        for (int[] heights : chunks) {
            for (int z = 0; z < 16; z++) {
                int[] rowCounts = countRowSlopes(heights, z);
                steepSteps += rowCounts[0];
                cliffSteps += rowCounts[1];
                pairs += rowCounts[2];
            }
        }
        return new SlopeCounts(steepSteps, cliffSteps, pairs);
    }

    private static int[] countRowSlopes(int[] heights, int z) {
        int steepSteps = 0;
        int cliffSteps = 0;
        int pairs = 0;
        for (int x = 0; x + 1 < 16; x++) {
            int delta = Math.abs(heights[z * 16 + x + 1] - heights[z * 16 + x]);
            pairs++;
            if (delta >= STEEP_STEP) {
                steepSteps++;
            }
            if (delta >= CLIFF_STEP) {
                cliffSteps++;
            }
        }
        return new int[] {steepSteps, cliffSteps, pairs};
    }

    private static HeightSummary summarizeHeights(List<int[]> chunks) {
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        int oceanColumns = 0;
        int peakColumns = 0;
        int columns = 0;
        List<Integer> samples = new ArrayList<>();
        for (int[] heights : chunks) {
            for (int height : heights) {
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
                if (height < SEA_LEVEL) {
                    oceanColumns++;
                }
                if (height > PEAK_MIN) {
                    peakColumns++;
                }
                samples.add(height);
                columns++;
            }
        }
        Collections.sort(samples);
        int medianHeight = samples.get(samples.size() / 2);
        return new HeightSummary(minHeight, maxHeight, medianHeight, oceanColumns, peakColumns,
                columns, samples);
    }
}
