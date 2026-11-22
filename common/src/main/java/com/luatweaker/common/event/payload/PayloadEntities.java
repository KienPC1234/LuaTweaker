package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.common.entity.MinecraftEntityWrapper;
import com.luatweaker.common.entity.MinecraftPlayerWrapper;
import com.luatweaker.common.interaction.MinecraftInteractableItem;
import com.luatweaker.entities.EntitiesLuaBinding;
import com.luatweaker.interaction.InteractionLuaBinding;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps live entities, players and items as engine-neutral Lua tables.
 */
public final class PayloadEntities {

    private PayloadEntities() {
    }

    public static void putEntity(@NotNull ILuaEngine engine, @NotNull ILuaTable table,
                                 @NotNull String lower, @NotNull String upper, @Nullable Entity entity) {
        ILuaTable wrapped = wrap(engine, entity);
        if (wrapped != null) {
            table.rawset(lower, wrapped);
            table.rawset(upper, wrapped);
        }
    }

    public static void putPlayer(@NotNull ILuaEngine engine, @NotNull ILuaTable table, @Nullable Player player) {
        if (player == null) {
            return;
        }
        ILuaTable wrapped = EntitiesLuaBinding.createPlayerLuaTable(engine, new MinecraftPlayerWrapper(player));
        table.rawset("player", wrapped);
        table.rawset("Player", wrapped);
        table.rawset("entity", wrapped);
        table.rawset("Entity", wrapped);
    }

    public static void putItem(@NotNull ILuaEngine engine, @NotNull ILuaTable table, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ILuaTable wrapped = InteractionLuaBinding.wrapItem(engine, new MinecraftInteractableItem(stack));
        table.rawset("item", wrapped);
        table.rawset("Item", wrapped);
    }

    public static @Nullable ILuaTable wrap(@NotNull ILuaEngine engine, @Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof Player player) {
            return EntitiesLuaBinding.createPlayerLuaTable(engine, new MinecraftPlayerWrapper(player));
        }
        return EntitiesLuaBinding.createEntityLuaTable(engine, new MinecraftEntityWrapper(entity));
    }
}
