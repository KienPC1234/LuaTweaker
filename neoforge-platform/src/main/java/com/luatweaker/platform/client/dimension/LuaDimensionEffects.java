package com.luatweaker.platform.client.dimension;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;

/**
 * Custom sky for Lua dimensions: renders a colored sky disc using the same
 * technique as the vanilla 1.21 sky disc, colored with the dimension's
 * {@code skyColor} config (resolved per dimension at render time).
 *
 * <p>Registered once under {@code luatweaker:lua}; every Lua dimension
 * references it via its dimension type JSON {@code effects} field.</p>
 */
public class LuaDimensionEffects extends DimensionSpecialEffects {

    public static final ResourceLocation EFFECTS_ID = ResourceLocation.fromNamespaceAndPath("luatweaker", "lua");

    private static final float DISC_RADIUS = 512.0F;
    private static final int DISC_SEGMENTS = 32;
    /** Vanilla overworld cloud layer height (blocks above the surface). */
    private static final float CLOUD_HEIGHT = 192.0F;

    public LuaDimensionEffects() {
        super(CLOUD_HEIGHT, true, SkyType.NONE, false, false);
    }

    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EFFECTS_ID, new LuaDimensionEffects());
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Registered custom dimension sky effects under '" + EFFECTS_ID + "'");
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float distance) {
        // Fog colors come from the biome effects (datapack); keep them as-is.
        return fogColor;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f poseMatrix,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        setupFog.run();

        int rgb = skyColorFor(level);
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        // Flat disc centered on the camera (rotation-only pose), like the
        // vanilla sky disc: it forms the horizon band; the sky above the
        // horizon is the biome fog color.
        buffer.addVertex(poseMatrix, 0.0F, 0.0F, 0.0F).setColor(r, g, b, 1.0F);
        for (int i = 0; i <= DISC_SEGMENTS; i++) {
            double angle = i / (double) DISC_SEGMENTS * 2.0 * Math.PI;
            buffer.addVertex(poseMatrix,
                    (float) (Math.cos(angle) * DISC_RADIUS), 0.0F, (float) (Math.sin(angle) * DISC_RADIUS))
                    .setColor(r, g, b, 1.0F);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return true;
    }

    private static int skyColorFor(ClientLevel level) {
        ResourceLocation dimensionId = level.dimension().location();
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        if (service instanceof DimensionServiceImpl dim) {
            DimensionConfig config = dim.getConfig(dimensionId.toString());
            if (config != null) {
                return config.skyColor();
            }
        }
        return DimensionConfig.DEFAULT_SKY_COLOR;
    }
}
