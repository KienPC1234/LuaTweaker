package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

import java.util.Map;

/**
 * KubeJS-style Virtual DataPack service.
 *
 * <p>All data registered here is served 100% from RAM — no files are written to disk.
 * Supports any mod namespace (minecraft, create, mekanism, luatweaker, ...).
 *
 * <p>Examples:
 * <pre>
 *   -- Patch vanilla loot table
 *   datapack:addLootTable("minecraft:blocks/diamond_ore", json)
 *
 *   -- Add recipe for another mod
 *   datapack:addJsonRecipe("create:my_recipe", json)
 *
 *   -- Overwrite any raw data path
 *   datapack:addData("minecraft/tags/block/mineable/pickaxe.json", json)
 * </pre>
 */
@LuaDoc(description = "Virtual DataPack service (KubeJS-style). Injects data into Minecraft at load time without writing files to disk. Supports any namespace.")
public interface IDatapackService {

    @LuaDoc(description = "Registers a crafting recipe JSON. Supports any namespace, e.g. 'minecraft:my_recipe' or 'create:cool_recipe'.",
            params = {"recipeId: string — ResourceLocation e.g. 'luatweaker:ruby_gear'", "jsonContent: string — raw JSON"})
    void addJsonRecipe(String recipeId, String jsonContent);

    @LuaDoc(description = "Registers a loot table JSON. Supports any namespace, e.g. 'minecraft:blocks/diamond_ore' to patch vanilla.",
            params = {"path: string — ResourceLocation e.g. 'luatweaker:blocks/ruby_ore'", "jsonContent: string — raw JSON"})
    void addLootTable(String path, String jsonContent);

    @LuaDoc(description = "Registers an advancement JSON under any namespace.",
            params = {"path: string — ResourceLocation", "jsonContent: string — raw JSON"})
    void addAdvancement(String path, String jsonContent);

    @LuaDoc(description = "Registers an .mcfunction file under any namespace.",
            params = {"path: string — ResourceLocation", "commands: string — mcfunction commands"})
    void addFunction(String path, String commands);

    @LuaDoc(description = "Registers any raw data file by its relative path inside the 'data/' directory. e.g. 'minecraft/tags/block/mineable/pickaxe.json'.",
            params = {"relPath: string — path relative to data/ directory", "jsonContent: string — raw JSON"})
    void addData(String relPath, String jsonContent);

    @LuaDoc(description = "Creates or appends to a tag JSON (item, block, entity, etc.) under any namespace. e.g. datapack:addTag('item', 'luatweaker:ruby_items', {'luatweaker:custom_ruby','luatweaker:ruby_block'}) or datapack:addTag('block', 'minecraft:beacon_base_blocks', {'luatweaker:ruby_block'}).",
            params = {"tagType: string — 'item', 'block', 'entity_type', 'fluid', etc.", "tagId: string — ResourceLocation of the tag e.g. 'luatweaker:ruby_items'", "values: table — list of ResourceLocation strings to include"})
    void addTag(String tagType, String tagId, java.util.List<String> values);

    /** Returns a live, unmodifiable view of all registered virtual data paths → content. */
    Map<String, String> getVirtualFiles();

    /** Wipes every virtual file; a reload must rebuild the pack from scratch so stale entries disappear. */
    void clear();
}
