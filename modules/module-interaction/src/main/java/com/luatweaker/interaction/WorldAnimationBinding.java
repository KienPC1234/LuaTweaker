package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds geo animation triggers for entities, items and blocks. */
final class WorldAnimationBinding {

    private WorldAnimationBinding() {
    }

    static void bindWorkspaceAnimation(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset(InteractionBindingSupport.METHOD_PLAY_ANIMATION, args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                return engine.wrapBoolean(false);
            }
            ILuaValue targetVal = args[off];
            String animName = args[off + 1].asString();
            String ctrl = InteractionBindingSupport.hasMinArgs(args, off, 3)
                    ? args[off + 2].asString() : InteractionBindingSupport.CTRL_DEFAULT;
            Object target = resolveAnimationTarget(targetVal);
            if (target == null) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .triggerGeoAnimation(target, ctrl, animName, 1.0, false));
        });
        workspaceTable.rawset("playAnimation", workspaceTable.rawget(InteractionBindingSupport.METHOD_PLAY_ANIMATION));
        workspaceTable.rawset("TriggerAnimation",
                workspaceTable.rawget(InteractionBindingSupport.METHOD_PLAY_ANIMATION));
        workspaceTable.rawset("triggerAnimation",
                workspaceTable.rawget(InteractionBindingSupport.METHOD_PLAY_ANIMATION));

        workspaceTable.rawset(InteractionBindingSupport.METHOD_STOP_ANIMATION, args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                return engine.wrapBoolean(false);
            }
            ILuaValue targetVal = args[off];
            String animName = args[off + 1].asString();
            String ctrl = InteractionBindingSupport.hasMinArgs(args, off, 3)
                    ? args[off + 2].asString() : InteractionBindingSupport.CTRL_DEFAULT;
            Object target = resolveAnimationTarget(targetVal);
            if (target == null) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .stopGeoAnimation(target, ctrl, animName));
        });
        workspaceTable.rawset("stopAnimation", workspaceTable.rawget(InteractionBindingSupport.METHOD_STOP_ANIMATION));
    }

    private static Object resolveAnimationTarget(ILuaValue targetVal) {
        if (targetVal.isTable()) {
            return resolveTableAnimationTarget(targetVal.asTable());
        }
        return targetVal.toJavaObject();
    }

    private static Object resolveTableAnimationTarget(ILuaTable tbl) {
        ILuaValue ent = tbl.rawget(InteractionBindingSupport.ENTITY_MARKER);
        if (ent != null && !ent.isNil()) {
            return ent.toJavaObject();
        }
        ILuaValue itm = tbl.rawget(InteractionBindingSupport.ITEM_MARKER);
        if (itm != null && !itm.isNil()) {
            return itm.toJavaObject();
        }
        ILuaValue blk = tbl.rawget(InteractionBindingSupport.BLOCK_MARKER);
        if (blk != null && !blk.isNil()) {
            return blk.toJavaObject();
        }
        return resolvePositionAnimationTarget(tbl);
    }

    private static Object resolvePositionAnimationTarget(ILuaTable tbl) {
        int[] xyz = InteractionParse.tablePosition(tbl);
        if (xyz.length == 0) {
            return null;
        }
        ILuaValue dimVal = tbl.rawget(InteractionBindingSupport.KEY_DIMENSION_UPPER);
        if (dimVal == null || dimVal.isNil()) {
            dimVal = tbl.rawget(InteractionBindingSupport.KEY_DIMENSION);
        }
        String dim = dimVal != null && !dimVal.isNil() ? dimVal.asString()
                : InteractionBindingSupport.DEFAULT_DIMENSION;
        return com.luatweaker.api.pal.Platform.getInteraction()
                .getInteractableBlock(dim, xyz[0], xyz[1], xyz[2]);
    }
}

