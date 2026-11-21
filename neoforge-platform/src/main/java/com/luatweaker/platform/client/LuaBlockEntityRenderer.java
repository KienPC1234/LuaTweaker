package com.luatweaker.platform.client;

import com.luatweaker.api.content.IBlockRenderer;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.platform.LuaTweakerMod;
import com.luatweaker.platform.container.CustomContainerBlock;
import com.luatweaker.platform.container.CustomContainerBlockEntity;
import com.luatweaker.platform.container.CustomContainerRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.NotNull;
import org.luava.runtime.LuaException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Client-side block entity renderer for Lua {@code :OnRender} callbacks.
 *
 * <p>Resolves the handler through the live block spec on every frame so {@code /lt reload}
 * replaces the Lua closure instead of keeping the one captured at registration.</p>
 */
public class LuaBlockEntityRenderer implements BlockEntityRenderer<CustomContainerBlockEntity> {
    private final BlockEntityType<?> type;
    private final BlockEntityRendererProvider.Context context;

    public LuaBlockEntityRenderer(BlockEntityType<?> type, BlockEntityRendererProvider.Context context) {
        this.type = type;
        this.context = context;
    }

    @Override
    public void render(CustomContainerBlockEntity be, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        CustomContainerBlock block = CustomContainerRegistry.BLOCK_BY_TYPE.get(type);
        BiConsumer<Object, Object> renderHandler = block == null ? null : block.getSpec().renderHandler();
        if (renderHandler == null) { return; }
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) { return; }

        NeoForgeBlockRenderer renderer = new NeoForgeBlockRenderer(be, poseStack, bufferSource, packedLight, packedOverlay, context, partialTick);
        ILuaTable rendererTable = LuaBinder.bindTable(engine, renderer, IBlockRenderer.class);

        try {
            renderHandler.accept(rendererTable, renderData(be, partialTick));
        } catch (LuaException | IllegalStateException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM, "Error executing BlockEntityRenderer: " + e.getMessage());
        }
    }

    /**
     * Builds the data table handed to the Lua render callback.
     *
     * <p>Insertion-ordered so a script that dumps the table sees the same key order every frame.
     * The map is a per-call local, never shared, so it is deliberately not concurrent.</p>
     */
    private static @NotNull Map<String, Object> renderData(@NotNull CustomContainerBlockEntity be, float partialTick) {
        Map<String, Object> data;
        data = new LinkedHashMap<>();
        BlockPos pos = be.getBlockPos();
        data.put("X", pos.getX());
        data.put("Y", pos.getY());
        data.put("Z", pos.getZ());
        data.put("Energy", be.getEnergyStored());
        data.put("FluidId", be.getFluidId());
        data.put("FluidAmount", be.getFluidAmount());
        data.put("Progress", be.getProgress());
        data.put("PartialTicks", (double) partialTick);
        return data;
    }
}
