package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import dev.architectury.event.events.common.LootEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * Payload builders for player item/clone events and {@link LootEvent.MODIFY_LOOT_TABLE}.
 */
public final class PlayerActionPayloads {

    private static final int INDEX_MIN_ROLLS = 3;
    private static final int INDEX_MAX_ROLLS = 4;
    private static final int SINGLE_ITEM_COUNT = 1;

    private PlayerActionPayloads() {
    }

    public static @NotNull ILuaTable buildItemAction(@NotNull ILuaEngine engine, @NotNull Player player,
                                                    @NotNull ItemStack stack) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadEntities.putItem(engine, table, stack);
        return table;
    }

    public static @NotNull ILuaTable buildClone(@NotNull ILuaEngine engine, @NotNull Player oldPlayer,
                                              @NotNull Player newPlayer, boolean wonGame) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putEntity(engine, table, "oldPlayer", "OldPlayer", oldPlayer);
        PayloadEntities.putEntity(engine, table, "newPlayer", "NewPlayer", newPlayer);
        PayloadTables.putBoth(table, "conqueredEnd", engine.wrapBoolean(wonGame));
        return table;
    }

    public static @NotNull ILuaTable buildLootModify(@NotNull ILuaEngine engine, @NotNull ResourceLocation tableId,
                                                   @NotNull LootEvent.LootTableModificationContext context,
                                                   boolean builtin) {
        ILuaTable table = engine.createTable();
        PayloadTables.putBoth(table, "id", engine.wrapString(tableId.toString()));
        PayloadTables.putBoth(table, "builtin", engine.wrapBoolean(builtin));
        ILuaFunction addPoolFn = args -> engine.wrapBoolean(addPoolToContext(context, args));
        PayloadTables.putBoth(table, "addPool", addPoolFn);
        return table;
    }

    private static boolean addPoolToContext(LootEvent.LootTableModificationContext context, ILuaValue... args) {
        int off = LuaBinder.getOffset(args);
        if (args.length <= off || !args[off].isString()) {
            return false;
        }
        ResourceLocation rl = ResourceLocation.tryParse(args[off].asString().trim());
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return false;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) {
            return false;
        }
        int count = readPositiveInt(args, off + 1, 1);
        int weight = readPositiveInt(args, off + 2, 1);
        int minRolls = readPositiveInt(args, off + INDEX_MIN_ROLLS, 1);
        int maxRolls = Math.max(minRolls, readPositiveInt(args, off + INDEX_MAX_ROLLS, minRolls));

        var entry = LootItem.lootTableItem(item).setWeight(weight);
        if (count > SINGLE_ITEM_COUNT) {
            entry.apply(SetItemCountFunction.setCount(UniformGenerator.between(count, count)));
        }
        context.addPool(LootPool.lootPool()
                .setRolls(UniformGenerator.between(minRolls, maxRolls))
                .add(entry));
        return true;
    }

    private static int readPositiveInt(ILuaValue[] args, int index, int fallback) {
        if (index >= args.length || !args[index].isNumber()) {
            return fallback;
        }
        return Math.max(1, args[index].asInt());
    }
}
