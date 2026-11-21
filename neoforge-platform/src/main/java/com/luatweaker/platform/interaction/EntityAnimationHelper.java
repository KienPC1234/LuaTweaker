package com.luatweaker.platform.interaction;

import com.luatweaker.api.entity.IEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/** Geo/vanilla animation dispatch for {@code Entity:PlayAnimation}. */
public final class EntityAnimationHelper {

    private static final String ANIMATION_SWING = "swing";
    private static final String ANIMATION_OFFHAND_SWING = "offhand_swing";
    private static final double DEFAULT_ANIMATION_TRANSITION = 20.0;
    private static final double DEFAULT_ANIMATION_SPEED = 1.0;

    private EntityAnimationHelper() {
    }

    public static void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed, double transition) {
        com.luatweaker.api.pal.Platform.getInteraction().triggerGeoAnimation(entity, null, animName, speed, false);
        if (!(entity.getRawEntity() instanceof LivingEntity living)) {
            return;
        }
        if (ANIMATION_SWING.equalsIgnoreCase(animName)) {
            living.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            return;
        }
        if (ANIMATION_OFFHAND_SWING.equalsIgnoreCase(animName)) {
            living.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
            return;
        }
        sendCustomAnimation(living, animName, speed, transition);
    }

    private static void sendCustomAnimation(LivingEntity living, String animName, double speed, double transition) {
        int duration = (int) Math.round(transition > 1.0 ? transition : DEFAULT_ANIMATION_TRANSITION);
        if (!(living.level() instanceof ServerLevel)) {
            return;
        }
        var payload = new com.luatweaker.platform.network.ItemAnimationPayload(
                living.getId(), animName, duration, (float) (speed > 0 ? speed : DEFAULT_ANIMATION_SPEED));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(living, payload);
        if (living instanceof net.minecraft.server.level.ServerPlayer player) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
