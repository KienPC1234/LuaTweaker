package com.luatweaker.platform.command;

import com.luatweaker.api.command.ICommandSender;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Adapts NeoForge's {@link CommandSourceStack} to {@link ICommandSender}.
 * This is the only NeoForge-specific class in the command pipeline —
 * all command logic above this layer is platform-agnostic.
 */
public class NeoForgeCommandSender implements ICommandSender {

    private final CommandSourceStack source;

    public NeoForgeCommandSender(CommandSourceStack source) {
        this.source = source;
    }

    @Override
    public void sendMessage(String message) {
        source.sendSystemMessage(Component.literal(message));
    }

    @Override
    public void sendSuccess(String message) {
        source.sendSuccess(() -> Component.literal("§a[LuaTweaker] " + message), false);
    }

    @Override
    public void sendError(String message) {
        source.sendFailure(Component.literal("§c[LuaTweaker] " + message));
    }

    @Override
    public boolean hasPermission(int level) {
        return source.hasPermission(level);
    }

    @Override
    public String getName() {
        return source.getTextName();
    }

    @Override
    public String getHeldItemId() {
        if (!(source.getEntity() instanceof Player player)) return "";
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || held.getItem() == Items.AIR) return "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(held.getItem())
                .toString();
    }
}
