package com.luatweaker.common.client;

import com.luatweaker.api.content.IBlockRenderer;
import com.luatweaker.common.container.CustomContainerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;

public class MinecraftBlockRenderer implements IBlockRenderer {
    private final CustomContainerBlockEntity blockEntity;
    private final PoseStack poseStack;
    private final MultiBufferSource bufferSource;
    private final int packedLight;
    private final int packedOverlay;
    private final BlockEntityRendererProvider.Context context;
    private final float partialTick;

    public MinecraftBlockRenderer(CustomContainerBlockEntity blockEntity, PoseStack poseStack,
                                MultiBufferSource bufferSource, int packedLight, int packedOverlay,
                                BlockEntityRendererProvider.Context context, float partialTick) {
        this.blockEntity = blockEntity;
        this.poseStack = poseStack;
        this.bufferSource = bufferSource;
        this.packedLight = packedLight;
        this.packedOverlay = packedOverlay;
        this.context = context;
        this.partialTick = partialTick;
    }

    @Override
    public void pushPose() {
        poseStack.pushPose();
    }

    @Override
    public void popPose() {
        poseStack.popPose();
    }

    @Override
    public void translate(double x, double y, double z) {
        poseStack.translate(x, y, z);
    }

    @Override
    public void rotate(float degrees, float axisX, float axisY, float axisZ) {
        float rad = (float) Math.toRadians(degrees);
        poseStack.mulPose(new Quaternionf().rotationAxis(rad, axisX, axisY, axisZ));
    }

    @Override
    public void scale(float x, float y, float z) {
        poseStack.scale(x, y, z);
    }

    @Override
    public void renderItem(String itemId, int count, int light) {
        if (itemId == null || itemId.isBlank()) { return; }
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) { return; }
        var item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == net.minecraft.world.item.Items.AIR) { return; }
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        int l = light > 0 ? light : packedLight;
        context.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, l, packedOverlay,
                poseStack, bufferSource, blockEntity.getLevel(), 0);
    }

    @Override
    public void renderItemFromSlot(int slotIndex, int light) {
        if (slotIndex < 0 || slotIndex >= blockEntity.getContainerSize()) { return; }
        ItemStack stack = blockEntity.getItem(slotIndex);
        if (stack.isEmpty()) { return; }
        int l = light > 0 ? light : packedLight;
        context.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, l, packedOverlay,
                poseStack, bufferSource, blockEntity.getLevel(), 0);
    }

    @Override
    public void renderBlock(String blockId, int light) {
        if (blockId == null || blockId.isBlank()) { return; }
        ResourceLocation rl = ResourceLocation.tryParse(blockId);
        if (rl == null) { return; }
        Block block = BuiltInRegistries.BLOCK.get(rl);
        if (block == null || block == Blocks.AIR) { return; }
        int l = light > 0 ? light : packedLight;
        context.getBlockRenderDispatcher().renderSingleBlock(block.defaultBlockState(),
                poseStack, bufferSource, l, packedOverlay);
    }

    @Override
    public int getLight() {
        return packedLight;
    }

    @Override
    public double getTime() {
        if (blockEntity.getLevel() != null) {
            double gameTime = blockEntity.getLevel().getGameTime();
            return gameTime + partialTick;
        }
        return System.currentTimeMillis() / 50.0;
    }
}
