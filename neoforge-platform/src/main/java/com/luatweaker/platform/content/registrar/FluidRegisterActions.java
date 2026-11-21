package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import com.luatweaker.platform.util.CallbackFirewall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Per-builder fluid registration steps (kept out of {@link FluidRegistrar}'s loops). */
final class FluidRegisterActions {

    private FluidRegisterActions() {
    }

    static void registerFluids(FluidRegistrar registrar, RegisterEvent event, IFluidBuilder builder) {
        ResourceLocation id = registrar.locationParser.apply(builder.getId());
        ResourceLocation flowingId = registrar.locationParser.apply(builder.getId() + "_flowing");
        registrar.ensureFluidsCreated(id, flowingId, builder);
        Fluid stillFluid = registrar.createdFluids.get(id);
        Fluid flowingFluid = registrar.createdFluids.get(flowingId);
        event.register(Registries.FLUID, id, () -> stillFluid);
        event.register(Registries.FLUID, flowingId, () -> flowingFluid);
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Fluid: " + id);
    }

    static void registerFluidBlocks(FluidRegistrar registrar, RegisterEvent event, IFluidBuilder builder) {
        ResourceLocation id = registrar.locationParser.apply(builder.getId());
        ResourceLocation flowingId = registrar.locationParser.apply(builder.getId() + "_flowing");
        ResourceLocation blockId = registrar.locationParser.apply(builder.getId() + "_block");
        registrar.ensureFluidsCreated(id, flowingId, builder);
        FlowingFluid fluid = (FlowingFluid) registrar.createdFluids.get(id);
        LiquidBlock block = new LiquidBlock(
                fluid != null ? fluid : net.minecraft.world.level.material.Fluids.WATER,
                BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WATER)) {
            @Override
            public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
                super.entityInside(state, level, pos, entity);
                if (builder.getTouchHandler() != null && !level.isClientSide() && entity instanceof Player player) {
                    CallbackFirewall.run(() -> builder.getTouchHandler().accept(new NeoForgePlayerWrapper(player)),
                            LogStage.SYSTEM,
                            () -> "Failed fluid onTouch handler for " + builder.getId());
                }
            }
        };
        event.register(Registries.BLOCK, blockId, () -> block);
        registrar.createdBlocks.put(blockId, block);
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom LiquidBlock: " + blockId);
    }

    static void registerFluidItems(FluidRegistrar registrar, RegisterEvent event, IFluidBuilder builder) {
        ResourceLocation id = registrar.locationParser.apply(builder.getId());
        ResourceLocation flowingId = registrar.locationParser.apply(builder.getId() + "_flowing");
        ResourceLocation bucketId = registrar.locationParser.apply(builder.getId() + "_bucket");
        registrar.ensureFluidsCreated(id, flowingId, builder);
        Fluid stillFluid = registrar.createdFluids.get(id);
        BucketItem bucket = new BucketItem(
                stillFluid != null ? stillFluid : net.minecraft.world.level.material.Fluids.WATER,
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));
        event.register(Registries.ITEM, bucketId, () -> bucket);
        registrar.createdItems.put(bucketId, bucket);
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BucketItem: " + bucketId);
    }

    static void registerFluidTypes(FluidRegistrar registrar, RegisterEvent event, IFluidBuilder builder) {
        ResourceLocation id = registrar.locationParser.apply(builder.getId());
        FluidType type = registrar.getOrCreateFluidType(id, builder);
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES, id, () -> type);
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom FluidType: " + id);
    }
}


