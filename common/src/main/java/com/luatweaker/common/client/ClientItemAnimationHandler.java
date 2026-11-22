package com.luatweaker.common.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientItemAnimationHandler {
    private static final int FINAL_TICK = 1;

    private ClientItemAnimationHandler() {
    }

    public record ActiveAnim(String name, int totalTicks, int ticksRemaining, float speed) {}

    private static final Map<Integer, ActiveAnim> ACTIVE_ANIMS = new ConcurrentHashMap<>();

    public static void init() {
        ClientTickEvent.CLIENT_PRE.register(mc -> onClientTick());
    }

    public static void trigger(int entityId, String name, int durationTicks, float speed) {
        int ticks = durationTicks <= 0 ? 20 : durationTicks;
        ACTIVE_ANIMS.put(entityId, new ActiveAnim(name.toLowerCase(java.util.Locale.ROOT), ticks, ticks, speed <= 0 ? 1.0f : speed));
    }

    public static void onClientTick() {
        if (ACTIVE_ANIMS.isEmpty()) { return; }
        ACTIVE_ANIMS.entrySet().removeIf(entry -> {
            ActiveAnim a = entry.getValue();
            if (a.ticksRemaining() <= FINAL_TICK) { return true; }
            entry.setValue(new ActiveAnim(a.name(), a.totalTicks(), a.ticksRemaining() - 1, a.speed()));
            return false;
        });
    }

    public static void onRenderHand(PoseStack poseStack, float partialTicks) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) { return; }
        ActiveAnim anim = ACTIVE_ANIMS.get(mc.player.getId());
        if (anim == null) { return; }

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
