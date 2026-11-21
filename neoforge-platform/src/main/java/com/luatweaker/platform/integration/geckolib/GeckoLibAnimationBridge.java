package com.luatweaker.platform.integration.geckolib;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.interaction.IInteractableItem;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.platform.interaction.NeoForgeInteractableItem;
import com.luatweaker.platform.interaction.ServerLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.GeoItem;

/**
 * Isolated bridge for GeckoLib keyframe animation triggering and stopping.
 * Encapsulates all third-party GeckoLib animatable calls away from core interaction platforms.
 */
public final class GeckoLibAnimationBridge {

    private GeckoLibAnimationBridge() {}

    public static boolean triggerAnimation(
            @NotNull Object target,
            @Nullable String controllerName,
            @NotNull String animName,
            double speed,
            boolean loop
    ) {
        return animate(target, controllerName, animName, GeoEntity::triggerAnim, GeoItem::triggerAnim,
                GeoBlockEntity::triggerAnim);
    }

    public static boolean stopAnimation(
            @NotNull Object target,
            @Nullable String controllerName,
            @NotNull String animName
    ) {
        return animate(target, controllerName, animName, GeoEntity::stopTriggeredAnim, GeoItem::stopTriggeredAnim,
                GeoBlockEntity::stopTriggeredAnim);
    }

    /**
     * Resolves {@code target} to the first animatable it exposes and lets the matching GeckoLib
     * call ({@code entityAnimation}, {@code itemAnimation} or {@code blockAnimation}) perform the
     * animation on it. Every supported target kind is tried in order: the raw GeoEntity, the item
     * in a living entity's hands, an item wrapper carrying a GeoItem, a block wrapper, then the
     * raw GeoBlockEntity itself.
     */
    private static boolean animate(@NotNull Object target,
                                   @Nullable String controllerName,
                                   @NotNull String animName,
                                   GeoEntityAnimation entityAnimation,
                                   GeoItemAnimation itemAnimation,
                                   GeoBlockAnimation blockAnimation) {
        String ctrl = normalizeController(controllerName);
        Object resolvedTarget = resolveTarget(target);

        if (resolvedTarget instanceof GeoEntity geoEntity) {
            entityAnimation.apply(geoEntity, ctrl, animName);
            return true;
        }
        if (resolvedTarget instanceof LivingEntity living
                && animateHeldItem(living, ctrl, animName, itemAnimation)) {
            return true;
        }
        if (resolvedTarget instanceof IInteractableItem item
                && animateItem(item, ctrl, animName, itemAnimation)) {
            return true;
        }
        if (resolvedTarget instanceof IInteractableBlock block
                && animateBlock(block, ctrl, animName, blockAnimation)) {
            return true;
        }
        if (resolvedTarget instanceof GeoBlockEntity geoBlockEntity) {
            blockAnimation.apply(geoBlockEntity, ctrl, animName);
            return true;
        }
        return false;
    }

    /** Lua entity wrappers are unwrapped to the real {@link Entity}; anything else is used as-is. */
    @NotNull
    private static Object resolveTarget(@NotNull Object target) {
        return target instanceof IEntity ie ? ie.getRawEntity() : target;
    }

    /** Blank or missing controllers mean "whatever controller the model plays by default". */
    @Nullable
    private static String normalizeController(@Nullable String controllerName) {
        return controllerName != null && !controllerName.isBlank() ? controllerName : null;
    }

    private static boolean animateHeldItem(LivingEntity holder, @Nullable String ctrl, @NotNull String animName,
                                           GeoItemAnimation itemAnimation) {
        ItemStack mainHand = holder.getMainHandItem();
        if (mainHand.getItem() instanceof GeoItem mainItem) {
            itemAnimation.apply(mainItem, holder, geoInstanceId(holder, mainHand), ctrl, animName);
            return true;
        }
        ItemStack offHand = holder.getOffhandItem();
        if (!(offHand.getItem() instanceof GeoItem offItem)) {
            return false;
        }
        itemAnimation.apply(offItem, holder, geoInstanceId(holder, offHand), ctrl, animName);
        return true;
    }

    private static boolean animateItem(IInteractableItem item, @Nullable String ctrl, @NotNull String animName,
                                        GeoItemAnimation itemAnimation) {
        if (!(item.getRawItemStack() instanceof ItemStack stack)
                || !(stack.getItem() instanceof GeoItem geoItem)) {
            return false;
        }
        Entity ownerEntity = resolveOwnerEntity(item);
        if (ownerEntity == null) {
            return false;
        }
        itemAnimation.apply(geoItem, ownerEntity, geoInstanceId(ownerEntity, stack), ctrl, animName);
        return true;
    }

    private static boolean animateBlock(IInteractableBlock block, @Nullable String ctrl, @NotNull String animName,
                                        GeoBlockAnimation blockAnimation) {
        ServerLevel level = ServerLevels.resolve(block.getDimension());
        if (level == null) {
            return false;
        }
        BlockPos pos = new BlockPos(block.getX(), block.getY(), block.getZ());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof GeoBlockEntity geoBlockEntity)) {
            return false;
        }
        blockAnimation.apply(geoBlockEntity, ctrl, animName);
        return true;
    }

    /** Per-stack GeckoLib instance id, assigned on the server and read back on the client. */
    private static long geoInstanceId(Entity holder, ItemStack stack) {
        return holder.level() instanceof ServerLevel serverLevel
                ? GeoItem.getOrAssignId(stack, serverLevel)
                : GeoItem.getId(stack);
    }

    private static Entity resolveOwnerEntity(IInteractableItem item) {
        if (item instanceof NeoForgeInteractableItem nfItem && nfItem.getOwner() instanceof Entity e) {
            return e;
        }
        String ownerUuid = item.getOwnerUuid();
        if (ownerUuid != null && !ownerUuid.isBlank()) {
            var player = Platform.getEntity().getPlayer(ownerUuid);
            if (player != null && player.getRawEntity() instanceof Entity e) {
                return e;
            }
        }
        return null;
    }

    /** The GeckoLib call to run on a resolved {@link GeoEntity}. */
    @FunctionalInterface
    private interface GeoEntityAnimation {
        void apply(GeoEntity entity, @Nullable String controller, String animation);
    }

    /** The GeckoLib call to run on a resolved {@link GeoItem} held by {@code holder}. */
    @FunctionalInterface
    private interface GeoItemAnimation {
        void apply(GeoItem item, Entity holder, long instanceId, @Nullable String controller, String animation);
    }

    /** The GeckoLib call to run on a resolved {@link GeoBlockEntity}. */
    @FunctionalInterface
    private interface GeoBlockAnimation {
        void apply(GeoBlockEntity blockEntity, @Nullable String controller, String animation);
    }
}
