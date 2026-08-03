package com.luatweaker.platform.command;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.entity.IPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

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

    public CommandSourceStack getSource() {
        return source;
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
    public boolean isPlayer() {
        return source.getEntity() instanceof Player;
    }

    @Override
    public @Nullable IPlayer getPlayer() {
        if (source.getEntity() instanceof ServerPlayer serverPlayer) {
            return new com.luatweaker.platform.entity.NeoForgePlayerWrapper(serverPlayer);
        }
        return null;
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
