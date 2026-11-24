package com.luatweaker.common.mixin;

import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.common.client.MinecraftClientPlatform;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(PostChain.class)
public class PostChainMixin {

    @Inject(method = "process", at = @At("HEAD"))
    void syncLuaShaderUniformsBeforeProcess(float partialTicks, CallbackInfo ci) {
        Objects.requireNonNull(ci);
        if (partialTicks < -1.0f || !Platform.isInitialized()) {
            return;
        }
        IPlatformClient client = Platform.getClientOrNull();
        if (client instanceof MinecraftClientPlatform platform) {
            platform.prepareShaderPostChain((PostChain) (Object) this);
        }
    }
}
