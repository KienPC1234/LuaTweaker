package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.log.LogStage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public final class FluidRegistrar {
    final IContentService contentService;
    final Map<ResourceLocation, Fluid> createdFluids;
    final Map<ResourceLocation, Item> createdItems;
    final Map<ResourceLocation, Block> createdBlocks;
    private final Map<ResourceLocation, FluidType> createdFluidTypes = new ConcurrentHashMap<>();
    final Function<String, ResourceLocation> locationParser;

    public FluidRegistrar(IContentService contentService, Map<ResourceLocation, Fluid> createdFluids, Map<ResourceLocation, Item> createdItems, Map<ResourceLocation, Block> createdBlocks, Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdFluids = createdFluids;
        this.createdItems = createdItems;
        this.createdBlocks = createdBlocks;
        this.locationParser = locationParser;
    }

    FluidType getOrCreateFluidType(ResourceLocation rl, IFluidBuilder builder) {
        return createdFluidTypes.computeIfAbsent(rl, key -> {
            ResourceLocation stillTex = builder.getStillTexture() != null
                    ? locationParser.apply(builder.getStillTexture())
                    : ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
            ResourceLocation flowTex = builder.getFlowingTexture() != null
                    ? locationParser.apply(builder.getFlowingTexture())
                    : ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow");

            FluidType.Properties props = FluidType.Properties.create()
                    .descriptionId("fluid.luatweaker." + builder.getId())
                    .temperature(builder.getTemperature())
                    .viscosity(builder.getViscosity())
                    .density(builder.getDensity())
                    .lightLevel(builder.getLightLevel());

            if (builder.getRarity() != null) {
                net.minecraft.world.item.Rarity rarity = switch (builder.getRarity().toUpperCase(java.util.Locale.ROOT)) {
                    case "UNCOMMON" -> net.minecraft.world.item.Rarity.UNCOMMON;
                    case "RARE" -> net.minecraft.world.item.Rarity.RARE;
                    case "EPIC" -> net.minecraft.world.item.Rarity.EPIC;
                    default -> net.minecraft.world.item.Rarity.COMMON;
                };
                props.rarity(rarity);
            }

            @SuppressWarnings("removal")
            FluidType type = new FluidType(props) {
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        @Override public ResourceLocation getStillTexture() { return stillTex; }
                        @Override public ResourceLocation getFlowingTexture() { return flowTex; }
                        @Override public int getTintColor() { return builder.getColor() | 0xFF000000; }
                    });
                }
            };
            return type;
        });
    }

    void ensureFluidsCreated(ResourceLocation rl, ResourceLocation flowRl, IFluidBuilder builder) {
        if (createdFluids.containsKey(rl) && createdFluids.containsKey(flowRl)) {
            return;
        }
        FluidType finalType = getOrCreateFluidType(rl, builder);

        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
                () -> finalType,
                () -> (FlowingFluid) createdFluids.get(rl),
                () -> (FlowingFluid) createdFluids.get(flowRl)
        )
        .slopeFindDistance(builder.getSlopeFindDistance())
        .levelDecreasePerBlock(builder.getLevelDecreasePerBlock())
        .tickRate(builder.getTickRate())
        .explosionResistance(builder.getExplosionResistance());

        ResourceLocation blockRl = locationParser.apply(builder.getId() + "_block");

        props.block(() -> {
            Block b = createdBlocks.get(blockRl);
            if (b instanceof LiquidBlock lb) { return lb; }
            Block alt = BuiltInRegistries.BLOCK.get(blockRl);
            if (alt instanceof LiquidBlock lb) { return lb; }
            return null;
        });

        ResourceLocation bucketRl = locationParser.apply(builder.getId() + "_bucket");
        ResourceLocation altBucketRl = locationParser.apply(builder.getId().replace("_fluid", "") + "_bucket");

        props.bucket(() -> {
            Item i = createdItems.get(bucketRl);
            if (i != null) { return i; }
            Item alt = createdItems.get(altBucketRl);
            if (alt != null) { return alt; }
            return Items.BUCKET;
        });

        Fluid stillFluid = new BaseFlowingFluid.Source(props);
        Fluid flowingFluid = new BaseFlowingFluid.Flowing(props);

        createdFluids.put(rl, stillFluid);
        createdFluids.put(flowRl, flowingFluid);
    }

    public void registerFluids(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            com.luatweaker.platform.util.CallbackFirewall.run(
                    () -> FluidRegisterActions.registerFluids(this, event, builder), LogStage.SYSTEM,
                    () -> "Failed to register fluid " + builder.getId());
        }
    }

    public void registerFluidBlocks(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            com.luatweaker.platform.util.CallbackFirewall.run(
                    () -> FluidRegisterActions.registerFluidBlocks(this, event, builder), LogStage.SYSTEM,
                    () -> "Failed to register fluid block " + builder.getId());
        }
    }

    public void registerFluidItems(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            com.luatweaker.platform.util.CallbackFirewall.run(
                    () -> FluidRegisterActions.registerFluidItems(this, event, builder), LogStage.SYSTEM,
                    () -> "Failed to register fluid bucket " + builder.getId());
        }
    }

    public void registerFluidTypes(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            com.luatweaker.platform.util.CallbackFirewall.run(
                    () -> FluidRegisterActions.registerFluidTypes(this, event, builder), LogStage.SYSTEM,
                    () -> "Failed to register fluid type " + builder.getId());
        }
    }
}

