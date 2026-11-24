package com.luatweaker.fabric.recipe;

import com.luatweaker.common.recipe.CommonInterceptionApplier;
import com.luatweaker.common.recipe.CommonRecipeManager;
import com.luatweaker.common.recipe.InterceptionHelper;
import dev.architectury.event.events.common.LifecycleEvent;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers Lua brewing recipes and villager trades on Fabric.
 * Anvil recipes are handled loader-agnostically for Fabric via {@code AnvilMenuMixin}.
 */
public final class FabricRecipeAndLootHandler {

    /**
     * Intentional snapshot of vanilla/modded villager trade arrays prior to Lua additions,
     * so reloads replace previous Lua trades instead of duplicating them.
     */
    private static final Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> BASE_TRADES =
            new ConcurrentHashMap<>();

    private FabricRecipeAndLootHandler() {
    }

    public static void register() {
        FabricBrewingRecipeRegistryBuilder.BUILD.register(CommonInterceptionApplier::applyPendingBrewing);
        LifecycleEvent.SERVER_STARTING.register(server -> applyVillagerTrades());
        CommonInterceptionApplier.registerPostPopulateHook(() -> {
            if (!BASE_TRADES.isEmpty()) {
                applyVillagerTrades();
            }
        });
    }

    public static void applyVillagerTrades() {
        ensureBaseTradesCaptured();
        restoreBaseTrades();
        for (CommonRecipeManager.TradeData data : InterceptionHelper.getPendingTrades()) {
            ResourceLocation profLoc = ResourceLocation.parse(data.profession());
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(profLoc);
            if (profession == null) {
                continue;
            }
            Int2ObjectMap<VillagerTrades.ItemListing[]> levelMap = VillagerTrades.TRADES.get(profession);
            if (levelMap == null || !levelMap.containsKey(data.level())) {
                continue;
            }
            VillagerTrades.ItemListing[] current = levelMap.get(data.level());
            VillagerTrades.ItemListing[] updated = Arrays.copyOf(current, current.length + 1);
            updated[current.length] = (trader, random) -> CommonInterceptionApplier.createOffer(data);
            levelMap.put(data.level(), updated);
        }
    }

    private static void ensureBaseTradesCaptured() {
        if (!BASE_TRADES.isEmpty()) {
            return;
        }
        for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> entry
                : VillagerTrades.TRADES.entrySet()) {
            BASE_TRADES.put(entry.getKey(), copyTradeLevels(entry.getValue()));
        }
    }

    private static Int2ObjectMap<VillagerTrades.ItemListing[]> copyTradeLevels(
            Int2ObjectMap<VillagerTrades.ItemListing[]> source) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> copy = new Int2ObjectOpenHashMap<>();
        for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> lvl : source.int2ObjectEntrySet()) {
            VillagerTrades.ItemListing[] arr = lvl.getValue();
            copy.put(lvl.getIntKey(), Arrays.copyOf(arr, arr.length));
        }
        return copy;
    }

    private static void restoreBaseTrades() {
        for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> entry
                : BASE_TRADES.entrySet()) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> live = VillagerTrades.TRADES.get(entry.getKey());
            if (live == null) {
                continue;
            }
            for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> lvl : entry.getValue().int2ObjectEntrySet()) {
                VillagerTrades.ItemListing[] baseArr = lvl.getValue();
                live.put(lvl.getIntKey(), Arrays.copyOf(baseArr, baseArr.length));
            }
        }
    }
}
