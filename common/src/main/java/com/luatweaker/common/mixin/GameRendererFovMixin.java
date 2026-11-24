package com.luatweaker.common.mixin;

import com.luatweaker.client.ShaderServiceImpl;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(GameRenderer.class)
public class GameRendererFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    void applyLuaFovDelta(Camera activeRenderInfo, float partialTicks, boolean useFOVSetting,
                          CallbackInfoReturnable<Double> cir) {
        Objects.requireNonNull(activeRenderInfo);
        Objects.requireNonNull(cir);
        if (!useFOVSetting && partialTicks < 0.0f) {
            return;
        }
        if (!isFabricSafe()) {
            return;
        }
        float[] delta = ShaderServiceImpl.getCameraDelta(System.currentTimeMillis());
        if (delta != null && delta[3] != 0.0f) {
            cir.setReturnValue(cir.getReturnValueD() + delta[3]);
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
