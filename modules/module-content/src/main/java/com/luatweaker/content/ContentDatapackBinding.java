package com.luatweaker.content;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Registers the "datapack" table, its PascalCase aliases, and the global "tag" shortcut table. */
final class ContentDatapackBinding {

    private ContentDatapackBinding() {
    }

    static ILuaTable registerDatapackBindings(ILuaEngine engine, IDatapackService datapackService) {
        ILuaTable datapackTable = engine.createTable();

        datapackTable.rawset("addJsonRecipe", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("datapack:addJsonRecipe requires (recipeId, jsonString)");
            }
            datapackService.addJsonRecipe(args[off].asString(), args[off + 1].asString());
            return null;
        });

        datapackTable.rawset("addLootTable", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("datapack:addLootTable requires (path, jsonString)");
            }
            datapackService.addLootTable(args[off].asString(), args[off + 1].asString());
            return null;
        });

        datapackTable.rawset("addAdvancement", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("datapack:addAdvancement requires (path, jsonString)");
            }
            datapackService.addAdvancement(args[off].asString(), args[off + 1].asString());
            return null;
        });

        datapackTable.rawset("addFunction", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("datapack:addFunction requires (path, commands)");
            }
            datapackService.addFunction(args[off].asString(), args[off + 1].asString());
            return null;
        });

        datapackTable.rawset("addData", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("datapack:addData requires (relPath, jsonString)");
            }
            datapackService.addData(args[off].asString(), args[off + 1].asString());
            return null;
        });

        // datapack:addTag("item", "luatweaker:ruby_items", {"luatweaker:custom_ruby", "luatweaker:ruby_block"})
        // datapack:addTag("block", "minecraft:beacon_base_blocks", {"luatweaker:ruby_block"})
        bindDatapackTag(datapackTable, datapackService);

        // PascalCase aliases (AddData, AddLootTable, ...) so the Datapack API
        // follows the service naming convention; both styles work in Lua.
        aliasDatapackMethods(datapackTable);
        return datapackTable;
    }

    private static void bindDatapackTag(ILuaTable datapackTable, IDatapackService datapackService) {
        datapackTable.rawset("addTag", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 3)) {
                throw new IllegalArgumentException("datapack:addTag requires (tagType, tagId, valuesTable)");
            }
            String tagType = args[off].asString();
            String tagId = args[off + 1].asString();
            java.util.List<String> values = new java.util.ArrayList<>();
            // args[off + 2] may be an ILuaTable — iterate sequential keys 1..n
            ILuaValue valArg = args[off + 2];
            if (valArg instanceof ILuaTable valTable) {
                for (int i = 1; ; i++) {
                    ILuaValue entry = valTable.rawget(i);
                    if (entry == null || entry.isNil()) {
                        break;
                    }
                    values.add(entry.asString());
                }
            }
            if (!values.isEmpty()) {
                datapackService.addTag(tagType, tagId, values);
            }
            return null;
        });
    }

    private static void aliasDatapackMethods(ILuaTable datapackTable) {
        // PascalCase aliases (AddData, AddLootTable, ...) so the Datapack API
        // follows the service naming convention; both styles work in Lua.
        for (String method : new String[]{"addJsonRecipe", "addLootTable", "addAdvancement",
                "addFunction", "addData", "addTag"}) {
            ILuaValue fn = datapackTable.rawget(method);
            if (fn != null && fn.isFunction()) {
                datapackTable.rawset(Character.toUpperCase(method.charAt(0)) + method.substring(1), fn);
            }
        }
    }

    static ILuaTable registerTagTable(ILuaEngine engine, IDatapackService datapackService) {
        // Convenient global 'tag' shortcut API:
        //   tag.item("c:gems", "luatweaker:custom_ruby")
        //   tag.block("minecraft:beacon_base_blocks", {"luatweaker:ruby_block"})
        ILuaTable tagTable = engine.createTable();
        tagTable.rawset(ContentBindingSupport.TAG_TYPE_ITEM, args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("tag.item requires (tagId, values)");
            }
            ContentBindingSupport.addTagFromLua(datapackService, ContentBindingSupport.TAG_TYPE_ITEM,
                    args[off].asString(),
                    ContentBindingSupport.hasMinArgs(args, off, 2) ? args[off + 1] : null);
            return null;
        });
        tagTable.rawset(ContentBindingSupport.TAG_TYPE_BLOCK, args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("tag.block requires (tagId, values)");
            }
            ContentBindingSupport.addTagFromLua(datapackService, ContentBindingSupport.TAG_TYPE_BLOCK,
                    args[off].asString(),
                    ContentBindingSupport.hasMinArgs(args, off, 2) ? args[off + 1] : null);
            return null;
        });
        tagTable.rawset("add", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("tag.add requires (type, tagId, values)");
            }
            ContentBindingSupport.addTagFromLua(datapackService, args[off].asString(), args[off + 1].asString(),
                    ContentBindingSupport.hasMinArgs(args, off, 3) ? args[off + 2] : null);
            return null;
        });

        // Add __call metamethod so tag(...) function call works for both tag ingredients AND tag registration!
        ILuaTable tagMeta = engine.createTable();
        tagMeta.rawset("__call", args -> handleTagCall(engine, datapackService, args));
        tagTable.setMetatable(tagMeta);
        return tagTable;
    }

    private static ILuaValue handleTagCall(ILuaEngine engine, IDatapackService datapackService, ILuaValue... args) {
        // Lua passes the table itself first, so the call arguments start at index 1.
        if (isTagRegistrationCall(args)) {
            String type = args[1].asString();
            String tagId = ContentBindingSupport.hasMinArgs(args, 0, 3) ? args[2].asString() : "";
            ILuaValue val = ContentBindingSupport.hasMinArgs(args, 0, 4) ? args[3] : null;
            if (!tagId.isEmpty()) {
                ContentBindingSupport.addTagFromLua(datapackService, type, tagId, val);
            }
            return null;
        }

        // Single argument recipe ingredient tag call: tag("#minecraft:logs") or tag("minecraft:logs")
        if (ContentBindingSupport.hasMinArgs(args, 0, 2) && !args[1].isNil()) {
            String tagStr = args[1].asString();
            if (!tagStr.startsWith("#")) {
                tagStr = "#" + tagStr;
            }
            return engine.toLuaValue(new com.luatweaker.api.wrapper.IngredientWrapper(tagStr));
        }

        return null;
    }

    private static boolean isTagRegistrationCall(ILuaValue... args) {
        return ContentBindingSupport.hasMinArgs(args, 0, 3) && !args[1].isNil()
                && (ContentBindingSupport.TAG_TYPE_ITEM.equalsIgnoreCase(args[1].asString())
                || ContentBindingSupport.TAG_TYPE_BLOCK.equalsIgnoreCase(args[1].asString())
                || ContentBindingSupport.TAG_TYPE_FLUID.equalsIgnoreCase(args[1].asString())
                || ContentBindingSupport.TAG_TYPE_ENTITY.equalsIgnoreCase(args[1].asString()));
    }
}
