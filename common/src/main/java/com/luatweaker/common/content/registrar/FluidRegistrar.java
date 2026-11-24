package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.util.CallbackFirewall;
import dev.architectury.core.block.ArchitecturyLiquidBlock;
import dev.architectury.core.fluid.ArchitecturyFlowingFluid;
import dev.architectury.core.fluid.ArchitecturyFluidAttributes;
import dev.architectury.core.item.ArchitecturyBucketItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Cross-loader custom fluid registrar powered by Architectury Fluid API
 * ({@link ArchitecturyFlowingFluid}, {@link ArchitecturyLiquidBlock}, {@link ArchitecturyBucketItem}).
 */
public final class FluidRegistrar {

    record RegisteredFluidEntry(
            @NotNull IFluidBuilder builder,
            @NotNull RegistrySupplier<FlowingFluid> still,
            @NotNull RegistrySupplier<FlowingFluid> flowing,
            @NotNull RegistrySupplier<Item> bucket) {
    }

    private static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(LuaTweakerCommon.MOD_ID, Registries.FLUID);
    private static final DeferredRegister<Block> FLUID_BLOCKS =
            DeferredRegister.create(LuaTweakerCommon.MOD_ID, Registries.BLOCK);
    private static final DeferredRegister<Item> FLUID_ITEMS =
            DeferredRegister.create(LuaTweakerCommon.MOD_ID, Registries.ITEM);

    private static final List<RegisteredFluidEntry> ENTRIES = new CopyOnWriteArrayList<>();

    private final IContentService contentService;
    private final Map<ResourceLocation, Item> createdItems;
    private final Map<ResourceLocation, Block> createdBlocks;
    private final Function<String, ResourceLocation> locationParser;

    public FluidRegistrar(@NotNull IContentService contentService,
                          @NotNull Map<ResourceLocation, Item> createdItems,
                          @NotNull Map<ResourceLocation, Block> createdBlocks,
                          @NotNull Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdItems = createdItems;
        this.createdBlocks = createdBlocks;
        this.locationParser = locationParser;
    }

    public void registerFluids() {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            CallbackFirewall.run(() -> queueFluid(builder), LogStage.SYSTEM,
                    () -> "Failed to queue custom fluid " + builder.getId());
        }
        FLUIDS.register();
        FLUID_BLOCKS.register();
        FLUID_ITEMS.register();
    }

    public static void registerClientVisuals() {
        FluidClientVisuals.register(ENTRIES);
    }

    private void queueFluid(@NotNull IFluidBuilder builder) {
        ResourceLocation stillId = locationParser.apply(builder.getId());
        String basePath = stillId.getPath();
        String flowingPath = "flowing_" + basePath;
        String blockPath = basePath + "_block";
        String bucketPath = basePath + "_bucket";
        ResourceLocation blockId = ResourceLocation.fromNamespaceAndPath(stillId.getNamespace(), blockPath);
        ResourceLocation bucketId = ResourceLocation.fromNamespaceAndPath(stillId.getNamespace(), bucketPath);

        AtomicReference<RegistrySupplier<FlowingFluid>> stillRef = new AtomicReference<>();
        AtomicReference<RegistrySupplier<FlowingFluid>> flowRef = new AtomicReference<>();
        AtomicReference<RegistrySupplier<LiquidBlock>> blockRef = new AtomicReference<>();
        AtomicReference<RegistrySupplier<Item>> bucketRef = new AtomicReference<>();

        ArchitecturyFluidAttributes attributes = FluidAttributesFactory.build(
                builder, locationParser, stillRef, flowRef, blockRef, bucketRef);

        RegistrySupplier<FlowingFluid> stillSupplier = FLUIDS.register(basePath,
                () -> new ArchitecturyFlowingFluid.Source(attributes));
        RegistrySupplier<FlowingFluid> flowSupplier = FLUIDS.register(flowingPath,
                () -> new ArchitecturyFlowingFluid.Flowing(attributes));
        stillRef.set(stillSupplier);
        flowRef.set(flowSupplier);

        RegistrySupplier<LiquidBlock> blockSupplier = FLUID_BLOCKS.register(blockPath, () -> {
            LiquidBlock block = new ArchitecturyLiquidBlock(stillSupplier,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)
                            .lightLevel(state -> builder.getLightLevel()));
            createdBlocks.put(blockId, block);
            return block;
        });
        blockRef.set(blockSupplier);

        RegistrySupplier<Item> bucketSupplier = FLUID_ITEMS.register(bucketPath, () -> {
            Item bucket = new ArchitecturyBucketItem(stillSupplier,
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)
                            .rarity(FluidAttributesFactory.parseRarity(builder.getRarity())));
            createdItems.put(bucketId, bucket);
            return bucket;
        });
        bucketRef.set(bucketSupplier);

        ENTRIES.add(new RegisteredFluidEntry(builder, stillSupplier, flowSupplier, bucketSupplier));
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Fluid: " + stillId);
    }
}
