package com.luatweaker.content;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

/** Shared glue for the content binding collaborators: arg checks, method aliases, callbacks. */
final class ContentBindingSupport {

    static final int DEFAULT_COUNT = 1;
    static final int DEFAULT_CONTAINER_COLUMNS = 6;
    static final String DEFAULT_DROP_MODE = "packed";
    static final String DEFAULT_BOSS_BAR_COLOR = "RED";
    static final String DEFAULT_BOSS_BAR_OVERLAY = "PROGRESS";

    static final String METHOD_DISPLAY_NAME = "displayName";
    static final String METHOD_CREATIVE_TAB = "creativeTab";
    static final String TAG_TYPE_ITEM = "item";
    static final String TAG_TYPE_BLOCK = "block";
    static final String TAG_TYPE_FLUID = "fluid";
    static final String TAG_TYPE_ENTITY = "entity_type";

    private ContentBindingSupport() {
    }

    static boolean hasMinArgs(ILuaValue[] args, int off, int min) {
        return args.length - off >= min;
    }

    /**
     * Throws a loud usage error when a builder call is missing required arguments,
     * instead of crashing with an index error.
     */
    static void requireArgs(ILuaValue[] args, int off, int min, String usage) {
        if (!hasMinArgs(args, off, min)) {
            throw new IllegalArgumentException(usage + " requires " + min + " argument(s)");
        }
    }

    static void bindMethod(ILuaTable table, String name, ILuaFunction func) {
        table.rawset(name, func);
        if (name != null && !name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
            String pascal = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            table.rawset(pascal, func);
        }
    }

    static void bindFirstString(ILuaValue[] args, java.util.function.Consumer<String> setter) {
        int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
        if (hasMinArgs(args, off, 1)) {
            setter.accept(args[off].asString());
        }
    }

    static ILuaValue wrapItemValue(ILuaEngine engine, Object itemStack) {
        if (itemStack instanceof com.luatweaker.api.interaction.IInteractableItem item) {
            return com.luatweaker.interaction.InteractionLuaBinding.wrapItem(engine, item);
        }
        return engine.toLuaValue(itemStack);
    }

    static ILuaValue wrapActor(ILuaEngine engine, Object actor) {
        if (actor instanceof com.luatweaker.api.entity.IPlayer player) {
            return com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, player);
        }
        return engine.toLuaValue(actor);
    }

    static ILuaValue wrapTargetEntity(ILuaEngine engine, Object target) {
        if (target instanceof com.luatweaker.api.entity.IEntity entity) {
            return com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, entity);
        }
        return engine.toLuaValue(target);
    }

    /**
     * Runs a gameplay callback (item/block/entity handlers). Lua errors are recorded
     * by the engine itself; only the shutdown gate ({@link IllegalStateException})
     * escapes and is logged here.
     */
    static void invokeCallback(ILuaEngine engine, String action, ILuaValue func, ILuaValue... callbackArgs) {
        try {
            engine.callFunction(func, callbackArgs);
        } catch (IllegalStateException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM, "Error executing " + action + " callback: " + e.getMessage());
        }
    }

    /**
     * Runs a startup builder callback when it is a function. Lua errors are recorded
     * by the engine itself; only the shutdown gate ({@link IllegalStateException})
     * escapes and is logged here with the content id for context.
     */
    static void invokeBuilderCallback(ILuaEngine engine, String kind, String id,
            ILuaValue callbackVal, ILuaTable table) {
        try {
            engine.callFunction(callbackVal, table);
        } catch (IllegalStateException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Error in " + kind + " builder callback for '" + id + "': " + e.getMessage());
        }
    }

    static void addTagFromLua(IDatapackService service, String type, String tagId, ILuaValue val) {
        if (service == null) {
            return;
        }
        java.util.List<String> list = new java.util.ArrayList<>();
        if (val instanceof ILuaTable tbl) {
            for (int i = 1; ; i++) {
                ILuaValue entry = tbl.rawget(i);
                if (entry == null || entry.isNil()) {
                    break;
                }
                list.add(entry.asString());
            }
        } else if (val != null && !val.isNil()) {
            list.add(val.asString());
        }
        if (!list.isEmpty()) {
            service.addTag(type, tagId, list);
        }
    }
}
