package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.platform.command.NeoForgeCommandSender;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * /luatweaker hand
 *
 * Inspects held item or target block with clickable copy-to-clipboard elements.
 * Generates ready-to-use Lua code snippets for item/block IDs and tags.
 */
public class HandCommand implements ILuaTweakerCommand {

    @Override
    public String getName() { return "hand"; }

    @Override
    public String getDescription() { return "Inspect held item or target block with clickable Lua copy."; }

    @Override
    public boolean isConsoleAllowed() { return false; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (!(sender instanceof NeoForgeCommandSender neoforgeSender)) {
            String id = sender.getHeldItemId();
            if (id == null || id.isEmpty()) {
                sender.sendError("You are not holding any item.");
                return 0;
            }
            sender.sendSuccess("Held item: " + id);
            return 1;
        }

        CommandSourceStack source = neoforgeSender.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            sender.sendError("Only in-game players can execute /lt hand.");
            return 0;
        }

        ItemStack held = player.getMainHandItem();

        if (!held.isEmpty() && held.getItem() != Items.AIR) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
            String idStr = itemId.toString();
            String itemLua = String.format("item(\"%s\", %d)", idStr, held.getCount());
            String ingLua = String.format("ingredient(\"%s\")", idStr);
            String rawStringLua = String.format("\"%s\"", idStr);

            player.sendSystemMessage(Component.literal("§6=== [LuaTweaker Hand Inspection] ==="));

            // Item ID (Clickable)
            MutableComponent idComp = Component.literal("  Item ID: ")
                .withStyle(ChatFormatting.GRAY)
                .append(createClickableText("\"" + idStr + "\"", idStr, "Click to copy item ID: " + idStr, ChatFormatting.YELLOW));
            player.sendSystemMessage(idComp);

            // Item Name & Count
            player.sendSystemMessage(Component.literal("  Name: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(held.getHoverName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  Count: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(held.getCount())).withStyle(ChatFormatting.GREEN)));

            // Item NBT / Custom Components (Clickable)
            var customData = held.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            String nbtStr = customData != null ? customData.copyTag().toString() : null;
            if (nbtStr != null && !nbtStr.isEmpty()) {
                String nbtLua = String.format(":withNbt(\"%s\")", nbtStr.replace("\"", "\\\""));
                player.sendSystemMessage(Component.literal("  Lua NBT: ").withStyle(ChatFormatting.GRAY)
                    .append(createClickableText(nbtStr, nbtLua, "Click to copy Lua :withNbt() code", ChatFormatting.GOLD)));
            } else {
                player.sendSystemMessage(Component.literal("  NBT: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("(No custom NBT)").withStyle(ChatFormatting.DARK_GRAY)));
            }

            // Item Tags
            player.sendSystemMessage(Component.literal("  Item Tags:").withStyle(ChatFormatting.GRAY));
            var tags = held.getTags().toList();
            if (tags.isEmpty()) {
                player.sendSystemMessage(Component.literal("    (No tags on this item)").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                for (TagKey<Item> tagKey : tags) {
                    String tagStr = "#" + tagKey.location().toString();
                    String tagFuncLua = String.format("tag(\"%s\")", tagStr);
                    MutableComponent tagComp = Component.literal("    • ").withStyle(ChatFormatting.YELLOW)
                        .append(createClickableText(tagFuncLua, tagFuncLua, "Click to copy Lua tag function: " + tagFuncLua, ChatFormatting.LIGHT_PURPLE));
                    player.sendSystemMessage(tagComp);
                }
            }
            return 1;
        }

        // Hand is EMPTY -> Raycast block
        HitResult hit = player.pick(20.0D, 0.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = player.level().getBlockState(pos);
            Block block = state.getBlock();
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            String blockIdStr = blockId.toString();

            player.sendSystemMessage(Component.literal("§6=== [LuaTweaker Block Inspection] ==="));

            // Block ID (Clickable)
            MutableComponent blockComp = Component.literal("  Block ID: ")
                .withStyle(ChatFormatting.GRAY)
                .append(createClickableText("\"" + blockIdStr + "\"", blockIdStr, "Click to copy block ID: " + blockIdStr, ChatFormatting.YELLOW));
            player.sendSystemMessage(blockComp);

            // Block Name & Position
            player.sendSystemMessage(Component.literal("  Name: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(block.getName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  Pos: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.format("(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())).withStyle(ChatFormatting.GREEN)));

            // Block State Properties
            if (!state.getProperties().isEmpty()) {
                StringBuilder propsSb = new StringBuilder();
                for (Property<?> p : state.getProperties()) {
                    if (!propsSb.isEmpty()) propsSb.append(", ");
                    propsSb.append(p.getName()).append("=").append(state.getValue(p));
                }
                player.sendSystemMessage(Component.literal("  State: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(propsSb.toString()).withStyle(ChatFormatting.DARK_GREEN)));
            }

            // Block Tags
            player.sendSystemMessage(Component.literal("  Block Tags:").withStyle(ChatFormatting.GRAY));
            var blockTags = state.getTags().toList();
            if (blockTags.isEmpty()) {
                player.sendSystemMessage(Component.literal("    (No tags on this block)").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                for (TagKey<Block> tagKey : blockTags) {
                    String tagStr = "#" + tagKey.location().toString();
                    String tagFuncLua = String.format("tag(\"%s\")", tagStr);
                    MutableComponent tagComp = Component.literal("    • ").withStyle(ChatFormatting.YELLOW)
                        .append(createClickableText(tagFuncLua, tagFuncLua, "Click to copy Lua tag function: " + tagFuncLua, ChatFormatting.LIGHT_PURPLE));
                    player.sendSystemMessage(tagComp);
                }
            }
            return 1;
        }

        sender.sendError("You are not holding any item and not looking at any block.");
        return 0;
    }

    private MutableComponent createClickableText(String text, String textToCopy, String hoverMessage, ChatFormatting color) {
        return Component.literal(text)
            .withStyle(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, textToCopy))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverMessage)))
            );
    }
}
