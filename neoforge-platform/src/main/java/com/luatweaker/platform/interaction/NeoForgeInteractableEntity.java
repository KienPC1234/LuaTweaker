package com.luatweaker.platform.interaction;

import com.luatweaker.api.interaction.IInteractableEntity;
import com.luatweaker.platform.entity.NeoForgeEntityWrapper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;

/**
 * Single entity wrapper (AGENTS.md 5.2): extends {@link NeoForgeEntityWrapper}
 * so the full {@link com.luatweaker.api.entity.IEntity} surface is inherited,
 * and adds only the {@link IInteractableEntity}-specific operations (player
 * messaging, titles, item giving, NBT, fire state, lookAt).
 */
public class NeoForgeInteractableEntity extends NeoForgeEntityWrapper implements IInteractableEntity {

    public NeoForgeInteractableEntity(@NotNull Entity entity) {
        super(entity);
    }

    @Override
    @NotNull
    public String getId() {
        return entity.getUUID().toString();
    }

    @Override
    public void setPosition(double x, double y, double z) {
        entity.teleportTo(x, y, z);
    }

    @Override
    public void lookAt(double x, double y, double z) {
        if (entity instanceof Mob mob) {
            mob.getLookControl().setLookAt(x, y, z);
        } else {
            double dx = x - entity.getX();
            double dy = y - (entity.getY() + entity.getEyeHeight());
            double dz = z - entity.getZ();
            double dh = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
            entity.setYRot(yaw);
            entity.setXRot(pitch);
            entity.setYHeadRot(yaw);
        }
    }

    @Override
    public void lookAt(@NotNull Object targetEntity) {
        if (targetEntity instanceof Entity mcTarget) {
            if (entity instanceof Mob mob) {
                mob.getLookControl().setLookAt(mcTarget, 10.0f, (float) mob.getMaxHeadXRot());
            } else {
                lookAt(mcTarget.getX(), mcTarget.getY() + mcTarget.getEyeHeight(), mcTarget.getZ());
            }
        }
    }

    @Override
    public void SendMessage(@NotNull String message) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }

    @Override
    public void SendTitle(@NotNull String title, @NotNull String subtitle) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(net.minecraft.network.chat.Component.literal(title)));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(net.minecraft.network.chat.Component.literal(subtitle)));
        }
    }

    @Override
    public void SendOverlayMessage(@NotNull String message) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), true);
        }
    }

    @Override
    public boolean GiveItem(@NotNull String itemId, int count) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.parse(itemId);
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (item != null) {
                return player.getInventory().add(new net.minecraft.world.item.ItemStack(item, count));
            }
        }
        return false;
    }

    @Override
    public boolean isOnFire() {
        return entity.isOnFire();
    }

    @Override
    public void setOnFire(boolean onFire) {
        if (onFire) {
            entity.setRemainingFireTicks(100);
        } else {
            entity.clearFire();
        }
    }

    @Override
    @NotNull
    public String getNbt() {
        return entity.getPersistentData().toString();
    }

    @Override
    public void setNbt(@NotNull String nbtJson) {
        try {
            net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(nbtJson);
            entity.getPersistentData().merge(tag);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "Failed to parse NBT JSON for entity " + entity.getUUID() + ": " + e.getMessage()
            );
        }
    }

    @Override
    public void PlayAnimation(@NotNull String animationName, double speed, double transitionLength) {
        EntityInteractionHelper.playAnimation(this, animationName, speed, transitionLength);
    }

    @Override
    public void StopAnimation(@NotNull String animationName) {
        entity.getPersistentData().remove("CurrentAnimation");
    }

    @Override
    public void Destroy() {
        entity.discard();
    }
}
