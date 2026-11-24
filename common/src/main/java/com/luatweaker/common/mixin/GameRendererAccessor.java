package com.luatweaker.common.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@FunctionalInterface
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    /**
     * Must NOT be named {@code loadEffect}: an invoker with the same name as its
     * target generates a bridge that calls itself, causing infinite recursion
     * (StackOverflowError) the first time a shader pass is activated.
     */
    @Invoker("loadEffect")
    void callLoadEffect(ResourceLocation location);
}
