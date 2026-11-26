package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IFluidBuilder;
import dev.architectury.core.fluid.ArchitecturyFlowingFluid;
import dev.architectury.core.fluid.ArchitecturyFluidAttributes;
import dev.architectury.core.fluid.SimpleArchitecturyFluidAttributes;
import dev.architectury.core.item.ArchitecturyBucketItem;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

final class FluidAttributesFactory {

    private FluidAttributesFactory() {
    }

    static ArchitecturyFluidAttributes build(
            IFluidBuilder builder,
            Function<String, ResourceLocation> locationParser,
            AtomicReference<RegistrySupplier<FlowingFluid>> stillRef,
            AtomicReference<RegistrySupplier<FlowingFluid>> flowRef,
            AtomicReference<RegistrySupplier<LiquidBlock>> blockRef,
            AtomicReference<RegistrySupplier<Item>> bucketRef) {
        ResourceLocation stillTex = builder.getStillTexture() != null
                ? locationParser.apply(builder.getStillTexture())
                : ResourceLocation.withDefaultNamespace("block/water_still");
        ResourceLocation flowTex = builder.getFlowingTexture() != null
                ? locationParser.apply(builder.getFlowingTexture())
                : ResourceLocation.withDefaultNamespace("block/water_flow");

        return SimpleArchitecturyFluidAttributes.ofSupplier(flowRef::get, stillRef::get)
                .blockSupplier(blockRef::get)
                .bucketItemSupplier(bucketRef::get)
                .sourceTexture(stillTex)
                .flowingTexture(flowTex)
                .color(builder.getColor() | 0xFF000000)
                .luminosity(builder.getLightLevel())
                .density(builder.getDensity())
                .temperature(builder.getTemperature())
                .viscosity(builder.getViscosity())
                .slopeFindDistance(builder.getSlopeFindDistance())
                .dropOff(builder.getLevelDecreasePerBlock())
                .tickDelay(builder.getTickRate())
                .explosionResistance(builder.getExplosionResistance())
                .rarity(parseRarity(builder.getRarity()));
    }

    static FlowingFluid createSource(ArchitecturyFluidAttributes attributes) {
        return new ArchitecturyFlowingFluid.Source(attributes);
    }

    static FlowingFluid createFlowing(ArchitecturyFluidAttributes attributes) {
        return new ArchitecturyFlowingFluid.Flowing(attributes);
    }

    static Item createBucket(RegistrySupplier<FlowingFluid> stillSupplier, IFluidBuilder builder) {
        return new ArchitecturyBucketItem(stillSupplier,
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)
                        .rarity(parseRarity(builder.getRarity())));
    }

    static Rarity parseRarity(String rawRarity) {
        if (rawRarity == null) {
            return Rarity.COMMON;
        }
        return switch (rawRarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> Rarity.UNCOMMON;
            case "RARE" -> Rarity.RARE;
            case "EPIC" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }
}
