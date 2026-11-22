package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
            params = {"recipeId: string — ResourceLocation e.g. 'luatweaker:ruby_gear'", LuaDocFragments.Content.JSON_CONTENT})
    void addJsonRecipe(String recipeId, String jsonContent);

    @LuaDoc(description = "Registers a loot table JSON. Supports any namespace, e.g. 'minecraft:blocks/diamond_ore' to patch vanilla.",
            params = {"path: string — ResourceLocation e.g. 'luatweaker:blocks/ruby_ore'", LuaDocFragments.Content.JSON_CONTENT})
    void addLootTable(String path, String jsonContent);

    @LuaDoc(description = "Registers an advancement JSON under any namespace.",
            params = {"path: string — ResourceLocation", LuaDocFragments.Content.JSON_CONTENT})
    void addAdvancement(String path, String jsonContent);

    @LuaDoc(description = "Registers an .mcfunction file under any namespace.",
            params = {"path: string — ResourceLocation", "commands: string — mcfunction commands"})
    void addFunction(String path, String commands);

    @LuaDoc(description = "Registers any raw data file by its relative path inside the 'data/' directory. e.g. 'minecraft/tags/block/mineable/pickaxe.json'.",
            params = {"relPath: string — path relative to data/ directory", LuaDocFragments.Content.JSON_CONTENT})
    void addData(String relPath, String jsonContent);

    @LuaDoc(description = "Creates or appends to a tag JSON (item, block, entity, etc.) under any namespace. e.g. datapack:addTag('item', 'luatweaker:ruby_items', {'luatweaker:custom_ruby','luatweaker:ruby_block'}) or datapack:addTag('block', 'minecraft:beacon_base_blocks', {'luatweaker:ruby_block'}).",
            params = {"tagType: string — 'item', 'block', 'entity_type', 'fluid', etc.", "tagId: string — ResourceLocation of the tag e.g. 'luatweaker:ruby_items'", "values: table — list of ResourceLocation strings to include"})
    void addTag(String tagType, String tagId, List<String> values);

    @LuaDoc(description = "Returns true when a virtual file exists at relPath (e.g. 'minecraft/recipe/iron.json' or 'data/minecraft/recipe/iron.json').",
            params = {"relPath: string — path relative to data/ or assets/"}, returnType = "boolean")
    boolean hasData(@NotNull String relPath);

    @LuaDoc(description = "Reads a virtual file registered by Lua (addData/addJsonRecipe/...). Returns nil when missing. Use getLoaded to read vanilla/other-mod datapacks.",
            params = {"relPath: string — path relative to data/ or assets/"}, returnType = "string | nil")
    @Nullable
    String getData(@NotNull String relPath);

    @LuaDoc(description = "Deletes a virtual file registered by Lua. Returns true when an entry was removed.",
            params = {"relPath: string — path relative to data/ or assets/"}, returnType = "boolean")
    boolean removeData(@NotNull String relPath);

    @LuaDoc(description = "Lists virtual file keys starting with prefix ('' lists all). Keys look like 'data/<ns>/<path>' or 'assets/<ns>/<path>'.",
            params = {"prefix: string — e.g. '', 'data/mymod/', 'data/minecraft/recipe/'"}, returnType = "table")
    @NotNull
    List<String> listData(@NotNull String prefix);

    @LuaDoc(description = "Lists namespaces registered in virtual files for a pack dir ('data' or 'assets').",
            params = {"packDir: string — 'data' or 'assets'"}, returnType = "table")
    @NotNull
    List<String> listNamespaces(@NotNull String packDir);

    /** Returns a live, unmodifiable view of all registered virtual data paths → content. */
    Map<String, String> getVirtualFiles();

    /** Wipes every virtual file; a reload must rebuild the pack from scratch so stale entries disappear. */
    void clear();
}
