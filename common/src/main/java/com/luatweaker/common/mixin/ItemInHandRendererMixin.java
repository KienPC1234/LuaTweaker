package com.luatweaker.common.mixin;

import com.luatweaker.common.client.ClientItemAnimationHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @Inject(
            method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)
    )
    private void onRenderArmWithItem(AbstractClientPlayer player, float partialTicks, float pitch,
                                     InteractionHand hand, float swingProgress, ItemStack stack,
                                     float equippedProgress, PoseStack poseStack,
                                     MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {
        if (hand == InteractionHand.MAIN_HAND) {
            ClientItemAnimationHandler.onRenderHand(poseStack, partialTicks);
        }
    }
}
