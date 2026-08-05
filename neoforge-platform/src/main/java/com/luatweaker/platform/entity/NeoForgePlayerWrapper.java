package com.luatweaker.platform.entity;

import com.luatweaker.api.entity.IPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class NeoForgePlayerWrapper extends NeoForgeEntityWrapper implements IPlayer {

    private final Player player;

    public NeoForgePlayerWrapper(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public void sendMessage(String message) {
        if (player != null && message != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    @Override
    public void sendActionBar(String message) {
        if (player != null && message != null) {
            player.displayClientMessage(Component.literal(message), true);
        }
    }

    @Override
    public String getName() {
        return player != null ? player.getName().getString() : "Unknown";
    }

    @Override
    public String getUuid() {
        return player != null ? player.getUUID().toString() : "";
    }

    @Override
    public boolean isSneaking() {
        return player != null && player.isShiftKeyDown();
    }

    @Override
    public boolean isCreative() {
        return player != null && player.isCreative();
    }

    @Override
    public void giveItem(String itemId, int count) {
        if (player != null && itemId != null) {
            ResourceLocation rl = itemId.contains(":") ? ResourceLocation.parse(itemId) : ResourceLocation.fromNamespaceAndPath("luatweaker", itemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null && item != Items.AIR) {
                player.getInventory().add(new ItemStack(item, Math.max(1, count)));
            }
        }
    }

    @Override
    public void giveExperience(int exp) {
        if (player != null) {
            player.giveExperiencePoints(exp);
        }
    }

    @Override
    public String getMainHandItem() {
        if (player != null) {
            ItemStack stack = player.getMainHandItem();
            return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        return "minecraft:air";
    }

    @Override
    public String getDimension() {
        if (player != null && player.level() != null) {
            return player.level().dimension().location().toString();
        }
        return "minecraft:overworld";
    }

    @Override
    public String getOffHandItem() {
        if (player != null) {
            ItemStack stack = player.getOffhandItem();
            return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        return "minecraft:air";
    }

    @Override
    public void setMainHandItem(String itemId, int count) {
        if (player != null && itemId != null) {
            ResourceLocation rl = itemId.contains(":") ? ResourceLocation.parse(itemId) : ResourceLocation.fromNamespaceAndPath("luatweaker", itemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(item, Math.max(1, count)));
            }
        }
    }

    @Override
    public void clearInventory() {
        if (player != null) {
            player.getInventory().clearContent();
        }
    }

    @Override
    public void dropItem(String itemId, int count) {
        if (player != null && itemId != null) {
            ResourceLocation rl = itemId.contains(":") ? ResourceLocation.parse(itemId) : ResourceLocation.fromNamespaceAndPath("luatweaker", itemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null && item != Items.AIR) {
                player.drop(new ItemStack(item, Math.max(1, count)), false);
            }
        }
    }

    @Override
    public int getFoodLevel() {
        return player != null ? player.getFoodData().getFoodLevel() : 20;
    }

    @Override
    public void setFoodLevel(int level) {
        if (player != null) {
            player.getFoodData().setFoodLevel(level);
        }
    }

    @Override
    public void playSound(String soundId, float volume, float pitch) {
        if (player == null || soundId == null) return;
        try {
            ResourceLocation rl = soundId.contains(":")
                    ? ResourceLocation.parse(soundId)
                    : ResourceLocation.fromNamespaceAndPath("luatweaker", soundId);
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(rl);
            if (sound != null) {
                player.level().playSound(
                        null,
                        player.getX(), player.getY(), player.getZ(),
                        sound,
                        SoundSource.PLAYERS,
                        Math.max(0f, volume),
                        Math.max(0.1f, pitch)
                );
            }
        } catch (Exception e) {
            // Silently ignore invalid sound IDs
        }
    }

    @Override
    public String getType() {
        return "minecraft:player";
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (title != null) {
                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(title)));
            }
            if (subtitle != null) {
                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
            }
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(fadeInTicks, stayTicks, fadeOutTicks));
        }
    }

    @Override
    public void heal(float amount) {
        if (player != null && amount > 0) {
            player.heal(amount);
        }
    }

    @Override
    public void feed(int foodAmount, float saturation) {
        if (player != null) {
            player.getFoodData().eat(Math.max(1, foodAmount), saturation);
        }
    }

    @Override
    public void teleport(double x, double y, double z) {
        if (player != null) {
            player.teleportTo(x, y, z);
        }
    }
}
