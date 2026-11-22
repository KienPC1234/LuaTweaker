package com.luatweaker.common.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@FunctionalInterface
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("loadEffect")
    void loadEffect(ResourceLocation location);
}
