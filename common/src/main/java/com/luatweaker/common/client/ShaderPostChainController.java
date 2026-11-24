package com.luatweaker.common.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.mixin.GameRendererAccessor;
import com.luatweaker.common.mixin.PostChainAccessor;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.AbstractUniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the active Lua post-processing {@link PostChain} and uploads engine
 * globals ({@code ScreenSize}, {@code Time}) plus user scalar/vector uniforms
 * directly onto each pass's live {@link EffectInstance}.
 */
final class ShaderPostChainController {

    private static final long ORIGIN_NANOS = System.nanoTime();
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final AtomicReference<String> expectedChain = new AtomicReference<>();
    private final Map<String, float[]> activeUniforms = new ConcurrentHashMap<>();

    void activate(@NotNull String chainLocation) {
        ResourceLocation location = ResourceLocation.tryParse(chainLocation);
        if (location == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] refusing to load malformed chain location '" + chainLocation + "'.");
            return;
        }
        Minecraft.getInstance().execute(() -> loadOnRenderThread(chainLocation, location));
        Minecraft.getInstance().execute(() -> {
            if (isChainLive(chainLocation)) {
                LuaTweakerLog.get().debug(LogStage.SYSTEM, "[Shaders] pass '" + chainLocation + "' is live.");
            } else {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Shaders] pass '" + chainLocation + "' failed to activate - the driver"
                                + " rejected the chain. Check log output above for GLSL compile errors.");
            }
        });
    }

    void deactivate() {
        expectedChain.set(null);
        activeUniforms.clear();
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gameRenderer != null) {
                Minecraft.getInstance().gameRenderer.shutdownEffect();
            }
        });
    }

    void setFloat(@Nullable String uniformName, float value) {
        if (uniformName == null || uniformName.isBlank()) {
            return;
        }
        float[] vec = { value };
        activeUniforms.put(uniformName, vec);
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gameRenderer != null) {
                applyUniformOnChain(Minecraft.getInstance().gameRenderer.currentEffect(), uniformName, vec);
            }
        });
    }

    void setFloatVec(@Nullable String uniformName, float @Nullable ... values) {
        if (uniformName == null || uniformName.isBlank() || values == null) {
            return;
        }
        float[] copy = values.clone();
        activeUniforms.put(uniformName, copy);
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gameRenderer != null) {
                applyUniformOnChain(Minecraft.getInstance().gameRenderer.currentEffect(), uniformName, copy);
            }
        });
    }

    void prepareChain(@Nullable PostChain chain) {
        String expected = expectedChain.get();
        if (chain == null || expected == null || !expected.equals(chain.getName())) {
            return;
        }
        Window window = Minecraft.getInstance().getWindow();
        float width = window != null ? Math.max(1.0f, (float) window.getWidth()) : 1.0f;
        float height = window != null ? Math.max(1.0f, (float) window.getHeight()) : 1.0f;
        float elapsedSeconds = (float) ((System.nanoTime() - ORIGIN_NANOS) / NANOS_PER_SECOND);

        ((PostChainAccessor) chain).getPasses()
                .forEach(pass -> prepareEffect(pass.getEffect(), width, height, elapsedSeconds));
    }

    private void prepareEffect(EffectInstance effect, float width, float height, float elapsedSeconds) {
        effect.safeGetUniform("ScreenSize").set(width, height);
        effect.safeGetUniform("Time").set(elapsedSeconds);
        for (Map.Entry<String, float[]> entry : activeUniforms.entrySet()) {
            writeUniform(effect, entry.getKey(), entry.getValue());
        }
    }

    private void loadOnRenderThread(String chainLocation, ResourceLocation location) {
        if (Minecraft.getInstance().gameRenderer == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] game renderer not ready; pass '" + chainLocation + "' not loaded.");
            return;
        }
        String previous = expectedChain.getAndSet(chainLocation);
        if (!chainLocation.equals(previous)) {
            activeUniforms.clear();
        }
        if (!isChainLive(chainLocation)) {
            ((GameRendererAccessor) Minecraft.getInstance().gameRenderer).callLoadEffect(location);
        }
        prepareChain(Minecraft.getInstance().gameRenderer.currentEffect());
    }

    private void applyUniformOnChain(@Nullable PostChain chain, String name, float... values) {
        String expected = expectedChain.get();
        if (chain == null || expected == null || !expected.equals(chain.getName())) {
            return;
        }
        ((PostChainAccessor) chain).getPasses()
                .forEach(pass -> writeUniform(pass.getEffect(), name, values));
    }

    private static boolean isChainLive(String chainLocation) {
        return Minecraft.getInstance().gameRenderer != null
                && Minecraft.getInstance().gameRenderer.currentEffect() != null
                && chainLocation.equals(Minecraft.getInstance().gameRenderer.currentEffect().getName());
    }

    private static void writeUniform(EffectInstance effect, String name, float... values) {
        AbstractUniform uniform = effect.safeGetUniform(name);
        try {
            switch (values.length) {
                case 1 -> uniform.set(values[0]);
                case 2 -> uniform.set(values[0], values[1]);
                case 3 -> uniform.set(values[0], values[1], values[2]);
                case 4 -> uniform.set(values[0], values[1], values[2], values[3]);
                default -> uniform.set(values);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] uniform '" + name + "' upload failed: " + e.getMessage());
        }
    }
}
