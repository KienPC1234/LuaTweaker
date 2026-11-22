package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.common.command.MinecraftCommandSender;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /luatweaker hand
 *
 * Inspects held item or target block with clickable copy-to-clipboard elements.
 * Generates ready-to-use Lua code snippets for item/block IDs and tags.
 */
public class HandCommand implements ILuaTweakerCommand {

    /** Reach used for the empty-hand block raycast, matching vanilla interaction distance. */
    private static final double BLOCK_REACH = 20.0D;
    private static final String LUA_WITH_NBT = ":withNbt(\"%s\")";
    private static final String LUA_ITEM = "item(\"%s\", %d)";
    private static final String LUA_INGREDIENT = "ingredient(\"%s\")";
    private static final String LUA_RAW_STRING = "\"%s\"";
    private static final String LUA_TAG = "tag(\"%s\")";
    private static final String NO_ITEM_TAGS = "    (No tags on this item)";
    private static final String NO_BLOCK_TAGS = "    (No tags on this block)";

    @Override
    public String getName() { return "hand"; }

    @Override
    public String getDescription() { return "Inspect held item or target block with clickable Lua copy."; }

    @Override
    public boolean isConsoleAllowed() { return false; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (!(sender instanceof MinecraftCommandSender neoforgeSender)) {
            return reportHeldItemId(sender);
        }
        CommandSourceStack source = neoforgeSender.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            sender.sendError("Only in-game players can execute /lt hand.");
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && held.getItem() != Items.AIR) {
            reportHeldItem(player, held);
            return 1;
        }
        if (reportTargetBlock(player)) {
            return 1;
        }
        sender.sendError("You are not holding any item and not looking at any block.");
        return 0;
    }

    /** Fallback for non-player senders (console, command blocks): report the id they expose. */
    private int reportHeldItemId(@NotNull ICommandSender sender) {
        String id = sender.getHeldItemId();
        if (id == null || id.isEmpty()) {
            sender.sendError("You are not holding any item.");
            return 0;
        }
        sender.sendSuccess("Held item: " + id);
        return 1;
    }

    private void reportHeldItem(@NotNull ServerPlayer player, @NotNull ItemStack held) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        String idStr = itemId.toString();
        String itemLua = String.format(LUA_ITEM, idStr, held.getCount());
        String ingredientLua = String.format(LUA_INGREDIENT, idStr);
        String rawStringLua = String.format(LUA_RAW_STRING, idStr);

        player.sendSystemMessage(Component.literal("§6=== [LuaTweaker Hand Inspection] ==="));
        player.sendSystemMessage(Component.literal("  Item ID: ").withStyle(ChatFormatting.GRAY)
                .append(createClickableText("\"" + idStr + "\"", idStr,
                        "Click to copy item ID: " + idStr, ChatFormatting.YELLOW)));
        player.sendSystemMessage(Component.literal("  Name: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(held.getHoverName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  Count: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(held.getCount())).withStyle(ChatFormatting.GREEN)));

        sendCustomData(player, held);
        player.sendSystemMessage(Component.literal("  Lua item: ").withStyle(ChatFormatting.GRAY)
                .append(createClickableText(itemLua, itemLua,
                        "Click to copy Lua item stack: " + itemLua, ChatFormatting.GREEN)));
        player.sendSystemMessage(Component.literal("  Lua ingredient: ").withStyle(ChatFormatting.GRAY)
                .append(createClickableText(ingredientLua, ingredientLua,
                        "Click to copy Lua ingredient: " + ingredientLua, ChatFormatting.GREEN)));
        player.sendSystemMessage(Component.literal("  Lua id: ").withStyle(ChatFormatting.GRAY)
                .append(createClickableText(rawStringLua, rawStringLua,
                        "Click to copy raw item ID", ChatFormatting.GREEN)));

        player.sendSystemMessage(Component.literal("  Item Tags:").withStyle(ChatFormatting.GRAY));
        sendTags(player, held.getTags().toList(), NO_ITEM_TAGS);
    }

    private void sendCustomData(@NotNull ServerPlayer player, @NotNull ItemStack held) {
        CustomData customData = held.get(DataComponents.CUSTOM_DATA);
        String nbt = customData == null ? null : customData.copyTag().toString();
        if (nbt == null || nbt.isEmpty()) {
            player.sendSystemMessage(Component.literal("  NBT: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("(No custom NBT)").withStyle(ChatFormatting.DARK_GRAY)));
            return;
        }
        String nbtLua = String.format(LUA_WITH_NBT, nbt.replace("\"", "\\\""));
        player.sendSystemMessage(Component.literal("  Lua NBT: ").withStyle(ChatFormatting.GRAY)
                .append(createClickableText(nbt, nbtLua,
                        "Click to copy Lua :withNbt() code", ChatFormatting.GOLD)));
    }

    /** Raycasts from the player's eyes and reports the targeted block; false when nothing is hit. */
    private boolean reportTargetBlock(@NotNull ServerPlayer player) {
        HitResult hit = player.pick(BLOCK_REACH, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return false;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = player.level().getBlockState(pos);
        Block block = state.getBlock();
        String blockIdStr = BuiltInRegistries.BLOCK.getKey(block).toString();

        player.sendSystemMessage(Component.literal("§6=== [LuaTweaker Block Inspection] ==="));
        player.sendSystemMessage(Component.literal("  Block ID: ").withStyle(ChatFormatting.GRAY)
                .append(createClickableText("\"" + blockIdStr + "\"", blockIdStr,
                        "Click to copy block ID: " + blockIdStr, ChatFormatting.YELLOW)));
        player.sendSystemMessage(Component.literal("  Name: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(block.getName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  Pos: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ()))
                        .withStyle(ChatFormatting.GREEN)));

        sendBlockState(player, state);
        player.sendSystemMessage(Component.literal("  Block Tags:").withStyle(ChatFormatting.GRAY));
        sendTags(player, state.getTags().toList(), NO_BLOCK_TAGS);
        return true;
    }

    private void sendBlockState(@NotNull ServerPlayer player, @NotNull BlockState state) {
        if (state.getProperties().isEmpty()) {
            return;
        }
        StringBuilder properties = new StringBuilder();
        for (Property<?> property : state.getProperties()) {
            if (!properties.isEmpty()) {
                properties.append(", ");
            }
            properties.append(property.getName()).append('=').append(state.getValue(property));
        }
        player.sendSystemMessage(Component.literal("  State: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(properties.toString()).withStyle(ChatFormatting.DARK_GREEN)));
    }

    private void sendTags(@NotNull ServerPlayer player, @NotNull List<? extends TagKey<?>> tags,
                          @NotNull String emptyMessage) {
        if (tags.isEmpty()) {
            player.sendSystemMessage(Component.literal(emptyMessage).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        for (TagKey<?> tagKey : tags) {
            String tagStr = "#" + tagKey.location();
            String tagFuncLua = String.format(LUA_TAG, tagStr);
            player.sendSystemMessage(Component.literal("    - ").withStyle(ChatFormatting.YELLOW)
                    .append(createClickableText(tagFuncLua, tagFuncLua,
                            "Click to copy Lua tag function: " + tagFuncLua, ChatFormatting.LIGHT_PURPLE)));
        }
    }

    private static @NotNull MutableComponent createClickableText(@NotNull String text, @NotNull String textToCopy,
                                                                  @NotNull String hoverMessage,
                                                                  @NotNull ChatFormatting color) {
        return Component.literal(text)
            .withStyle(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, textToCopy))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverMessage)))
            );
    }
}
