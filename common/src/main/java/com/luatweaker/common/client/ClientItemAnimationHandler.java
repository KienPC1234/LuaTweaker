package com.luatweaker.common.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientItemAnimationHandler {
    private static final int FINAL_TICK = 1;
    private static final int DEFAULT_DURATION = 20;
    private static final float DEFAULT_SPEED = 1.0f;

    private ClientItemAnimationHandler() {
    }

    public record ActiveAnim(@NotNull String name, int totalTicks, int ticksRemaining, float speed) {}

    private static final Map<Integer, ActiveAnim> ACTIVE_ANIMS = new ConcurrentHashMap<>();

    public static void init() {
        ClientTickEvent.CLIENT_PRE.register(mc -> onClientTick());
    }

    public static void trigger(int entityId, @NotNull String name, int durationTicks, float speed) {
        int ticks = durationTicks <= 0 ? DEFAULT_DURATION : durationTicks;
        float finalSpeed = speed <= 0 ? DEFAULT_SPEED : speed;
        ACTIVE_ANIMS.put(entityId, new ActiveAnim(name.toLowerCase(Locale.ROOT), ticks, ticks, finalSpeed));
    }

    public static @Nullable ActiveAnim getAnimation(int entityId) {
        return ACTIVE_ANIMS.get(entityId);
    }

    public static void clear() {
        ACTIVE_ANIMS.clear();
    }

    public static void onClientTick() {
        if (ACTIVE_ANIMS.isEmpty()) {
            return;
        }
        for (Integer entityId : ACTIVE_ANIMS.keySet()) {
            ACTIVE_ANIMS.computeIfPresent(entityId, (ignored, anim) -> advanceAnimation(anim));
        }
    }

    private static @Nullable ActiveAnim advanceAnimation(@NotNull ActiveAnim anim) {
        if (anim.ticksRemaining() <= FINAL_TICK) {
            return null;
        }
        return new ActiveAnim(anim.name(), anim.totalTicks(), anim.ticksRemaining() - 1, anim.speed());
    }

    public static void onRenderHand(@NotNull PoseStack poseStack, float partialTicks) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ActiveAnim anim = ACTIVE_ANIMS.get(mc.player.getId());
        if (anim == null) {
            return;
        }

        float progress = 1.0f - (anim.ticksRemaining() - partialTicks) / anim.totalTicks();
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        float sinP = (float) Math.sin(progress * Math.PI);

        switch (anim.name()) {
            case "recoil" -> {
                poseStack.translate(0.0, 0.06 * sinP, 0.16 * sinP);
                poseStack.mulPose(Axis.XP.rotationDegrees(16.0f * sinP));
            }
            case "thrust" -> {
                poseStack.translate(0.0, 0.02 * sinP, 0.25 * sinP);
                poseStack.mulPose(Axis.YP.rotationDegrees(8.0f * sinP));
            }
            case "spin", "cast" -> {
                poseStack.mulPose(Axis.ZP.rotationDegrees(360.0f * progress));
            }
            case "inspect" -> {
                poseStack.translate(0.08 * sinP, 0.05 * sinP, 0.05 * sinP);
                poseStack.mulPose(Axis.YP.rotationDegrees(25.0f * sinP));
                poseStack.mulPose(Axis.ZP.rotationDegrees(10.0f * sinP));
            }
            case "slash" -> {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0f * sinP));
                poseStack.mulPose(Axis.XP.rotationDegrees(30.0f * sinP));
                poseStack.translate(0.15 * sinP, -0.1 * sinP, 0.1 * sinP);
            }
            default -> {}
        }
    }
}
