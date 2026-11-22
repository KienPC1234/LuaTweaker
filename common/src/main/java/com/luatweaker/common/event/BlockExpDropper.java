package com.luatweaker.common.event;

import com.luatweaker.common.container.CustomContainerRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BlockExpDropper {

    private BlockExpDropper() {
    }

    public static void dropExpIfNeeded(Level level, net.minecraft.core.BlockPos pos, BlockState state, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || state == null || state.isAir()) {
            return;
        }
        int[] range = CustomContainerRegistry.EXP_DROPS.get(state.getBlock());
        if (range == null || range[1] <= 0 || hasSilkTouch(player, serverLevel)) {
            return;
        }
        int xp = rollXp(range[0], range[1], serverLevel.random);
        if (xp > 0) {
            ExperienceOrb.award(serverLevel, Vec3.atCenterOf(pos), xp);
        }
    }

    private static boolean hasSilkTouch(Player player, ServerLevel level) {
        if (player == null) {
            return false;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty()) {
            return false;
        }
        Holder<Enchantment> silk = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silk, tool) > 0;
    }

    private static int rollXp(int minExp, int maxExp, RandomSource random) {
        if (minExp >= maxExp) {
            return Math.max(0, minExp);
        }
        return minExp + random.nextInt(maxExp - minExp + 1);
    }
}
