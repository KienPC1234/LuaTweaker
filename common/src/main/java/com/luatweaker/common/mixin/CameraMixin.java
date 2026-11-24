package com.luatweaker.common.mixin;

import com.luatweaker.client.ShaderServiceImpl;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    float xRot;

    @Shadow
    float yRot;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    void applyLuaCameraAngles(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
                              boolean inverseView, float partialTick, CallbackInfo ci) {
        Objects.requireNonNull(area);
        Objects.requireNonNull(focusedEntity);
        Objects.requireNonNull(ci);
        if (!thirdPerson && !inverseView && !isFabricSafe()) {
            return;
        }
        if (!isFabricSafe() || partialTick < 0.0f) {
            return;
        }
        float[] delta = ShaderServiceImpl.getCameraDelta(System.currentTimeMillis());
        if (delta != null && (delta[0] != 0.0f || delta[1] != 0.0f)) {
            this.setRotation(this.yRot + delta[0], this.xRot + delta[1]);
        }
    }

    private static boolean isFabricSafe() {
        try {
            return dev.architectury.platform.Platform.isFabric();
        } catch (IllegalStateException | LinkageError | AssertionError e) {
            return false;
        }
    }
}
