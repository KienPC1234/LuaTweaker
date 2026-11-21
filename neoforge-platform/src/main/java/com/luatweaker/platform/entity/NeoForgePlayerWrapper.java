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
    private static final String AIR_ITEM_ID = "minecraft:air";


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
        ResourceLocation location = com.luatweaker.platform.sound.SoundHelper.parseId(soundId);
        SoundEvent sound = com.luatweaker.platform.sound.SoundHelper.registered(location, "player " + getName());
        if (sound == null) {
            return;
        }
        SoundSource category = com.luatweaker.platform.sound.SoundHelper.parseSource(source, SoundSource.PLAYERS);
        player.level().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                sound,
                category,
                com.luatweaker.platform.sound.SoundHelper.volume(volume),
                com.luatweaker.platform.sound.SoundHelper.pitch(pitch)
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
}
