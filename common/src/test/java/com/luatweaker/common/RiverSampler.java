package com.luatweaker.common;

import com.luatweaker.dimension.DimensionServiceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Samples a river window and derives bank/width statistics from the heights. */
final class RiverSampler {

    private static final int RIVER_CHUNKS = 9;
    private static final int RIVER_SIZE = RIVER_CHUNKS * 16;
    private static final int SEA_LEVEL = 62;
    private static final int OCEAN_RUN_WIDTH = 48;
    private static final int NARROW_RUN_WIDTH = 2;

    private RiverSampler() {
    }

    record RiverSample(List<Integer> bankSteps, List<Integer> riverWidths,
            int maxBankStep, int wideBankStep, int medianBankStep, int medianRiverWidth,
            int wideRiverWidth, long narrowRuns, double riverWaterFraction) {
    }

    private record BankData(List<Integer> bankSteps, int waterColumns) {
    }

    static RiverSample sample(DimensionServiceImpl dimensions) {
        int[] riverOrigin = AutonomousModsFixture.findRiverWindow(dimensions, RIVER_CHUNKS / 2);
        int[][] riverHeights = sampleHeights(dimensions, riverOrigin[0], riverOrigin[1]);
        BankData banks = collectBanks(riverHeights);
        List<Integer> riverWidths = collectWidths(riverHeights);
        Collections.sort(banks.bankSteps());
        Collections.sort(riverWidths);
        List<Integer> bankSteps = banks.bankSteps();
        int maxBankStep = bankSteps.isEmpty() ? 0 : bankSteps.get(bankSteps.size() - 1);
        int wideBankStep = bankSteps.isEmpty() ? 0 : bankSteps.get((int) (bankSteps.size() * 0.95));
        int medianBankStep = bankSteps.isEmpty() ? 0 : bankSteps.get(bankSteps.size() / 2);
        int medianRiverWidth = riverWidths.isEmpty() ? 0 : riverWidths.get(riverWidths.size() / 2);
        int wideRiverWidth = riverWidths.isEmpty() ? 0
                : riverWidths.get((int) (riverWidths.size() * 0.95));
        long narrowRuns = riverWidths.stream().filter(width -> width <= NARROW_RUN_WIDTH).count();
        double riverWaterFraction = (double) banks.waterColumns() / (RIVER_SIZE * RIVER_SIZE);
        return new RiverSample(bankSteps, riverWidths, maxBankStep, wideBankStep, medianBankStep,
                medianRiverWidth, wideRiverWidth, narrowRuns, riverWaterFraction);
    }

    private static int[][] sampleHeights(DimensionServiceImpl dimensions,
            int riverBase, int riverBaseZ) {
        int[][] riverHeights = new int[RIVER_SIZE][RIVER_SIZE];
        for (int cx = 0; cx < RIVER_CHUNKS; cx++) {
            for (int cz = 0; cz < RIVER_CHUNKS; cz++) {
                DimensionServiceImpl.ChunkTerrainResult chunk = dimensions.computeChunkTerrain(
                        AutonomousModsFixture.GRAND_WILDS, riverBase + cx, riverBaseZ + cz);
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        riverHeights[cz * 16 + z][cx * 16 + x] = chunk.heights()[z * 16 + x];
                    }
                }
            }
        }
        return riverHeights;
    }

    private static BankData collectBanks(int[]... riverHeights) {
        List<Integer> bankSteps = new ArrayList<>();
        int waterColumns = 0;
        for (int z = 0; z < RIVER_SIZE; z++) {
            for (int x = 0; x < RIVER_SIZE; x++) {
                int here = riverHeights[z][x];
                if (here >= SEA_LEVEL) {
                    continue;
                }
                waterColumns++;
                addBanksForColumn(riverHeights, x, z, here, bankSteps);
            }
        }
        return new BankData(bankSteps, waterColumns);
    }

    private static void addBanksForColumn(int[][] riverHeights, int x, int z, int here,
            List<Integer> bankSteps) {
        // Compare each water column with its dry neighbours: a one-block vertical shaft
        // shows up as a huge step, a gently carved valley as a few blocks.
        if (x > 0 && riverHeights[z][x - 1] >= SEA_LEVEL) {
            bankSteps.add(riverHeights[z][x - 1] - here);
        }
        if (x + 1 < RIVER_SIZE && riverHeights[z][x + 1] >= SEA_LEVEL) {
            bankSteps.add(riverHeights[z][x + 1] - here);
        }
        if (z > 0 && riverHeights[z - 1][x] >= SEA_LEVEL) {
            bankSteps.add(riverHeights[z - 1][x] - here);
        }
        if (z + 1 < RIVER_SIZE && riverHeights[z + 1][x] >= SEA_LEVEL) {
            bankSteps.add(riverHeights[z + 1][x] - here);
        }
    }

    private static List<Integer> collectWidths(int[]... riverHeights) {
        List<Integer> riverWidths = new ArrayList<>();
        // Water runs between dry land, including runs that leave the window on one side.
        for (int z = 0; z < RIVER_SIZE; z++) {
            collectRowWidths(riverHeights, z, riverWidths);
        }
        return riverWidths;
    }

    private static void collectRowWidths(int[][] riverHeights, int z, List<Integer> riverWidths) {
        int x = 0;
        while (x < RIVER_SIZE) {
            x = scanDryRun(riverHeights, z, x, riverWidths);
        }
    }

    private static int scanDryRun(int[][] riverHeights, int z, int x, List<Integer> riverWidths) {
        if (riverHeights[z][x] >= SEA_LEVEL) {
            return x + 1;
        }
        int start = x;
        int end = start;
        while (end < RIVER_SIZE && riverHeights[z][end] < SEA_LEVEL) {
            end++;
        }
        int width = end - start;
        if (width <= OCEAN_RUN_WIDTH && start > 0 && end < RIVER_SIZE) {
            riverWidths.add(width);
        }
        return end;
    }
}
