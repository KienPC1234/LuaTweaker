package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.entities.EntitiesLuaBinding;
import com.luatweaker.interaction.InteractionLuaBinding;
import com.luatweaker.platform.entity.NeoForgeEntityWrapper;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import com.luatweaker.platform.interaction.NeoForgeInteractableItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Wraps live entities, players and items as engine-neutral Lua tables. */
final class PayloadEntities {

    private PayloadEntities() {
    }

    static void putEntity(@NotNull ILuaEngine engine, @NotNull ILuaTable table,
                          @NotNull String lower, @NotNull String upper, @Nullable Entity entity) {
        ILuaTable wrapped = wrap(engine, entity);
        if (wrapped != null) {
            table.rawset(lower, wrapped);
            table.rawset(upper, wrapped);
        }
    }

    static void putPlayer(@NotNull ILuaEngine engine, @NotNull ILuaTable table, @Nullable Player player) {
        if (player == null) {
            return;
        }
        ILuaTable wrapped = EntitiesLuaBinding.createPlayerLuaTable(engine, new NeoForgePlayerWrapper(player));
        table.rawset("player", wrapped);
        table.rawset("Player", wrapped);
        table.rawset("entity", wrapped);
        table.rawset("Entity", wrapped);
    }

    static void putItem(@NotNull ILuaEngine engine, @NotNull ILuaTable table, @Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ILuaTable wrapped = InteractionLuaBinding.wrapItem(engine, new NeoForgeInteractableItem(stack));
        table.rawset("item", wrapped);
        table.rawset("Item", wrapped);
    }

    static @Nullable ILuaTable wrap(@NotNull ILuaEngine engine, @Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof Player player) {
            return EntitiesLuaBinding.createPlayerLuaTable(engine, new NeoForgePlayerWrapper(player));
        }
        return EntitiesLuaBinding.createEntityLuaTable(engine, new NeoForgeEntityWrapper(entity));
    }
}
