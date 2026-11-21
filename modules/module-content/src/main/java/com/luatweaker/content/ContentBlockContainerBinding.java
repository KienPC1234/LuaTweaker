package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds block container, slot and item-filter builder methods. */
final class ContentBlockContainerBinding {

    private ContentBlockContainerBinding() {
    }

    static void bindBlockDrops(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "drop", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                String itemId = args[off].asString();
                int minCount = ContentBindingSupport.hasMinArgs(args, off, 2)
                        ? args[off + 1].asInt() : ContentBindingSupport.DEFAULT_COUNT;
                int maxCount = ContentBindingSupport.hasMinArgs(args, off, 3) ? args[off + 2].asInt() : minCount;
                builder.drop(itemId, minCount, maxCount);
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "dropExperience", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                int minExp = args[off].asInt();
                int maxExp = ContentBindingSupport.hasMinArgs(args, off, 2) ? args[off + 1].asInt() : minExp;
                builder.dropExperience(minExp, maxExp);
            }
            return table;
        });
        bindBlockContainer(table, builder);
    }

    static void bindBlockContainer(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "container", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                int rows = args[off].asInt();
                int cols = ContentBindingSupport.hasMinArgs(args, off, 2)
                        ? args[off + 1].asInt() : ContentBindingSupport.DEFAULT_CONTAINER_COLUMNS;
                String dropMode = ContentBindingSupport.hasMinArgs(args, off, 3)
                        ? args[off + 2].asString() : ContentBindingSupport.DEFAULT_DROP_MODE;
                builder.container(rows, cols, dropMode);
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "containerUseDistance", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                builder.containerUseDistance(args[off].asDouble());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "containerTexture", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                builder.containerTexture(args[off].asString());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "containerTitle", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                builder.containerTitle(args[off].asString());
            }
            return table;
        });
    }

    static void bindBlockItemFilter(ILuaEngine engine, ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "itemFilter", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.itemFilter((itemId, count) -> {
                    if (!(count instanceof Number countNum)) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing block itemFilter callback: expected numeric count, got: " + count);
                        return false;
                    }
                    try {
                        ILuaValue allowed = engine.callFunction(func,
                                engine.wrapString(String.valueOf(itemId)),
                                engine.wrapNumber(countNum.doubleValue()));
                        return allowed.isNil() || allowed.asBoolean();
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing block itemFilter callback: " + e.getMessage());
                        return false;
                    }
                });
            }
            return table;
        });
    }

    static void bindBlockSlots(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "slotPosition", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 3)) {
                builder.slotPosition(args[off].asInt(), args[off + 1].asInt(), args[off + 2].asInt());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "slotTexture", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                builder.slotTexture(args[off].asString());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "manualSlots", args -> {
            builder.manualSlots();
            return table;
        });
    }

    static void bindBlockSlotLocks(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "lockSlot", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                int index = args[off].asInt();
                boolean locked = !ContentBindingSupport.hasMinArgs(args, off, 2) || args[off + 1].asBoolean();
                builder.lockSlot(index, locked);
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "lockedSlots", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1) && args[off].isTable()) {
                ILuaTable list = args[off].asTable();
                for (int i = 1; ; i++) {
                    ILuaValue entry = list.rawget(i);
                    if (entry == null || entry.isNil()) {
                        break;
                    }
                    builder.lockSlot(entry.asInt(), true);
                }
            }
            return table;
        });
    }
}


