package com.luatweaker.platform.loot;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.loot.LootServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class NeoForgeLootProvider {

    private final LootServiceImpl lootService;
    private final IDatapackService datapackService;

    public NeoForgeLootProvider(@NotNull LootServiceImpl lootService, @NotNull IDatapackService datapackService) {
        this.lootService = lootService;
        this.datapackService = datapackService;
    }

    public void applyAll() {
        applyMobDrops();
        applyChestLoot();
        applyBlockDrops();
        applyFishingLoot();
        applyRemovals();

        Map<String, Object> summary = lootService.getModifications();
        LuaTweakerLog.get().info(LogStage.SYSTEM,
            "[LootProvider] Applied: " + summary.get("mobDrops") + " mob drops, "
            + summary.get("chestLoot") + " chest loot, "
            + summary.get("blockDrops") + " block drops, "
            + summary.get("fishingLoot") + " fishing entries, "
            + summary.get("removals") + " removals");
    }

    private void applyMobDrops() {
        for (Map.Entry<String, List<LootServiceImpl.MobDropEntry>> entry : lootService.getPendingMobDrops().entrySet()) {
            String entityId = entry.getKey();
            List<LootServiceImpl.MobDropEntry> drops = entry.getValue();

            String entityPath = entityId.replace(":", "/entities/");
            String json = buildEntityLootTableJson(drops);
            datapackService.addLootTable(entityPath, json);
        }
    }

    private void applyChestLoot() {
        for (Map.Entry<String, List<LootServiceImpl.ChestLootEntry>> entry : lootService.getPendingChestLoot().entrySet()) {
            String tableId = entry.getKey();
            List<LootServiceImpl.ChestLootEntry> entries = entry.getValue();

            String tablePath = tableId.replace(":", "/");
            if (!tablePath.contains("/")) {
                tablePath = "minecraft/" + tablePath;
            }
            String json = buildChestLootTableJson(entries);
            datapackService.addLootTable(tablePath, json);
        }
    }

    private void applyBlockDrops() {
        for (Map.Entry<String, LootServiceImpl.BlockDropEntry> entry : lootService.getPendingBlockDrops().entrySet()) {
            String blockId = entry.getKey();
            LootServiceImpl.BlockDropEntry drop = entry.getValue();

            String blockPath = blockId.replace(":", "/blocks/");
            String json = buildBlockLootTableJson(drop);
            datapackService.addLootTable(blockPath, json);
        }
    }

    private void applyFishingLoot() {
        List<LootServiceImpl.FishingLootEntry> entries = lootService.getPendingFishingLoot();
        if (entries.isEmpty()) return;

        // Colon form required: 'minecraft:gameplay/fishing' -> data/minecraft/loot_table/gameplay/fishing.json
        String json = buildFishingLootTableJson(entries);
        datapackService.addLootTable("minecraft:gameplay/fishing", json);
    }

    private void applyRemovals() {
        for (LootServiceImpl.RemovalEntry removal : lootService.getPendingRemovals()) {
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[LootProvider] Removal registered: " + removal.targetId() + " -> " + removal.itemId());
        }
    }

    private String buildEntityLootTableJson(List<LootServiceImpl.MobDropEntry> drops) {
        StringBuilder pools = new StringBuilder();
        for (int i = 0; i < drops.size(); i++) {
            LootServiceImpl.MobDropEntry drop = drops.get(i);
            if (i > 0) pools.append(",");

            StringBuilder functions = new StringBuilder();
            if (drop.minCount() != 1 || drop.maxCount() != 1) {
                functions.append(",\"functions\":[{")
                    .append("\"function\":\"minecraft:set_count\",")
                    .append("\"count\":{")
                    .append("\"type\":\"minecraft:uniform\",")
                    .append("\"min\":").append(drop.minCount()).append(",")
                    .append("\"max\":").append(drop.maxCount())
                    .append("}}]");
            }

            StringBuilder conditions = new StringBuilder();
            if (drop.chance() < 1.0) {
                conditions.append(",\"conditions\":[{")
                    .append("\"condition\":\"minecraft:random_chance\",")
                    .append("\"chance\":").append(drop.chance())
                    .append("}]");
            }

            pools.append("{")
                .append("\"rolls\":1,")
                .append("\"entries\":[{")
                .append("\"type\":\"minecraft:item\",")
                .append("\"name\":\"").append(drop.itemId()).append("\"")
                .append(functions)
                .append(conditions)
                .append("}]")
                .append("}");
        }

        return "{\"type\":\"minecraft:entity\",\"pools\":[" + pools + "]}";
    }

    private String buildChestLootTableJson(List<LootServiceImpl.ChestLootEntry> entries) {
        StringBuilder poolEntries = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            LootServiceImpl.ChestLootEntry entry = entries.get(i);
            if (i > 0) poolEntries.append(",");

            StringBuilder functions = new StringBuilder();
            if (entry.minCount() != 1 || entry.maxCount() != 1) {
                functions.append(",\"functions\":[{")
                    .append("\"function\":\"minecraft:set_count\",")
                    .append("\"count\":{")
                    .append("\"type\":\"minecraft:uniform\",")
                    .append("\"min\":").append(entry.minCount()).append(",")
                    .append("\"max\":").append(entry.maxCount())
                    .append("}}]");
            }

            StringBuilder conditions = new StringBuilder();
            if (entry.chance() < 1.0) {
                conditions.append(",\"conditions\":[{")
                    .append("\"condition\":\"minecraft:random_chance\",")
                    .append("\"chance\":").append(entry.chance())
                    .append("}]");
            }

            poolEntries.append("{")
                .append("\"type\":\"minecraft:item\",")
                .append("\"name\":\"").append(entry.itemId()).append("\"")
                .append(functions)
                .append(conditions)
                .append("}");
        }

        return "{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":[" + poolEntries + "]}]}";
    }

    private String buildBlockLootTableJson(LootServiceImpl.BlockDropEntry drop) {
        StringBuilder pool = new StringBuilder();
        pool.append("{\"rolls\":1,\"entries\":[{")
            .append("\"type\":\"minecraft:item\",")
            .append("\"name\":\"").append(drop.itemId()).append("\"");

        StringBuilder functions = new StringBuilder();
        if (drop.fortuneBonus() > 0) {
            functions.append(",{\"function\":\"minecraft:apply_bonus\",")
                .append("\"enchantment\":\"minecraft:fortune\",")
                .append("\"formula\":\"minecraft:uniform_bonus_count\",")
                .append("\"parameters\":{\"bonusMultiplier\":").append(drop.fortuneBonus()).append("}}");
        }

        if (functions.length() > 0) {
            pool.append(",\"functions\":[").append(functions.substring(1)).append("]");
        }
        pool.append("}]");

        if (drop.silkTouchDrop() != null) {
            pool.append(",\"conditions\":[{")
                .append("\"condition\":\"minecraft:match_tool\",")
                .append("\"predicate\":{\"enchantments\":[{")
                .append("\"enchantment\":\"minecraft:silk_touch\",")
                .append("\"levels\":{\"min\":1}}]}}]");
        }

        pool.append("}");

        return "{\"type\":\"minecraft:block\",\"pools\":[" + pool + "]}";
    }

    private String buildFishingLootTableJson(List<LootServiceImpl.FishingLootEntry> entries) {
        StringBuilder poolEntries = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            LootServiceImpl.FishingLootEntry entry = entries.get(i);
            if (i > 0) poolEntries.append(",");

            poolEntries.append("{")
                .append("\"type\":\"minecraft:item\",")
                .append("\"name\":\"").append(entry.itemId()).append("\",")
                .append("\"weight\":").append((int)(entry.chance() * 100))
                .append("}");
        }

        return "{\"type\":\"minecraft:fishing\",\"pools\":[{\"rolls\":1,\"entries\":[" + poolEntries + "]}]}";
    }
}
