package com.luatweaker.platform.loot;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.loot.LootServiceImpl;
import com.luatweaker.loot.LootTableJsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Materializes the Lua-registered loot entries into virtual datapack loot tables.
 *
 * <p>Removals are applied to the service's pending registries before any JSON is built, so a
 * removed item is genuinely absent from the emitted table. Only entries registered through the
 * Loot API can be removed; vanilla drops are outside the scope of this provider (see
 * {@link LootServiceImpl#applyRemovalsToPending()}).
 */
public class NeoForgeLootProvider {

    private final LootServiceImpl lootService;
    private final IDatapackService datapackService;

    public NeoForgeLootProvider(@NotNull LootServiceImpl lootService, @NotNull IDatapackService datapackService) {
        this.lootService = lootService;
        this.datapackService = datapackService;
    }

    public void applyAll() {
        lootService.applyRemovalsToPending();

        applyMobDrops();
        applyChestLoot();
        applyBlockDrops();
        applyFishingLoot();

        Map<String, Object> summary = lootService.getModifications();
        LuaTweakerLog.get().info(LogStage.SYSTEM,
            "[LootProvider] Applied: " + summary.get("mobDrops") + " mob drops, "
            + summary.get("chestLoot") + " chest loot, "
            + summary.get("blockDrops") + " block drops, "
            + summary.get("fishingLoot") + " fishing entries, "
            + summary.get("removals") + " removals");
    }

    /**
     * Builds the loot-table id the game actually loads: {@code namespace:entities/path}.
     * Passing a slash-only path made {@code addLootTable} fall back to the luatweaker namespace,
     * so the file was written where vanilla never reads it.
     */
    private static String lootPath(String id, String category) {
        int colon = id.indexOf(':');
        String namespace = colon > 0 ? id.substring(0, colon) : "minecraft";
        String path = colon > 0 ? id.substring(colon + 1) : id;
        return namespace + ":" + category + "/" + path;
    }

    private void applyMobDrops() {
        for (Map.Entry<String, List<LootServiceImpl.MobDropEntry>> entry : lootService.getPendingMobDrops().entrySet()) {
            String entityId = entry.getKey();
            List<LootServiceImpl.MobDropEntry> drops = entry.getValue();
            if (drops.isEmpty()) {
                continue;
            }

            String entityPath = lootPath(entityId, "entities");
            String json = LootTableJsonBuilder.buildEntityLootTableJson(drops);
            datapackService.addLootTable(entityPath, json);
        }
    }

    private void applyChestLoot() {
        for (Map.Entry<String, List<LootServiceImpl.ChestLootEntry>> entry : lootService.getPendingChestLoot().entrySet()) {
            String tableId = entry.getKey();
            List<LootServiceImpl.ChestLootEntry> entries = entry.getValue();
            if (entries.isEmpty()) {
                continue;
            }

            // Keep the namespace separator: addLootTable() only picks a non-default
            // namespace from the colon form, otherwise the file lands under data/luatweaker.
            String tablePath = tableId.contains(":") ? tableId : "minecraft:" + tableId;
            String json = LootTableJsonBuilder.buildChestLootTableJson(entries);
            datapackService.addLootTable(tablePath, json);
        }
    }

    private void applyBlockDrops() {
        for (Map.Entry<String, LootServiceImpl.BlockDropEntry> entry : lootService.getPendingBlockDrops().entrySet()) {
            String blockId = entry.getKey();
            LootServiceImpl.BlockDropEntry drop = entry.getValue();

            String blockPath = lootPath(blockId, "blocks");
            String json = LootTableJsonBuilder.buildBlockLootTableJson(drop);
            datapackService.addLootTable(blockPath, json);
        }
    }

    /**
     * Each category lands in its own vanilla subtable
     * ({@code gameplay/fishing/fish|junk|treasure}) so the vanilla root router
     * keeps its weights, quality values and open-water treasure condition.
     * Only categories with Lua entries are emitted; the others (and the root)
     * stay vanilla. Within one category the Lua entries replace vanilla items.
     */
    private void applyFishingLoot() {
        List<LootServiceImpl.FishingLootEntry> entries = lootService.getPendingFishingLoot();
        if (entries.isEmpty()) return;

        Map<String, List<LootServiceImpl.FishingLootEntry>> byCategory = new LinkedHashMap<>();
        for (LootServiceImpl.FishingLootEntry entry : entries) {
            byCategory.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<String, List<LootServiceImpl.FishingLootEntry>> grouped : byCategory.entrySet()) {
            // Validated at registration: FISH, JUNK or TREASURE (see LootServiceImpl.addFishingLoot).
            String subTable = switch (grouped.getKey()) {
                case "JUNK" -> "junk";
                case "TREASURE" -> "treasure";
                default -> "fish";
            };
            String tableId = "minecraft:gameplay/fishing/" + subTable;
            String json = LootTableJsonBuilder.buildFishingLootTableJson(grouped.getValue(), tableId);
            datapackService.addLootTable(tableId, json);
        }
    }
}
