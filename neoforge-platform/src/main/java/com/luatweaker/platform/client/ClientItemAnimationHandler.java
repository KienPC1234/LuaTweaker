package com.luatweaker.platform.client;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import com.luatweaker.platform.LuaTweakerMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = LuaTweakerMod.MODID, value = Dist.CLIENT)
public final class ClientItemAnimationHandler {
    private static final int FINAL_TICK = 1;


    private ClientItemAnimationHandler() {
    }

    public record ActiveAnim(String name, int totalTicks, int ticksRemaining, float speed) {}

    private static final Map<Integer, ActiveAnim> ACTIVE_ANIMS = new ConcurrentHashMap<>();

    public static void trigger(int entityId, String name, int durationTicks, float speed) {
        int ticks = durationTicks <= 0 ? 20 : durationTicks;
        ACTIVE_ANIMS.put(entityId, new ActiveAnim(name.toLowerCase(java.util.Locale.ROOT), ticks, ticks, speed <= 0 ? 1.0f : speed));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (ACTIVE_ANIMS.isEmpty()) { return; }
        ACTIVE_ANIMS.entrySet().removeIf(entry -> {
            ActiveAnim a = entry.getValue();
            if (a.ticksRemaining() <= FINAL_TICK) { return true; }
            entry.setValue(new ActiveAnim(a.name(), a.totalTicks(), a.ticksRemaining() - 1, a.speed()));
            return false;
        });
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) { return; }
        ActiveAnim anim = ACTIVE_ANIMS.get(mc.player.getId());
        if (anim == null) { return; }

        float partialTicks = event.getPartialTick();
        float progress = 1.0f - (anim.ticksRemaining() - partialTicks) / anim.totalTicks();
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        var poseStack = event.getPoseStack();
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
                poseStack.mulPose(Axis.YP.rotationDegrees(-35.0f * sinP));
                poseStack.mulPose(Axis.XP.rotationDegrees(20.0f * sinP));
            }
            default -> {
                poseStack.translate(0.0, -0.04 * sinP, -0.08 * sinP);
            }
        }
    }
}
