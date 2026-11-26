package com.luatweaker.common.platform;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import dev.architectury.hooks.item.tool.AxeItemHooks;
import dev.architectury.hooks.item.tool.HoeItemHooks;
import dev.architectury.hooks.item.tool.ShovelItemHooks;
import dev.architectury.registry.level.entity.trade.SimpleTrade;
import dev.architectury.registry.level.entity.trade.TradeRegistry;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool block-transformation and Wandering Trader helpers for {@link ArchitecturyServiceImpl}.
 */
final class ArchitecturyRegistryOps {

    private static final int DEFAULT_COUNT = 1;
    private static final int DEFAULT_MAX_TRADES = 8;
    private static final int DEFAULT_XP = 2;
    private static final float DEFAULT_PRICE_MULTIPLIER = 0.05f;
    private static final String PREFIX = "[Architectury] ";
    private static final String KIND_STRIP = "strip";
    private static final String KIND_FLATTEN = "flatten";
    private static final String KIND_TILL = "till";
    private static final Map<String, String> TOOL_KINDS = Map.of(
            KIND_STRIP, KIND_STRIP,
            "axe", KIND_STRIP,
            KIND_FLATTEN, KIND_FLATTEN,
            "shovel", KIND_FLATTEN,
            KIND_TILL, KIND_TILL,
            "hoe", KIND_TILL
    );

    private static final Set<String> REGISTERED_TRANSFORMS = ConcurrentHashMap.newKeySet();
    private static final Set<String> REGISTERED_TRADES = ConcurrentHashMap.newKeySet();

    private ArchitecturyRegistryOps() {
    }

    static boolean addToolTransform(@Nullable String toolKind, @Nullable String inputId, @Nullable String resultId) {
        String canonical = toolKind == null ? null : TOOL_KINDS.get(toolKind.trim().toLowerCase(Locale.ROOT));
        Block input = ArchitecturyPlatformOps.resolveBlock(inputId);
        Block result = ArchitecturyPlatformOps.resolveBlock(resultId);
        if (canonical == null || input == null || result == null) {
            return false;
        }
        String key = canonical + "|" + inputId.trim() + "|" + resultId.trim();
        return switch (canonical) {
            case KIND_STRIP -> registerStrippable(input, result, key);
            case KIND_FLATTEN -> registerFlattenable(input, result, key);
            case KIND_TILL -> registerTillable(input, result, key);
            default -> false;
        };
    }

    private static boolean registerStrippable(Block input, Block result, String key) {
        if (!input.defaultBlockState().hasProperty(BlockStateProperties.AXIS)
                || !result.defaultBlockState().hasProperty(BlockStateProperties.AXIS)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    PREFIX + "addToolTransform('strip') requires both blocks to have AXIS property");
            return false;
        }
        if (REGISTERED_TRANSFORMS.add(key)) {
            AxeItemHooks.addStrippable(input, result);
        }
        return true;
    }

    private static boolean registerFlattenable(Block input, Block result, String key) {
        if (REGISTERED_TRANSFORMS.add(key)) {
            ShovelItemHooks.addFlattenable(input, result.defaultBlockState());
        }
        return true;
    }

    private static boolean registerTillable(Block input, Block result, String key) {
        if (REGISTERED_TRANSFORMS.add(key)) {
            HoeItemHooks.addTillable(input, HoeItem::onlyIfAirAbove, ctx -> {
            }, ctx -> result.defaultBlockState());
        }
        return true;
    }

    static boolean addWanderingTrade(@Nullable String buyId, @Nullable String sellId,
                                     @Nullable Map<String, Object> options) {
        Item buyItem = ArchitecturyPlatformOps.resolveItem(buyId);
        Item sellItem = ArchitecturyPlatformOps.resolveItem(sellId);
        if (buyItem == null || sellItem == null) {
            return false;
        }
        Map<String, Object> cfg = options == null ? Map.of() : options;
        int buyCount = Math.max(1, readInt(cfg, "BuyCount", "buyCount", DEFAULT_COUNT));
        int sellCount = Math.max(1, readInt(cfg, "SellCount", "sellCount", DEFAULT_COUNT));
        int maxTrades = Math.max(1, readInt(cfg, "MaxTrades", "maxTrades", DEFAULT_MAX_TRADES));
        int xp = Math.max(0, readInt(cfg, "Xp", "xp", DEFAULT_XP));
        float mult = Math.max(0.0f, (float) readDouble(cfg, "PriceMultiplier", "priceMultiplier",
                DEFAULT_PRICE_MULTIPLIER));
        boolean rare = readBool(cfg, "Rare", "rare");
        Item buy2Item = ArchitecturyPlatformOps.resolveItem(readString(cfg, "Buy2", "buy2"));
        int buy2Count = Math.max(1, readInt(cfg, "Buy2Count", "buy2Count", DEFAULT_COUNT));

        String key = rare + "|" + buyId.trim() + "x" + buyCount + "|" + buy2Item + "x" + buy2Count
                + "->" + sellId.trim() + "x" + sellCount;
        if (REGISTERED_TRADES.add(key)) {
            Optional<ItemCost> second = buy2Item != null
                    ? Optional.of(new ItemCost(buy2Item, buy2Count))
                    : Optional.empty();
            SimpleTrade trade = new SimpleTrade(
                    new ItemCost(buyItem, buyCount), second,
                    new ItemStack(sellItem, sellCount), maxTrades, xp, mult);
            TradeRegistry.registerTradeForWanderingTrader(rare, trade);
        }
        return true;
    }

    private static int readInt(Map<String, Object> map, String k1, String k2, int fallback) {
        Object val = map.getOrDefault(k1, map.get(k2));
        return val instanceof Number num ? num.intValue() : fallback;
    }

    private static double readDouble(Map<String, Object> map, String k1, String k2, double fallback) {
        Object val = map.getOrDefault(k1, map.get(k2));
        return val instanceof Number num ? num.doubleValue() : fallback;
    }

    private static boolean readBool(Map<String, Object> map, String k1, String k2) {
        Object val = map.getOrDefault(k1, map.get(k2));
        return val instanceof Boolean flag && flag;
    }

    @Nullable
    private static String readString(Map<String, Object> map, String k1, String k2) {
        Object val = map.getOrDefault(k1, map.get(k2));
        return val instanceof String str ? str : null;
    }
}
