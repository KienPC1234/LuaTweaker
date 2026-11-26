package com.luatweaker.api.platform;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@LuaDoc(description = "Cross-loader Architectury runtime metadata, mod registry, fuel, tool transforms, and wandering trade service.")
public interface IArchitecturyService {

    String TYPE_BOOLEAN = "boolean";

    @LuaDoc(description = "Returns the active mod loader identifier ('neoforge', 'fabric', or 'architectury').", returnType = "string")
    @NotNull String getLoader();

    @LuaDoc(description = "Returns the running Minecraft version string (e.g. '1.21.1').", returnType = "string")
    @NotNull String getMinecraftVersion();

    @LuaDoc(description = "Returns true when running inside a development workspace.", returnType = TYPE_BOOLEAN)
    boolean isDevelopmentEnvironment();

    @LuaDoc(
            description = "Checks whether a mod with the given mod ID is currently loaded.",
            params = {"modId: string"},
            returnType = TYPE_BOOLEAN
    )
    boolean isModLoaded(@NotNull String modId);

    @LuaDoc(description = "Returns the list of all loaded mod IDs reported by Architectury Platform.", returnType = "string[]")
    @NotNull List<String> getLoadedMods();

    @LuaDoc(
            description = "Returns metadata {Id, Name, Version, Description, Authors, License, Homepage?, Sources?, IssueTracker?} for a loaded mod, or nil when absent.",
            params = {"modId: string"},
            returnType = "table | nil"
    )
    @Nullable Map<String, Object> getModInfo(@NotNull String modId);

    @LuaDoc(
            description = "Registers a furnace burn duration in ticks for any vanilla or modded item via Architectury FuelRegistry.",
            params = {"itemId: string", "burnTicks: integer"},
            returnType = TYPE_BOOLEAN
    )
    boolean registerFuel(@NotNull String itemId, int burnTicks);

    @LuaDoc(
            description = "Returns the furnace burn time in ticks for the given item ID (0 when non-burnable or unknown).",
            params = {"itemId: string"},
            returnType = "integer"
    )
    int getBurnTime(@NotNull String itemId);

    @LuaDoc(
            description = "Registers a cross-loader tool-on-block transformation via Architectury hooks ('strip'/'axe', 'flatten'/'shovel', or 'till'/'hoe').",
            params = {"toolKind: string", "inputBlockId: string", "resultBlockId: string"},
            returnType = TYPE_BOOLEAN
    )
    boolean addToolTransform(@NotNull String toolKind, @NotNull String inputBlockId, @NotNull String resultBlockId);

    @LuaDoc(
            description = "Registers a cross-loader Wandering Trader offer via Architectury TradeRegistry. Options: {BuyCount, SellCount, MaxTrades, Xp, PriceMultiplier, Rare, Buy2, Buy2Count}.",
            params = {"buyItemId: string", "sellItemId: string", "[options: table]"},
            returnType = TYPE_BOOLEAN
    )
    boolean addWanderingTrade(@NotNull String buyItemId, @NotNull String sellItemId,
                              @LuaDefault("{}") @Nullable Map<String, Object> options);
}
