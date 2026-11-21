package com.luatweaker.platform.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import java.util.List;

/**
 * Data-driven config for the {@code luatweaker:mixed_vein} feature: GTNH-style
 * multi-ore blobs where every candidate block picks its ore by weight.
 *
 * <p>Codec field names mirror {@code OreConfiguration.TargetBlockState}
 * ({@code target}, {@code state}, verified in the 1.21.1 sources) plus
 * {@code weight}. The JSON is emitted by the platform worldgen provider from
 * {@code Worldgen:AddMixedVein} registrations; hand-written JSON with the same
 * shape works too. {@code size} allows 0-256 (vanilla ore caps at 64, but
 * mixed veins are meant to be big blobs; the ellipsoid math is identical).
 */
public record MixedVeinConfiguration(List<WeightedTarget> targets, int size,
                                      float discardChanceOnAirExposure)
        implements FeatureConfiguration {

    public static final Codec<MixedVeinConfiguration> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    WeightedTarget.CODEC.listOf().fieldOf("targets")
                            .forGetter(MixedVeinConfiguration::targets),
                    Codec.intRange(0, 256).fieldOf("size")
                            .forGetter(MixedVeinConfiguration::size),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("discard_chance_on_air_exposure")
                            .forGetter(MixedVeinConfiguration::discardChanceOnAirExposure)
            ).apply(instance, MixedVeinConfiguration::new));

    /**
     * One ore candidate: the rock it replaces, the block it places, and its
     * relative weight among the candidates matching the same position.
     */
    public record WeightedTarget(RuleTest target, BlockState state, int weight) {

        public static final Codec<WeightedTarget> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                        RuleTest.CODEC.fieldOf("target")
                                .forGetter(WeightedTarget::target),
                        BlockState.CODEC.fieldOf("state")
                                .forGetter(WeightedTarget::state),
                        Codec.intRange(1, 1_000_000).fieldOf("weight")
                                .forGetter(WeightedTarget::weight)
                ).apply(instance, WeightedTarget::new));
    }
}
