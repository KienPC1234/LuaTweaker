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

public class NeoForgePlayerWrapper implements IPlayer {

    private final Player player;

    public NeoForgePlayerWrapper(Player player) {
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
    public void addEffect(String effectId, int durationTicks, int amplifier) {
        if (player != null && effectId != null && !effectId.isBlank()) {
            ResourceLocation rl = effectId.contains(":") ? ResourceLocation.parse(effectId) : ResourceLocation.fromNamespaceAndPath("minecraft", effectId);
            net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(rl).orElse(null);
            if (effectHolder != null) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effectHolder, durationTicks, amplifier));
            }
        }
    }


    @Override
    public void playSound(String soundId, float volume, float pitch) {
        if (player == null || soundId == null) return;
        try {
            ResourceLocation rl = soundId.contains(":")
                    ? ResourceLocation.parse(soundId)
                    : ResourceLocation.fromNamespaceAndPath("luatweaker", soundId);
            // Look up in the registry first; fall back to a wrapper SoundEvent if not registered
            // (works for vanilla sounds and any mod sounds registered in sounds.json)
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(rl);
            if (sound != null) {
                player.level().playSound(
                        null,                  // null = play for everyone near position
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
    public float getHealth() {
        return player != null ? player.getHealth() : 0.0f;
    }

    @Override
    public void setHealth(float health) {
        if (player != null) {
            player.setHealth(health);
        }
    }

    @Override
    public float getMaxHealth() {
        return player != null ? player.getMaxHealth() : 20.0f;
    }

    @Override
    public boolean isAlive() {
        return player != null && player.isAlive();
    }

    @Override
    public void remove() {
        if (player != null) {
            player.discard();
        }
    }

    @Override
    public Object getRawEntity() {
        return player;
    }
}
