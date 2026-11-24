package com.luatweaker.common.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    private static final int FULL_BRIGHT = 15_728_880;

    @Shadow
    public abstract <T extends Entity> @Nullable EntityRenderer<? super T> getRenderer(@NotNull T entity);

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    <E extends Entity> void onShouldRender(E entity, Frustum frustum, double camX, double camY, double camZ,
                                           CallbackInfoReturnable<Boolean> cir) {
        EntityRenderer<? super E> renderer = this.getRenderer(entity);
        if (renderer == null) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(renderer.shouldRender(entity, frustum, camX, camY, camZ));
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    <E extends Entity> void onRender(E entity, double x, double y, double z, float rotationYaw,
                                     float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                                     int packedLight, CallbackInfo ci) {
        if (this.getRenderer(entity) == null) {
            ci.cancel();
        }
    }

    @Inject(method = "getPackedLightCoords", at = @At("HEAD"), cancellable = true)
    <E extends Entity> void onGetPackedLightCoords(E entity, float partialTicks,
                                                   CallbackInfoReturnable<Integer> cir) {
        if (this.getRenderer(entity) == null) {
            cir.setReturnValue(FULL_BRIGHT);
        }
    }
}
