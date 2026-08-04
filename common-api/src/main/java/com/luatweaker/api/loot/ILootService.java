package com.luatweaker.api.loot;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@LuaDoc(description = "Service for manipulating loot tables: mob drops, chest loot, fishing, block drops.")
public interface ILootService {

    @LuaDoc(
        description = "Add or modify a mob drop.",
        params = {"entityId: string - entity type ID (e.g. 'minecraft:zombie')",
                  "itemId: string - item to drop (e.g. 'minecraft:diamond')",
                  "options: table - {chance, minCount, maxCount, lootingBonus}"},
        returnType = "void"
    )
    void addMobDrop(
            @NotNull String entityId,
            @NotNull String itemId,
            double chance,
            @LuaDefault("1") int minCount,
            @LuaDefault("1") int maxCount,
            @LuaDefault("0") int lootingBonus
    );

    @LuaDoc(
        description = "Remove a specific drop from a mob's loot table.",
        params = {"entityId: string - entity type ID", "itemId: string - item to remove"},
        returnType = "boolean"
    )
    boolean removeMobDrop(@NotNull String entityId, @NotNull String itemId);

    @LuaDoc(
        description = "Add loot to a chest/container loot table.",
        params = {"tableId: string - loot table path (e.g. 'minecraft:chests/simple_dungeon')",
                  "itemId: string - item to add",
                  "chance: number - drop chance (0.0-1.0)",
                  "minCount: number", "maxCount: number"},
        returnType = "void"
    )
    void addChestLoot(
            @NotNull String tableId,
            @NotNull String itemId,
            double chance,
            @LuaDefault("1") int minCount,
            @LuaDefault("1") int maxCount
    );

    @LuaDoc(
        description = "Remove an item from a chest loot table.",
        params = {"tableId: string", "itemId: string"},
        returnType = "boolean"
    )
    boolean removeChestLoot(@NotNull String tableId, @NotNull String itemId);

    @LuaDoc(
        description = "Set the drop for a block when broken.",
        params = {"blockId: string - block ID",
                  "itemId: string - item to drop",
                  "fortuneBonus: number - extra per Fortune level",
                  "silkTouchDrop: string - alternative drop with Silk Touch (nil for same)"},
        returnType = "void"
    )
    void setBlockDrop(
            @NotNull String blockId,
            @NotNull String itemId,
            @LuaDefault("0") int fortuneBonus,
            @Nullable String silkTouchDrop
    );

    @LuaDoc(
        description = "Add an item to the fishing loot table.",
        params = {"itemId: string", "chance: number", "category: string - 'FISH', 'JUNK', or 'TREASURE'"},
        returnType = "void"
    )
    void addFishingLoot(
            @NotNull String itemId,
            double chance,
            @LuaDefault("TREASURE") @NotNull String category
    );

    @LuaDoc(
        description = "Get the raw LootTable object for direct Java access via Dynamic Bridge.",
        params = {"tableId: string - loot table resource location"},
        returnType = "object|nil"
    )
    @Nullable
    Object getTable(@NotNull String tableId);

    @LuaDoc(
        description = "Get all pending loot modifications as a summary.",
        returnType = "table"
    )
    @NotNull
    java.util.Map<String, Object> getModifications();

    @LuaDoc(
        description = "Clear all pending loot modifications.",
        returnType = "void"
    )
    void clearAll();
}
