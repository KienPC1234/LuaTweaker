package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds bulk world editing, patterns, structure placement and console commands. */
final class WorldBulkBinding {

    private WorldBulkBinding() {
    }

    static void bindWorkspaceBulk(ILuaEngine engine, ILuaTable workspaceTable) {
        bindFillReplace(engine, workspaceTable);
        bindPlaceStructure(engine, workspaceTable);

        // ===== Server console command (ExecuteCommand("give ...")) =====
        workspaceTable.rawset("ExecuteCommand", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .executeCommand(args[off].asString()));
        });
        workspaceTable.rawset("executeCommand", workspaceTable.rawget("ExecuteCommand"));

        bindCheckPattern(engine, workspaceTable);
        bindFindBlocks(engine, workspaceTable);
    }

    private static void bindFillReplace(ILuaEngine engine, ILuaTable workspaceTable) {
        // ===== Bulk world editing (FillBlocks / ReplaceBlocks) =====
        // FillBlocks(x1,y1,z1,x2,y2,z2,blockId,[properties]) or with a dimension prefix.
        // Returns the number of blocks set, or -1 when the platform rejected the
        // operation (unknown block, invalid region, or over the platform cap).
        workspaceTable.rawset("FillBlocks", args -> {
            WorldStateBinding.DimensionCoords coords = WorldStateBinding.readDimensionCoords(args);
            if (!InteractionBindingSupport.hasMinArgs(args, coords.off(), 7)) {
                return engine.wrapNumber(-1);
            }
            String dimension = coords.dimension();
            int off = coords.off();
            int x1 = args[off].asInt();
            int y1 = args[off + 1].asInt();
            int z1 = args[off + 2].asInt();
            int x2 = args[off + 3].asInt();
            int y2 = args[off + 4].asInt();
            int z2 = args[off + 5].asInt();
            String blockId = args[off + 6].asString();
            java.util.Map<String, Object> properties = null;
            if (InteractionBindingSupport.hasMinArgs(args, off, 8) && args[off + 7].isTable()) {
                properties = InteractionLuaBinding.toJavaMap(args[off + 7].asTable());
            }
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .fillBlocks(dimension, x1, y1, z1, x2, y2, z2, blockId, properties));
        });
        workspaceTable.rawset("fillBlocks", workspaceTable.rawget("FillBlocks"));

        // ReplaceBlocks(x1,y1,z1,x2,y2,z2,fromId,toId) or with a dimension prefix.
        workspaceTable.rawset("ReplaceBlocks", args -> {
            WorldStateBinding.DimensionCoords coords = WorldStateBinding.readDimensionCoords(args);
            if (!InteractionBindingSupport.hasMinArgs(args, coords.off(), 8)) {
                return engine.wrapNumber(-1);
            }
            String dimension = coords.dimension();
            int off = coords.off();
            int x1 = args[off].asInt();
            int y1 = args[off + 1].asInt();
            int z1 = args[off + 2].asInt();
            int x2 = args[off + 3].asInt();
            int y2 = args[off + 4].asInt();
            int z2 = args[off + 5].asInt();
            String fromId = args[off + 6].asString();
            String toId = args[off + 7].asString();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .replaceBlocks(dimension, x1, y1, z1, x2, y2, z2, fromId, toId));
        });
        workspaceTable.rawset("replaceBlocks", workspaceTable.rawget("ReplaceBlocks"));
    }

    private static void bindPlaceStructure(ILuaEngine engine, ILuaTable workspaceTable) {
        // ===== NBT structure placement (PlaceStructure) =====
        // PlaceStructure(templateId, x, y, z, [rotationDegrees]) or with a
        // dimension prefix. Loads the NBT template (data/<ns>/structure/<path>.nbt)
        // from the datapack and places it; the Lua mod decides when/where.
        workspaceTable.rawset("PlaceStructure", args -> {
            int off = LuaBinder.getOffset(args);
            // The first argument is always a string (template id); the prefix
            // form has ANOTHER string as the second argument (the dimension).
            // NOTE: Lua 5.2 isstring() is true for numbers too, so the strict
            // Java instanceof check is required here (same as FillBlocks).
            int dimOff = -1;
            if (InteractionBindingSupport.hasMinArgs(args, off, 2)
                    && args[off + 1].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (!InteractionBindingSupport.hasMinArgs(args, off, 4)) {
                return engine.wrapBoolean(false);
            }
            String dimension = dimOff >= 0 ? args[dimOff].asString()
                    : InteractionBindingSupport.DEFAULT_DIMENSION;
            String templateId = args[off].asString();
            int x = args[off + 1].asInt();
            int y = args[off + 2].asInt();
            int z = args[off + 3].asInt();
            int rotation = InteractionBindingSupport.hasMinArgs(args, off, 5) ? args[off + 4].asInt() : 0;
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .placeStructure(dimension, templateId, x, y, z, rotation));
        });
        workspaceTable.rawset("placeStructure", workspaceTable.rawget("PlaceStructure"));
    }

    private static void bindCheckPattern(ILuaEngine engine, ILuaTable workspaceTable) {
        // CheckPattern(x, y, z, patternTable) -> boolean
        workspaceTable.rawset("CheckPattern", args -> {
            InteractionBindingSupport.CoordinateArgs pos =
                    InteractionParse.readCoordinates(args, "World:CheckPattern");
            if (pos == null || pos.cursor() >= args.length || !args[pos.cursor()].isTable()) {
                return engine.wrapBoolean(false);
            }
            java.util.List<java.util.Map<String, Object>> pattern =
                    readPatternList(args[pos.cursor()].asTable());
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction().checkPattern(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), pattern));
        });
        workspaceTable.rawset("checkPattern", workspaceTable.rawget("CheckPattern"));
    }

    private static java.util.List<java.util.Map<String, Object>> readPatternList(ILuaTable patternTable) {
        java.util.List<java.util.Map<String, Object>> pattern = new java.util.ArrayList<>();
        for (int i = 1; ; i++) {
            ILuaValue item = patternTable.rawget(i);
            if (item == null || item.isNil() || !item.isTable()) {
                break;
            }
            pattern.add(readPatternEntry(item.asTable()));
        }
        return pattern;
    }

    private static java.util.Map<String, Object> readPatternEntry(ILuaTable entry) {
        java.util.Map<String, Object> map = new java.util.concurrent.ConcurrentHashMap<>();
        map.put("dx", readPatternAxis(entry, "dx", "DX",
                InteractionBindingSupport.KEY_X_LOWER, InteractionBindingSupport.KEY_X));
        map.put("dy", readPatternAxis(entry, "dy", "DY",
                InteractionBindingSupport.KEY_Y_LOWER, InteractionBindingSupport.KEY_Y));
        map.put("dz", readPatternAxis(entry, "dz", "DZ",
                InteractionBindingSupport.KEY_Z_LOWER, InteractionBindingSupport.KEY_Z));
        ILuaValue block = entry.rawget(InteractionBindingSupport.BLOCK_KEY);
        if (block.isNil()) {
            block = entry.rawget(InteractionBindingSupport.BLOCK_PROP);
        }
        if (!block.isNil()) {
            map.put(InteractionBindingSupport.BLOCK_KEY, block.asString());
        }
        ILuaValue tag = entry.rawget("tag");
        if (tag.isNil()) {
            tag = entry.rawget("Tag");
        }
        if (!tag.isNil()) {
            map.put("tag", tag.asString());
        }
        return map;
    }

    private static int readPatternAxis(ILuaTable entry, String... keys) {
        for (String key : keys) {
            ILuaValue val = entry.rawget(key);
            if (!val.isNil()) {
                return val.asInt();
            }
        }
        return 0;
    }

    private static void bindFindBlocks(ILuaEngine engine, ILuaTable workspaceTable) {
        // FindBlocks([dim, ] x, y, z, radius, filter [, limit]) or (pos, radius, filter [, limit])
        workspaceTable.rawset("FindBlocks", args -> {
            InteractionBindingSupport.CoordinateArgs pos = InteractionParse.readCoordinates(args, "World:FindBlocks");
            if (pos == null || pos.cursor() >= args.length) {
                return engine.createTable();
            }
            int cursor = pos.cursor();
            int radius = args[cursor++].asInt();
            if (cursor >= args.length || !args[cursor].isString()) {
                return engine.createTable();
            }
            String filter = args[cursor++].asString();
            int limit = cursor < args.length ? args[cursor].asInt() : 64;
            java.util.List<java.util.Map<String, Object>> list =
                    com.luatweaker.api.pal.Platform.getInteraction()
                            .findBlocks(pos.dimension(), pos.x(), pos.y(), pos.z(), radius, filter, limit);
            return InteractionLuaBinding.wrapValue(engine, list);
        });
        workspaceTable.rawset("findBlocks", workspaceTable.rawget("FindBlocks"));
    }
}

