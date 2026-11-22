package com.luatweaker.common.client;

import com.luatweaker.common.entity.LuaCustomMob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import dev.architectury.registry.client.level.entity.EntityModelLayerRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Texture-driven generic renderers for the humanoid/quadruped rigs. Both rigs
 * share one baked layer each (no per-entity layer spam); per-entity state is
 * only texture + render scale. Limb animation is procedural: HumanoidModel and
 * CowModel animate walk/idle from the vanilla walk animation, and attacks from
 * {@code swing()} / {@link LuaCustomMob#getAttackAnim(float)} — no keyframe
 * assets involved.
 */
public final class LuaMobRenderers {
    private LuaMobRenderers() {}

    public static final ModelLayerLocation HUMANOID_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("luatweaker", "custom_humanoid"), "main");
    public static final ModelLayerLocation QUADRUPED_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("luatweaker", "custom_quadruped"), "main");

    public static void registerLayerDefinitions() {
        Supplier<LayerDefinition> humanoid =
                () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
        EntityModelLayerRegistry.register(HUMANOID_LAYER, humanoid);
        EntityModelLayerRegistry.register(QUADRUPED_LAYER, CowModel::createBodyLayer);
    }

    public static HumanoidMobRenderer<LuaCustomMob, HumanoidModel<LuaCustomMob>> createHumanoid(
            EntityRendererProvider.Context ctx, ResourceLocation texture, float shadowRadius, double renderScale) {
        HumanoidModel<LuaCustomMob> model = new HumanoidModel<>(ctx.bakeLayer(HUMANOID_LAYER));
        return new HumanoidMobRenderer<>(ctx, model, shadowRadius) {
            @Override
            public ResourceLocation getTextureLocation(LuaCustomMob entity) {
                return texture;
            }

            @Override
            protected void scale(LuaCustomMob entity, PoseStack poseStack, float partialTick) {
                float s = (float) renderScale;
                poseStack.scale(s, s, s);
            }
        };
    }

    public static MobRenderer<LuaCustomMob, CowModel<LuaCustomMob>> createQuadruped(
            EntityRendererProvider.Context ctx, ResourceLocation texture, float shadowRadius, double renderScale) {
        CowModel<LuaCustomMob> model = new CowModel<>(ctx.bakeLayer(QUADRUPED_LAYER));
        return new MobRenderer<>(ctx, model, shadowRadius) {
            @Override
            public ResourceLocation getTextureLocation(LuaCustomMob entity) {
                return texture;
            }

            @Override
            protected void scale(LuaCustomMob entity, PoseStack poseStack, float partialTick) {
                float s = (float) renderScale;
                poseStack.scale(s, s, s);
            }
        };
    }
}
