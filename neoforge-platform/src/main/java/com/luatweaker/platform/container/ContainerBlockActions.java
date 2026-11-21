package com.luatweaker.platform.container;

import com.luatweaker.api.event.EventNames;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import com.luatweaker.platform.util.CallbackFirewall;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;
import java.util.function.BiConsumer;

/** Right-click/menu/drop behaviour shared by Lua blocks. */
final class ContainerBlockActions {

    private static final String LOCKED_KEY = "container.luatweaker.locked";

    private ContainerBlockActions() {
    }

    static void fireRightClick(@Nullable BiConsumer<Object, Object> handler, @NotNull Player player,
                               @NotNull Level level, @NotNull BlockPos pos, @NotNull String blockId) {
        if (handler == null) {
            return;
        }
        CallbackFirewall.run(() -> {
            String dimension = level.dimension().location().toString();
            var blockWrapper = new com.luatweaker.platform.interaction.NeoForgeInteractableBlock(
                    dimension, pos.getX(), pos.getY(), pos.getZ());
            handler.accept(new NeoForgePlayerWrapper(player), blockWrapper);
        }, LogStage.SYSTEM, () -> "Failed right-click handler for " + blockId);
    }

    /** Opens the container menu and fires {@code ContainerOpened}; FAIL when locked/unavailable. */
    @NotNull
    static InteractionResult openMenu(@NotNull CustomContainerBlock block,
                                      @NotNull CustomContainerBlockEntity container,
                                      @NotNull Player player, @NotNull Level level, @NotNull BlockPos pos) {
        if (!container.stillValid(player)) {
            return locked(player);
        }
        OptionalInt menuId = player.openMenu(container);
        if (menuId.isEmpty()) {
            return locked(player);
        }
        int blockX = pos.getX();
        int blockY = pos.getY();
        int blockZ = pos.getZ();
        ContainerEvents.post(EventNames.CONTAINER_OPENED, engine -> {
            var payload = ContainerEvents.basePayload(engine, block, blockX, blockY, blockZ);
            payload.rawset("Player", engine.wrapString(player.getName().getString()));
            return payload;
        });
        return InteractionResult.CONSUME;
    }

    @NotNull
    private static InteractionResult locked(Player player) {
        player.displayClientMessage(Component.translatable(LOCKED_KEY), true);
        return InteractionResult.FAIL;
    }

    /** Drops the container contents according to the builder's drop mode. */
    static void dropContents(@NotNull CustomContainerBlock block, @NotNull CustomContainerBlockEntity container,
                             @NotNull Level level, @NotNull BlockPos pos, @NotNull ContainerSpec spec) {
        switch (spec.dropMode()) {
            case "packed" -> {
                ItemStack packed = new ItemStack(block);
                container.saveToItem(packed, level.registryAccess());
                net.minecraft.world.level.block.Block.popResource(level, pos, packed);
            }
            case "spill" -> {
                for (int index = 0; index < container.getContainerSize(); index++) {
                    ItemStack stack = container.getItem(index);
                    if (!stack.isEmpty()) {
                        net.minecraft.world.level.block.Block.popResource(level, pos, stack);
                    }
                }
            }
            default -> {
            }
        }
    }
}
