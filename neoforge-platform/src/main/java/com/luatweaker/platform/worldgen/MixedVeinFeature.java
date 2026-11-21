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
    private static final double ZERO_RADIUS_DELTA = 0.0;
    private static final double UNIT_SPHERE_BOUND = 1.0;
    private static final float VEIN_SPREAD_DIVISOR = 8.0F;

    public MixedVeinFeature(Codec<MixedVeinConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<MixedVeinConfiguration> context) {
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        WorldGenLevel level = context.level();
        MixedVeinConfiguration config = context.config();
        VeinShape shape = VeinShape.from(origin, random, config);
        for (int worldX = shape.x(); worldX <= shape.x() + shape.width(); worldX++) {
            for (int worldZ = shape.z(); worldZ <= shape.z() + shape.width(); worldZ++) {
                if (shape.y() <= level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, worldX, worldZ)) {
                    return doPlace(level, random, config, shape);
                }
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    protected boolean doPlace(WorldGenLevel level, RandomSource random, MixedVeinConfiguration config,
                              VeinShape shape) {
        int size = config.size();
        double[] spheres = SphereField.build(random, config, shape);
        SphereField.mergeOverlapping(spheres, size);
        var vein = new VeinContext(level, random, config, shape,
                new BitSet(shape.width() * shape.height() * shape.width()),
                new BlockPos.MutableBlockPos(), new ArrayList<>());
        try (BulkSectionAccess access = new BulkSectionAccess(level)) {
            return carve(vein, spheres, access) > 0;
        }
    }

    private static int carve(VeinContext vein, double[] spheres, BulkSectionAccess access) {
        int placed = 0;
        for (int index = 0; index < vein.config().size(); index++) {
            double radius = SphereField.radius(spheres, index);
            if (radius >= ZERO_RADIUS_DELTA) {
                placed += carveSphere(vein, access, spheres, index, radius);
            }
        }
        return placed;
    }

    private static int carveSphere(VeinContext vein, BulkSectionAccess access, double[] spheres,
                                   int index, double radius) {
        VeinShape shape = vein.shape();
        double centerX = SphereField.center(spheres, index, 0);
        double centerY = SphereField.center(spheres, index, 1);
        double centerZ = SphereField.center(spheres, index, 2);
        int minX = Math.max(Mth.floor(centerX - radius), shape.x());
        int minY = Math.max(Mth.floor(centerY - radius), shape.y());
        int minZ = Math.max(Mth.floor(centerZ - radius), shape.z());
        int maxX = Math.max(Mth.floor(centerX + radius), minX);
        int maxY = Math.max(Mth.floor(centerY + radius), minY);
        int maxZ = Math.max(Mth.floor(centerZ + radius), minZ);
        int placed = 0;
        for (int blockX = minX; blockX <= maxX; blockX++) {
            double dx = (blockX + 0.5 - centerX) / radius;
            for (int blockY = minY; blockY <= maxY; blockY++) {
                double dy = (blockY + 0.5 - centerY) / radius;
                for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                    double dz = (blockZ + 0.5 - centerZ) / radius;
                    if (dx * dx + dy * dy + dz * dz < UNIT_SPHERE_BOUND
                            && !vein.level().isOutsideBuildHeight(blockY)) {
                        placed += placeAt(vein, access, blockX, blockY, blockZ);
                    }
                }
            }
        }
        return placed;
    }

    private static int placeAt(VeinContext vein, BulkSectionAccess access, int blockX, int blockY, int blockZ) {
        VeinShape shape = vein.shape();
        int index = blockX - shape.x() + (blockY - shape.y()) * shape.width()
                + (blockZ - shape.z()) * shape.width() * shape.height();
        if (vein.visited().get(index)) {
            return 0;
        }
        vein.visited().set(index);
        vein.mutable().set(blockX, blockY, blockZ);
        if (!vein.level().ensureCanWrite(vein.mutable())) {
            return 0;
        }
        LevelChunkSection section = access.getSection(vein.mutable());
        if (section == null) {
            return 0;
        }
        int localX = SectionPos.sectionRelative(blockX);
        int localY = SectionPos.sectionRelative(blockY);
        int localZ = SectionPos.sectionRelative(blockZ);
        BlockState current = section.getBlockState(localX, localY, localZ);
        return rollTarget(vein, access, section, current, localX, localY, localZ);
    }

    private static int rollTarget(VeinContext vein, BulkSectionAccess access, LevelChunkSection section,
                                  BlockState current, int localX, int localY, int localZ) {
        List<MixedVeinConfiguration.WeightedTarget> matching = vein.matching();
        matching.clear();
        int totalWeight = 0;
        for (MixedVeinConfiguration.WeightedTarget candidate : vein.config().targets()) {
            if (canPlaceOre(current, access::getBlockState, vein.random(), vein.config(), candidate,
                    vein.mutable())) {
                matching.add(candidate);
                totalWeight += candidate.weight();
            }
        }
        if (matching.isEmpty()) {
            return 0;
        }
        int roll = vein.random().nextInt(totalWeight);
        for (MixedVeinConfiguration.WeightedTarget candidate : matching) {
            roll -= candidate.weight();
            if (roll < 0) {
                section.setBlockState(localX, localY, localZ, candidate.state(), false);
                return 1;
            }
        }
        return 0;
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
        return !isAdjacentToAir(adjacentAccessor, pos);
    }

    /** Ellipsoid bounds computed once per vein. */
    record VeinShape(double minX, double maxX, double minZ, double maxZ, double minY, double maxY,
                     int x, int y, int z, int width, int height) {

        static VeinShape from(BlockPos origin, RandomSource random, MixedVeinConfiguration config) {
            float angle = random.nextFloat() * (float) Math.PI;
            float spread = config.size() / VEIN_SPREAD_DIVISOR;
            int radius = Mth.ceil((config.size() / 16.0F * 2.0F + 1.0F) / 2.0F);
            return new VeinShape(
                    origin.getX() + Math.sin(angle) * spread,
                    origin.getX() - Math.sin(angle) * spread,
                    origin.getZ() + Math.cos(angle) * spread,
                    origin.getZ() - Math.cos(angle) * spread,
                    origin.getY() + random.nextInt(3) - 2,
                    origin.getY() + random.nextInt(3) - 2,
                    origin.getX() - Mth.ceil(spread) - radius,
                    origin.getY() - 2 - radius,
                    origin.getZ() - Mth.ceil(spread) - radius,
                    2 * (Mth.ceil(spread) + radius),
                    2 * (2 + radius));
        }
    }

    /** Per-vein mutable state shared by the carve walk. */
    record VeinContext(WorldGenLevel level, RandomSource random, MixedVeinConfiguration config,
                       VeinShape shape, BitSet visited, BlockPos.MutableBlockPos mutable,
                       List<MixedVeinConfiguration.WeightedTarget> matching) {
    }
}




