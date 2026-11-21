package com.luatweaker.content;

import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds item use/combat callback builder methods. */
final class ContentItemHandlersBinding {

    private ContentItemHandlersBinding() {
    }

    static void bindItemUseHandlers(ILuaEngine engine, ILuaTable table, IItemBuilder builder) {
        ContentBindingSupport.bindMethod(table, "onUseTick", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onUseTick((player, itemStack, ticksRemaining) -> ContentBindingSupport.invokeCallback(
                        engine, "item onUseTick", func,
                        ContentBindingSupport.wrapActor(engine, player),
                        ContentBindingSupport.wrapItemValue(engine, itemStack),
                        engine.wrapNumber(ticksRemaining)));
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onReleaseUsing", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onReleaseUsing((player, itemStack, timeLeft) -> ContentBindingSupport.invokeCallback(
                        engine, "item onReleaseUsing", func,
                        ContentBindingSupport.wrapActor(engine, player),
                        ContentBindingSupport.wrapItemValue(engine, itemStack),
                        engine.wrapNumber(timeLeft)));
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onFinishUsing", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onFinishUsing((player, itemStack) -> ContentBindingSupport.invokeCallback(
                        engine, "item onFinishUsing", func,
                        ContentBindingSupport.wrapActor(engine, player),
                        ContentBindingSupport.wrapItemValue(engine, itemStack)));
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onRightClick", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onRightClick((player, itemStack) -> ContentBindingSupport.invokeCallback(
                        engine, "item rightClick", func,
                        ContentBindingSupport.wrapActor(engine, player),
                        ContentBindingSupport.wrapItemValue(engine, itemStack)));
            }
            return table;
        });
    }

    static void bindItemCombatHandlers(ILuaEngine engine, ILuaTable table, IItemBuilder builder) {
        ContentBindingSupport.bindMethod(table, "onHitEntity", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onHitEntity((targetEntity, shooterPlayer) -> ContentBindingSupport.invokeCallback(
                        engine, "item onHitEntity", func,
                        ContentBindingSupport.wrapTargetEntity(engine, targetEntity),
                        ContentBindingSupport.wrapActor(engine, shooterPlayer)));
            }
            return table;
        });
        bindOnUseOnBlock(engine, table, builder);
        ContentBindingSupport.bindMethod(table, "onHurtEnemy", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onHurtEnemy((targetEntity, attackerPlayer, itemStack) -> ContentBindingSupport.invokeCallback(
                        engine, "item onHurtEnemy", func,
                        ContentBindingSupport.wrapTargetEntity(engine, targetEntity),
                        ContentBindingSupport.wrapActor(engine, attackerPlayer),
                        ContentBindingSupport.wrapItemValue(engine, itemStack)));
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onMineBlock", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onMineBlock((player, itemStack, x, y, z) -> ContentBindingSupport.invokeCallback(
                        engine, "item onMineBlock", func,
                        ContentBindingSupport.wrapActor(engine, player),
                        ContentBindingSupport.wrapItemValue(engine, itemStack),
                        engine.wrapNumber(x), engine.wrapNumber(y), engine.wrapNumber(z)));
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onSwing", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onSwing((player, itemStack) -> ContentBindingSupport.invokeCallback(
                        engine, "item onSwing", func,
                        ContentBindingSupport.wrapActor(engine, player),
                        ContentBindingSupport.wrapItemValue(engine, itemStack)));
            }
            return table;
        });
    }

    static void bindOnUseOnBlock(ILuaEngine engine, ILuaTable table, IItemBuilder builder) {
        ContentBindingSupport.bindMethod(table, "onUseOnBlock", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onUseOnBlock((player, hit) -> {
                    try {
                        ILuaValue playerVal = ContentBindingSupport.wrapActor(engine, player);
                        ILuaTable hitTable = buildHitTable(engine, hit);
                        if (hitTable == null) {
                            com.luatweaker.api.log.LuaTweakerLog.get().error(
                                    com.luatweaker.api.log.LogStage.SYSTEM,
                                    "Error executing item onUseOnBlock callback: malformed hit data");
                            return false;
                        }
                        ILuaValue consumed = engine.callFunction(func, playerVal, hitTable);
                        return consumed.isNil() || consumed.asBoolean();
                    } catch (IllegalStateException e) {
                        com.luatweaker.api.log.LuaTweakerLog.get().error(
                                com.luatweaker.api.log.LogStage.SYSTEM,
                                "Error executing item onUseOnBlock callback: " + e.getMessage());
                        return false;
                    }
                });
            }
            return table;
        });
    }

    private static ILuaTable buildHitTable(ILuaEngine engine, Object hit) {
        if (hit instanceof java.util.Map<?, ?> hitMap) {
            if (!hasHitNumbers(hitMap)) {
                return null;
            }
            ILuaTable hitTable = engine.createTable();
            hitTable.rawset("X", engine.wrapNumber(intOf(hitMap, "X")));
            hitTable.rawset("Y", engine.wrapNumber(intOf(hitMap, "Y")));
            hitTable.rawset("Z", engine.wrapNumber(intOf(hitMap, "Z")));
            Object face = hitMap.get("Face");
            hitTable.rawset("Face", engine.wrapString(face != null ? face.toString() : ""));
            return hitTable;
        }
        return engine.createTable();
    }

    private static boolean hasHitNumbers(java.util.Map<?, ?> hitMap) {
        return hitMap.get("X") instanceof Number
                && hitMap.get("Y") instanceof Number
                && hitMap.get("Z") instanceof Number;
    }

    private static int intOf(java.util.Map<?, ?> hitMap, String key) {
        return ((Number) hitMap.get(key)).intValue();
    }

}

