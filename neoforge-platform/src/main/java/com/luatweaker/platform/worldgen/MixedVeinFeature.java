package com.luatweaker.platform.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Function;

/**
 * GTNH-style mixed-vein generator: an ellipsoid blob (the exact walk from
 * vanilla {@code OreFeature}, verified against the 1.21.1 sources) where every
 * candidate position picks its ore by weight instead of first-match-wins.
 *
 * <p>Why a custom feature: vanilla {@code OreFeature.doPlace} iterates targets
 * and places the FIRST match, so one JSON feature can never mix several ores
 * in one blob. Here all matching candidates are collected per position and one
 * is rolled by weight, giving true per-vein mixing ratios (e.g. 70% magnetite,
 * 20% vanadium magnetite, 10% gold).
 *
 * <p>Placement (count/rarity, height distribution, biome scoping) is vanilla:
 * the emitted placed-feature JSON uses the standard modifiers, so veins obey
 * the same per-chunk frequency and height rules as vanilla ores.
 */
public class MixedVeinFeature extends Feature<MixedVeinConfiguration> {

    public MixedVeinFeature(Codec<MixedVeinConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<MixedVeinConfiguration> context) {
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();
        MixedVeinConfiguration config = context.config();
        float angle = random.nextFloat() * (float) Math.PI;
        float spread = (float) config.size() / 8.0F;
        int radius = Mth.ceil(((float) config.size() / 16.0F * 2.0F + 1.0F) / 2.0F);
        double minX = (double) origin.getX() + Math.sin(angle) * (double) spread;
        double maxX = (double) origin.getX() - Math.sin(angle) * (double) spread;
        double minZ = (double) origin.getZ() + Math.cos(angle) * (double) spread;
        double maxZ = (double) origin.getZ() - Math.cos(angle) * (double) spread;
        double minY = (double) (origin.getY() + random.nextInt(3) - 2);
        double maxY = (double) (origin.getY() + random.nextInt(3) - 2);
        int x = origin.getX() - Mth.ceil(spread) - radius;
        int y = origin.getY() - 2 - radius;
        int z = origin.getZ() - Mth.ceil(spread) - radius;
        int width = 2 * (Mth.ceil(spread) + radius);
        int height = 2 * (2 + radius);

        for (int l1 = x; l1 <= x + width; l1++) {
            for (int i2 = z; i2 <= z + width; i2++) {
                if (y <= level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, l1, i2)) {
                    return doPlace(level, random, config, minX, maxX, minZ, maxZ,
                            minY, maxY, x, y, z, width, height);
                }
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    protected boolean doPlace(WorldGenLevel level, RandomSource random,
                              MixedVeinConfiguration config,
                              double minX, double maxX, double minZ, double maxZ,
                              double minY, double maxY,
                              int x, int y, int z, int width, int height) {
        int placed = 0;
        BitSet visited = new BitSet(width * height * width);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int size = config.size();
        double[] spheres = new double[size * 4];

        for (int k = 0; k < size; k++) {
            float f = (float) k / (float) size;
            double d0 = Mth.lerp(f, minX, maxX);
            double d1 = Mth.lerp(f, minY, maxY);
            double d2 = Mth.lerp(f, minZ, maxZ);
            double d3 = random.nextDouble() * (double) size / 16.0;
            double d4 = ((double) (Mth.sin((float) Math.PI * f) + 1.0F) * d3 + 1.0) / 2.0;
            spheres[k * 4 + 0] = d0;
            spheres[k * 4 + 1] = d1;
            spheres[k * 4 + 2] = d2;
            spheres[k * 4 + 3] = d4;
        }

        for (int l3 = 0; l3 < size - 1; l3++) {
            if (!(spheres[l3 * 4 + 3] <= 0.0)) {
                for (int i4 = l3 + 1; i4 < size; i4++) {
                    if (!(spheres[i4 * 4 + 3] <= 0.0)) {
                        double d8 = spheres[l3 * 4 + 0] - spheres[i4 * 4 + 0];
                        double d10 = spheres[l3 * 4 + 1] - spheres[i4 * 4 + 1];
                        double d12 = spheres[l3 * 4 + 2] - spheres[i4 * 4 + 2];
                        double d14 = spheres[l3 * 4 + 3] - spheres[i4 * 4 + 3];
                        if (d14 * d14 > d8 * d8 + d10 * d10 + d12 * d12) {
                            if (d14 > 0.0) {
                                spheres[i4 * 4 + 3] = -1.0;
                            } else {
                                spheres[l3 * 4 + 3] = -1.0;
                            }
                        }
                    }
                }
            }
        }

        List<MixedVeinConfiguration.WeightedTarget> matching = new ArrayList<>();
        try (BulkSectionAccess access = new BulkSectionAccess(level)) {
            for (int j4 = 0; j4 < size; j4++) {
                double radiusAt = spheres[j4 * 4 + 3];
                if (!(radiusAt < 0.0)) {
                    double cx = spheres[j4 * 4 + 0];
                    double cy = spheres[j4 * 4 + 1];
                    double cz = spheres[j4 * 4 + 2];
                    int x0 = Math.max(Mth.floor(cx - radiusAt), x);
                    int y0 = Math.max(Mth.floor(cy - radiusAt), y);
                    int z0 = Math.max(Mth.floor(cz - radiusAt), z);
                    int x1 = Math.max(Mth.floor(cx + radiusAt), x0);
                    int y1 = Math.max(Mth.floor(cy + radiusAt), y0);
                    int z1 = Math.max(Mth.floor(cz + radiusAt), z0);

                    for (int px = x0; px <= x1; px++) {
                        double dx = ((double) px + 0.5 - cx) / radiusAt;
                        if (dx * dx < 1.0) {
                            for (int py = y0; py <= y1; py++) {
                                double dy = ((double) py + 0.5 - cy) / radiusAt;
                                if (dx * dx + dy * dy < 1.0) {
                                    for (int pz = z0; pz <= z1; pz++) {
                                        double dz = ((double) pz + 0.5 - cz) / radiusAt;
                                        if (dx * dx + dy * dy + dz * dz < 1.0
                                                && !level.isOutsideBuildHeight(py)) {
                                            int index = px - x + (py - y) * width
                                                    + (pz - z) * width * height;
                                            if (!visited.get(index)) {
                                                visited.set(index);
                                                mutable.set(px, py, pz);
                                                if (level.ensureCanWrite(mutable)) {
                                                    LevelChunkSection section =
                                                            access.getSection(mutable);
                                                    if (section != null) {
                                                        int sx = SectionPos.sectionRelative(px);
                                                        int sy = SectionPos.sectionRelative(py);
                                                        int sz = SectionPos.sectionRelative(pz);
                                                        BlockState current =
                                                                section.getBlockState(sx, sy, sz);
                                                        matching.clear();
                                                        int totalWeight = 0;
                                                        for (MixedVeinConfiguration.WeightedTarget candidate
                                                                : config.targets()) {
                                                            if (canPlaceOre(current,
                                                                    access::getBlockState,
                                                                    random, config, candidate, mutable)) {
                                                                matching.add(candidate);
                                                                totalWeight += candidate.weight();
                                                            }
                                                        }
                                                        if (!matching.isEmpty()) {
                                                            int roll = random.nextInt(totalWeight);
                                                            for (MixedVeinConfiguration.WeightedTarget candidate
                                                                    : matching) {
                                                                roll -= candidate.weight();
                                                                if (roll < 0) {
                                                                    section.setBlockState(sx, sy, sz,
                                                                            candidate.state(), false);
                                                                    placed++;
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return placed > 0;
    }

    /**
     * Mirrors {@code OreFeature.canPlaceOre} (verified 1.21.1 source) for a
     * weighted target: rock must match, then the air-exposure roll decides.
     */
    private static boolean canPlaceOre(BlockState state,
                                       Function<BlockPos, BlockState> adjacentAccessor,
                                       RandomSource random,
                                       MixedVeinConfiguration config,
                                       MixedVeinConfiguration.WeightedTarget target,
                                       BlockPos.MutableBlockPos pos) {
        if (!target.target().test(state, random)) {
            return false;
        }
        float chance = config.discardChanceOnAirExposure();
        if (chance <= 0.0F || random.nextFloat() >= chance) {
            return true;
        }
        return !Feature.isAdjacentToAir(adjacentAccessor, pos);
    }
}
