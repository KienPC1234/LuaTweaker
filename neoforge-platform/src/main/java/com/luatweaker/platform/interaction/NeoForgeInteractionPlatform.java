package com.luatweaker.platform.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.pal.IPlatformInteraction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NeoForgeInteractionPlatform implements IPlatformInteraction {
    @Override
    public void shootProjectile(@NotNull IEntity shooter, @NotNull String projectileType, double speed, double inaccuracy) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.shootProjectile(shooter, projectileType, speed, inaccuracy);
    }

    @Override
    public void shootProjectileAt(@NotNull IEntity shooter, @NotNull String projectileType, @NotNull IEntity target, double speed) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.shootProjectileAt(shooter, projectileType, target, speed);
    }

    @Override
    public void playAnimation(@NotNull IEntity entity, @NotNull String animName, double speed, double transition) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.playAnimation(entity, animName, speed, transition);
    }

    @Override
    public boolean performBlockBreak(@NotNull IEntity actor, int x, int y, int z) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.performBlockBreak(actor, x, y, z);
    }

    @Override
    public boolean performBlockPlace(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.performBlockPlace(actor, x, y, z, blockId);
    }

    @Override
    public boolean performBlockUse(@NotNull IEntity actor, int x, int y, int z) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.performBlockUse(actor, x, y, z);
    }

    @Override
    public boolean performItemUse(@NotNull IEntity actor, int slot) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.performItemUse(actor, slot);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, double x, double y, double z) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.lookAt(actor, x, y, z);
    }

    @Override
    public void lookAt(@NotNull IEntity actor, @NotNull IEntity target) {
        com.luatweaker.platform.interaction.EntityInteractionHelper.lookAt(actor, target);
    }

    @Override
    public boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.moveInventoryItem(actor, fromSlot, toSlot);
    }

    @Override
    public boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.dropInventoryItem(actor, slot, count);
    }

    @Override
    @NotNull
    public List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius) {
        return com.luatweaker.platform.interaction.BlockInteractionHelper.getNearbyBlocks(entity, radius);
    }

    @Override
    @NotNull
    public List<ILocatedItem> getInventoryItems(@NotNull IEntity entity) {
        return com.luatweaker.platform.interaction.InventoryInteractionHelper.getInventoryItems(entity);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(@NotNull String dimension, int x, int y, int z) {
        return new NeoForgeInteractableBlock(dimension, x, y, z);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableItem getInteractableItem(@NotNull Object entityOrBlock, int slot) {
        return new NeoForgeInteractableItem(entityOrBlock, slot);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull String uuid) {
        return com.luatweaker.platform.interaction.EntityInteractionHelper.getInteractableEntity(uuid);
    }

    @Override
    @Nullable
    public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(@NotNull Object rawEntity) {
        if (rawEntity instanceof net.minecraft.world.entity.Entity mcEntity) {
            return new NeoForgeInteractableEntity(mcEntity);
        }
        return null;
    }
}
