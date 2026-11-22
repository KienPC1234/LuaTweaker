package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.common.entity.MinecraftEntityWrapper;
import com.luatweaker.common.entity.MinecraftPlayerWrapper;
import com.luatweaker.common.interaction.MinecraftInteractableItem;
import com.luatweaker.common.util.CallbackFirewall;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * Lua item callbacks shared by every item type.
 *
 * <p>Handlers only run server-side (client clicks must not consume mana / start
 * cooldowns / fire projectiles into a client level). Every callback goes through
 * {@link CallbackFirewall} so a broken script cannot abort the tick.
 */
public final class ItemHooks {

    private ItemHooks() {
    }

    /** Returns {@code null} when the item has no handler (caller falls back to vanilla behavior). */
    static InteractionResult handleUseOnBlock(net.minecraft.world.item.context.UseOnContext context,
                                              IItemBuilder builder) {
        if (context.getLevel().isClientSide() || builder.getOnUseOnBlockHandler() == null) {
            return null;
        }
        var pos = context.getClickedPos();
        Map<String, Object> hit = Map.of(
                "X", pos.getX(), "Y", pos.getY(), "Z", pos.getZ(),
                "Face", context.getClickedFace().getName());
        boolean consumed = Boolean.TRUE.equals(CallbackFirewall.supply(
                () -> builder.getOnUseOnBlockHandler().apply(new MinecraftPlayerWrapper(context.getPlayer()), hit),
                Boolean.FALSE, LogStage.SYSTEM,
                () -> "Failed onUseOnBlock handler for " + builder.getId()));
        return consumed ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    static InteractionResultHolder<ItemStack> handleItemUse(Level level, Player player,
                                                             net.minecraft.world.InteractionHand hand,
                                                             IItemBuilder builder) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && builder.getRightClickHandler() != null) {
            CallbackFirewall.run(
                    () -> builder.getRightClickHandler().accept(
                            new MinecraftPlayerWrapper(player), wrapItem(player, stack)),
                    LogStage.SYSTEM,
                    () -> "Failed right-click handler for " + builder.getId());
        }
        if (builder.getUseDuration() > 0 || builder.getFoodHunger() > 0) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return builder.getRightClickHandler() != null
                ? InteractionResultHolder.sidedSuccess(stack, level.isClientSide())
                : null;
    }

    static void handleUseTick(Level level, LivingEntity entity, ItemStack stack, int count,
                              IItemBuilder builder) {
        if (level.isClientSide() || builder.getOnUseTickHandler() == null || !(entity instanceof Player player)) {
            return;
        }
        CallbackFirewall.run(
                () -> builder.getOnUseTickHandler().accept(
                        new MinecraftPlayerWrapper(player), wrapItem(player, stack), count),
                LogStage.SYSTEM,
                () -> "Failed onUseTick handler for " + builder.getId());
    }

    static void handleReleaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft,
                                   IItemBuilder builder) {
        if (level.isClientSide() || builder.getOnReleaseUsingHandler() == null || !(entity instanceof Player player)) {
            return;
        }
        CallbackFirewall.run(
                () -> builder.getOnReleaseUsingHandler().accept(
                        new MinecraftPlayerWrapper(player), wrapItem(player, stack), timeLeft),
                LogStage.SYSTEM,
                () -> "Failed onReleaseUsing handler for " + builder.getId());
    }

    static void handleFinishUsing(ItemStack stack, Level level, LivingEntity entity, IItemBuilder builder) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        MinecraftInteractableItem itemWrapper = wrapItem(player, stack);
        if (builder.getOnFinishUsingHandler() != null) {
            CallbackFirewall.run(
                    () -> builder.getOnFinishUsingHandler().accept(new MinecraftPlayerWrapper(player), itemWrapper),
                    LogStage.SYSTEM,
                    () -> "Failed onFinishUsing handler for " + builder.getId());
        }
        if (builder.getOnConsumeHandler() != null) {
            CallbackFirewall.run(
                    () -> builder.getOnConsumeHandler().accept(new MinecraftPlayerWrapper(player), itemWrapper),
                    LogStage.SYSTEM,
                    () -> "Failed onConsume handler for " + builder.getId());
        }
    }

    static void handleHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker, IItemBuilder builder) {
        if (attacker.level().isClientSide() || builder.getOnHurtEnemyHandler() == null
                || !(attacker instanceof Player player)) {
            return;
        }
        CallbackFirewall.run(
                () -> builder.getOnHurtEnemyHandler().accept(
                        new MinecraftEntityWrapper(target),
                        new MinecraftPlayerWrapper(player),
                        wrapItem(player, stack)),
                LogStage.SYSTEM,
                () -> "Failed onHurtEnemy handler for " + builder.getId());
    }

    static void handleMineBlock(ItemStack stack, Level level,
                                net.minecraft.world.level.block.state.BlockState state,
                                net.minecraft.core.BlockPos pos, LivingEntity entity, IItemBuilder builder) {
        if (level.isClientSide() || builder.getOnMineBlockHandler() == null || !(entity instanceof Player player)) {
            return;
        }
        CallbackFirewall.run(
                () -> builder.getOnMineBlockHandler().accept(
                        new MinecraftPlayerWrapper(player),
                        wrapItem(player, stack),
                        pos.getX(), pos.getY(), pos.getZ()),
                LogStage.SYSTEM,
                () -> "Failed onMineBlock handler for " + builder.getId());
    }

    public static void handleSwing(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || player.level().isClientSide()) {
            return;
        }
        IItemBuilder builder = ItemRegistrar.BUILDER_BY_ITEM.get(stack.getItem());
        if (builder == null || builder.getOnSwingHandler() == null) {
            return;
        }
        CallbackFirewall.run(
                () -> builder.getOnSwingHandler().accept(new MinecraftPlayerWrapper(player), wrapItem(player, stack)),
                LogStage.SYSTEM,
                () -> "Failed onSwing handler for " + builder.getId());
    }

    private static MinecraftInteractableItem wrapItem(Player player, ItemStack stack) {
        int slot = -1;
        if (player != null) {
            var inventory = player.getInventory();
            if (java.util.Objects.equals(player.getMainHandItem(), stack)) {
                slot = inventory.selected;
            } else {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    if (java.util.Objects.equals(inventory.getItem(i), stack)) {
                        slot = i;
                        break;
                    }
                }
            }
        }
        return new MinecraftInteractableItem(player, slot, stack);
    }
}
