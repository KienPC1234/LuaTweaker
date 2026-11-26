package com.luatweaker.common.entity;

import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.text.TextSpec;
import com.luatweaker.common.text.TextComponentConverter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

public class MinecraftPlayerWrapper extends MinecraftEntityWrapper implements IPlayer {
    private static final String AIR_ITEM_ID = "minecraft:air";


    private final Player player;

    public MinecraftPlayerWrapper(Player player) {
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
    public void sendText(TextSpec text) {
        if (player != null && text != null) {
            player.sendSystemMessage(TextComponentConverter.toComponent(text));
        }
    }

    @Override
    public void sendActionBarText(TextSpec text) {
        if (player != null && text != null) {
            player.displayClientMessage(TextComponentConverter.toComponent(text), true);
        }
    }

    @Override
    public void sendTitleText(TextSpec title, TextSpec subtitle, int fadeInTicks,
                              int stayTicks, int fadeOutTicks) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new net.minecraft.network.protocol.game
                    .ClientboundSetTitlesAnimationPacket(fadeInTicks, stayTicks, fadeOutTicks));
            if (subtitle != null) {
                serverPlayer.connection.send(new net.minecraft.network.protocol.game
                        .ClientboundSetSubtitleTextPacket(TextComponentConverter.toComponent(subtitle)));
            }
            if (title != null) {
                serverPlayer.connection.send(new net.minecraft.network.protocol.game
                        .ClientboundSetTitleTextPacket(TextComponentConverter.toComponent(title)));
            }
        }
    }

    @Override
    public int getPing() {
        return player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                ? serverPlayer.connection.latency() : 0;
    }

    @Override
    public boolean kick(String reason) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            String message = reason == null || reason.isBlank() ? "Disconnected" : reason;
            serverPlayer.connection.disconnect(Component.literal(message));
            return true;
        }
        return false;
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
    public boolean hasPermissionLevel(int level) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        net.minecraft.server.MinecraftServer server = serverPlayer.server;
        if (server == null) {
            return false;
        }
        net.minecraft.server.players.ServerOpListEntry entry =
                server.getPlayerList().getOps().get(serverPlayer.getGameProfile());
        int granted = entry == null ? 0 : entry.getLevel();
        return granted >= level;
    }

    @Override
    public String getGameMode() {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            return serverPlayer.gameMode.getGameModeForPlayer().getName();
        }
        if (player == null) {
            return "SURVIVAL";
        }
        if (player.isSpectator()) {
            return "SPECTATOR";
        }
        return player.isCreative() ? "CREATIVE" : "SURVIVAL";
    }

    private static ResourceLocation parseItemLocation(String itemId) {
        if (itemId.contains(":")) {
            return ResourceLocation.parse(itemId);
        }
        ResourceLocation mcLoc = ResourceLocation.fromNamespaceAndPath("minecraft", itemId);
        if (BuiltInRegistries.ITEM.containsKey(mcLoc)) {
            return mcLoc;
        }
        return ResourceLocation.fromNamespaceAndPath("luatweaker", itemId);
    }

    @Override
    public void giveItem(String itemId, int count) {
        if (player == null || itemId == null) {
            return;
        }
        ResourceLocation rl = parseItemLocation(itemId);
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == Items.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
        player.containerMenu.broadcastChanges();
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
            return stack.isEmpty() ? AIR_ITEM_ID : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        return AIR_ITEM_ID;
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
            return stack.isEmpty() ? AIR_ITEM_ID : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }
        return AIR_ITEM_ID;
    }

    @Override
    public void setMainHandItem(String itemId, int count) {
        if (player != null && itemId != null) {
            ResourceLocation rl = parseItemLocation(itemId);
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
    public java.util.List<java.util.Map<String, Object>> getInventory() {
        if (player == null) {
            return java.util.List.of();
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        java.util.List<java.util.Map<String, Object>> slots = new java.util.ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            slots.add(inventoryRow(slot, stack));
        }
        return slots;
    }

    private static java.util.Map<String, Object> inventoryRow(int slot, ItemStack stack) {
        java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("Slot", slot);
        entry.putAll(describeStack(stack));
        return entry;
    }

    @Override
    public int countItem(String itemId) {
        if (player == null || itemId == null) {
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(parseItemLocation(itemId));
        if (item == Items.AIR) {
            return 0;
        }
        int total = 0;
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && java.util.Objects.equals(stack.getItem(), item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public int removeItem(String itemId, int count) {
        if (player == null || itemId == null || count <= 0) {
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(parseItemLocation(itemId));
        if (item == Items.AIR) {
            return 0;
        }
        int remaining = count;
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !java.util.Objects.equals(stack.getItem(), item)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        int removed = count - remaining;
        if (removed > 0) {
            player.containerMenu.broadcastChanges();
        }
        return removed;
    }

    @Override
    public void dropItem(String itemId, int count) {
        if (player != null && itemId != null) {
            ResourceLocation rl = parseItemLocation(itemId);
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
        playSoundFrom(soundId, volume, pitch, "PLAYERS");
    }

    @Override
    public void playSoundFrom(String soundId, float volume, float pitch, String source) {
        if (player == null) {
            return;
        }
        ResourceLocation location = com.luatweaker.common.sound.SoundHelper.parseId(soundId);
        SoundEvent sound = com.luatweaker.common.sound.SoundHelper.registered(location, "player " + getName());
        if (sound == null) {
            return;
        }
        SoundSource category = com.luatweaker.common.sound.SoundHelper.parseSource(source, SoundSource.PLAYERS);
        player.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                sound,
                category,
                com.luatweaker.common.sound.SoundHelper.volume(volume),
                com.luatweaker.common.sound.SoundHelper.pitch(pitch)
        );
    }

    @Override
    public String getType() {
        return "minecraft:player";
    }

    @Override
    public void sendTitle(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(fadeInTicks, stayTicks, fadeOutTicks));
            if (subtitle != null) {
                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
            }
            if (title != null) {
                serverPlayer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(title)));
            }
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

    @Override
    public void syncCustomData(String key, Object value) {
        setCustomData(key, value);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            Object unwrapped = unwrapLuaValue(value);
            String valStr = unwrapped != null ? unwrapped.toString() : "null";
            String type = unwrapped == null ? "null" : unwrapped instanceof Boolean ? "bool" : unwrapped instanceof Number ? "num" : "str";
            // Built with Gson so quotes/newlines in a value cannot produce malformed JSON.
            com.google.gson.JsonObject payloadJson = new com.google.gson.JsonObject();
            payloadJson.addProperty("key", key);
            payloadJson.addProperty("val", valStr);
            payloadJson.addProperty("type", type);
            com.luatweaker.api.pal.Platform.getNetwork().sendPayloadPacket(
                    serverPlayer.getUUID().toString(),
                    "LuaTweaker:SyncData",
                    payloadJson.toString()
            );
        }
    }

    /** Mirrors the NBT storage rules so the client sees the same primitive type. */
    private static Object unwrapLuaValue(Object value) {
        if (!(value instanceof com.luatweaker.api.vm.ILuaValue lv)) {
            return value;
        }
        if (lv.isNil()) { return null; }
        if (lv.isBoolean()) { return lv.asBoolean(); }
        if (lv.isNumber()) { return lv.asDouble(); }
        return lv.asString();
    }

    @Override
    public void openScreen(String screenId, String title) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // Built with Gson so quotes in the title cannot break the control payload.
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("id", screenId);
            json.addProperty("title", title != null ? title : "");
            com.luatweaker.api.pal.Platform.getNetwork().sendPayloadPacket(
                    serverPlayer.getUUID().toString(),
                    "LuaTweaker:OpenScreen",
                    json.toString()
            );
        }
    }

    @Override
    public void closeScreen() {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.luatweaker.api.pal.Platform.getNetwork().sendPayloadPacket(
                    serverPlayer.getUUID().toString(),
                    "LuaTweaker:CloseScreen",
                    "{}"
            );
        }
    }

    @Override
    public void setGameMode(@NotNull String gameMode) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            net.minecraft.world.level.GameType type = switch (gameMode.toUpperCase(java.util.Locale.ROOT)) {
                case "CREATIVE" -> net.minecraft.world.level.GameType.CREATIVE;
                case "ADVENTURE" -> net.minecraft.world.level.GameType.ADVENTURE;
                case "SPECTATOR" -> net.minecraft.world.level.GameType.SPECTATOR;
                default -> net.minecraft.world.level.GameType.SURVIVAL;
            };
            serverPlayer.setGameMode(type);
        }
    }

    @Override
    public boolean canFly() {
        return player != null && player.getAbilities().mayfly;
    }

    @Override
    public void setCanFly(boolean canFly) {
        if (player != null) {
            player.getAbilities().mayfly = canFly;
            player.onUpdateAbilities();
        }
    }

    @Override
    public void setFlying(boolean flying) {
        if (player != null) {
            player.getAbilities().flying = flying;
            player.onUpdateAbilities();
        }
    }

    @Override
    public int getLevel() {
        return player != null ? player.experienceLevel : 0;
    }

    @Override
    public void setLevel(int level) {
        if (player != null) {
            player.experienceLevel = level;
        }
    }

    @Override
    public float getExperienceProgress() {
        return player != null ? player.experienceProgress : 0.0f;
    }

    @Override
    public void setExperienceProgress(float progress) {
        if (player != null) {
            player.experienceProgress = progress;
        }
    }

    @Override
    public int getTotalExperience() {
        return player != null ? player.totalExperience : 0;
    }

    @Override
    public int getSelectedSlot() {
        return player != null ? player.getInventory().selected : 0;
    }

    @Override
    public void setSelectedSlot(int slot) {
        if (player != null && slot >= 0 && slot < 9) {
            player.getInventory().selected = slot;
        }
    }

    @Override
    public boolean hasItemCooldown(@NotNull String itemId) {
        if (player != null) {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                return player.getCooldowns().isOnCooldown(BuiltInRegistries.ITEM.get(rl));
            }
        }
        return false;
    }

    @Override
    public void setItemCooldown(@NotNull String itemId, int ticks) {
        if (player != null) {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                player.getCooldowns().addCooldown(BuiltInRegistries.ITEM.get(rl), ticks);
            }
        }
    }

    @Override
    public @org.jetbrains.annotations.Nullable java.util.Map<String, Object> getInventorySlot(int slot) {
        if (player != null && slot >= 0 && slot < player.getInventory().getContainerSize()) {
            ItemStack stack = player.getInventory().getItem(slot);
            return stack.isEmpty() ? null : describeStack(stack);
        }
        return null;
    }

    @Override
    public void setInventorySlot(int slot, @NotNull String itemId, int count) {
        if (player != null && slot >= 0 && slot < player.getInventory().getContainerSize()) {
            if (itemId.isBlank() || AIR_ITEM_ID.equalsIgnoreCase(itemId)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                return;
            }
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                player.getInventory().setItem(slot, new ItemStack(BuiltInRegistries.ITEM.get(rl), Math.max(1, count)));
            }
        }
    }

    @Override
    public boolean isFake() {
        return player != null && dev.architectury.hooks.level.entity.PlayerHooks.isFake(player);
    }
}
