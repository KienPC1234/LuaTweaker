package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FluidRegistrar {
    private final IContentService contentService;
    private final Map<ResourceLocation, Fluid> createdFluids;
    private final Map<ResourceLocation, Item> createdItems;
    private final Map<ResourceLocation, Block> createdBlocks;
    private final Map<ResourceLocation, FluidType> createdFluidTypes = new ConcurrentHashMap<>();
    private final Function<String, ResourceLocation> locationParser;

    public FluidRegistrar(IContentService contentService, Map<ResourceLocation, Fluid> createdFluids, Map<ResourceLocation, Item> createdItems, Map<ResourceLocation, Block> createdBlocks, Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdFluids = createdFluids;
        this.createdItems = createdItems;
        this.createdBlocks = createdBlocks;
        this.locationParser = locationParser;
    }

    private FluidType getOrCreateFluidType(ResourceLocation rl, IFluidBuilder builder) {
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
                net.minecraft.world.item.Rarity rarity = switch (builder.getRarity().toUpperCase()) {
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

    public void registerFluids(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            try {
                ResourceLocation rl = locationParser.apply(builder.getId());
                ResourceLocation flowRl = locationParser.apply(builder.getId() + "_flowing");

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
                    if (b instanceof LiquidBlock lb) return lb;
                    Block alt = BuiltInRegistries.BLOCK.get(blockRl);
                    if (alt instanceof LiquidBlock lb) return lb;
                    return null;
                });

                ResourceLocation bucketRl = locationParser.apply(builder.getId() + "_bucket");
                ResourceLocation altBucketRl = locationParser.apply(builder.getId().replace("_fluid", "") + "_bucket");

                props.bucket(() -> {
                    Item i = createdItems.get(bucketRl);
                    if (i != null) return i;
                    Item alt = createdItems.get(altBucketRl);
                    if (alt != null) return alt;
                    return Items.BUCKET;
                });

                Fluid stillFluid = new BaseFlowingFluid.Source(props);
                Fluid flowingFluid = new BaseFlowingFluid.Flowing(props);

                createdFluids.put(rl, stillFluid);
                createdFluids.put(flowRl, flowingFluid);

                event.register(Registries.FLUID, rl, () -> stillFluid);
                event.register(Registries.FLUID, flowRl, () -> flowingFluid);

                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Fluid: " + rl);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register fluid " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public void registerFluidBlocks(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            try {
                ResourceLocation rl = locationParser.apply(builder.getId());
                ResourceLocation blockRl = locationParser.apply(builder.getId() + "_block");

                FlowingFluid fluid = (FlowingFluid) createdFluids.get(rl);

                LiquidBlock block = new LiquidBlock(
                        fluid != null ? fluid : (FlowingFluid) net.minecraft.world.level.material.Fluids.WATER,
                        BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WATER)
                ) {
                    @Override
                    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
                        super.entityInside(state, level, pos, entity);
                        if (builder.getTouchHandler() != null && !level.isClientSide() && entity instanceof Player player) {
                            try {
                                builder.getTouchHandler().accept(new NeoForgePlayerWrapper(player));
                            } catch (Exception e) {
                                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed fluid onTouch handler for " + builder.getId() + ": " + e.getMessage());
                            }
                        }
                    }
                };

                event.register(Registries.BLOCK, blockRl, () -> block);
                createdBlocks.put(blockRl, block);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom LiquidBlock: " + blockRl);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register fluid block " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public void registerFluidItems(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            try {
                ResourceLocation rl = locationParser.apply(builder.getId());
                ResourceLocation bucketRl = locationParser.apply(builder.getId() + "_bucket");
                ResourceLocation altBucketRl = locationParser.apply(builder.getId().replace("_fluid", "") + "_bucket");

                Fluid stillFluid = createdFluids.get(rl);

                BucketItem bucketItem = new BucketItem(
                        stillFluid != null ? stillFluid : net.minecraft.world.level.material.Fluids.WATER,
                        new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)
                );

                event.register(Registries.ITEM, bucketRl, () -> bucketItem);
                createdItems.put(bucketRl, bucketItem);

                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BucketItem: " + bucketRl);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register fluid bucket " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public void registerFluidTypes(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            try {
                ResourceLocation rl = locationParser.apply(builder.getId());
                FluidType type = getOrCreateFluidType(rl, builder);
                event.register(NeoForgeRegistries.Keys.FLUID_TYPES, rl, () -> type);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom FluidType: " + rl);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register fluid type " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public void registerClientExtensions(RegisterClientExtensionsEvent event) {
        // FluidType.initializeClient already registers IClientFluidTypeExtensions during creation
    }

    public void registerItemColors(RegisterColorHandlersEvent.Item event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            ResourceLocation bucketRl = locationParser.apply(builder.getId() + "_bucket");
            Item bucketItem = createdItems.get(bucketRl);
            if (bucketItem != null) {
                event.register(new DynamicFluidContainerModel.Colors(), bucketItem);
            }
            ResourceLocation altBucketRl = locationParser.apply(builder.getId().replace("_fluid", "") + "_bucket");
            Item altBucketItem = createdItems.get(altBucketRl);
            if (altBucketItem != null && altBucketItem != bucketItem) {
                event.register(new DynamicFluidContainerModel.Colors(), altBucketItem);
            }
        }
    }
}
