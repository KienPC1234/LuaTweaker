package com.luatweaker.interaction;

import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.interaction.IInteractionService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds the GetBlock workspace query. */
final class WorldBlockQueryBinding {

    private WorldBlockQueryBinding() {
    }

    static void bindGetBlock(ILuaEngine engine, ILuaTable workspaceTable, IInteractionService service) {
        workspaceTable.rawset("GetBlock", args -> {
            int off = LuaBinder.getOffset(args);
            int count = args.length - off;
            if (count <= 0) {
                return engine.nilValue();
            }
            BlockTarget target = readBlockTarget(args, off, count);
            if (target == null) {
                return engine.nilValue();
            }
            IInteractableBlock block = service.GetBlock(target.dimension(), target.x(), target.y(), target.z());
            return block != null ? InteractionLuaBinding.wrapBlock(engine, block) : engine.nilValue();
        });
        workspaceTable.rawset("getBlock", workspaceTable.rawget("GetBlock"));
    }

    private record BlockTarget(String dimension, int x, int y, int z) {
    }

    private static BlockTarget readBlockTarget(ILuaValue[] args, int off, int count) {
        if (args[off].isTable()) {
            ILuaTable pos = args[off].asTable();
            int[] xyz = InteractionParse.tablePosition(pos);
            if (xyz.length == 0) {
                return null;
            }
            return new BlockTarget(readTableDimension(pos), xyz[0], xyz[1], xyz[2]);
        }
        if (count >= 4 && args[off].isString()) {
            return new BlockTarget(args[off].asString(),
                    args[off + 1].asInt(), args[off + 2].asInt(), args[off + 3].asInt());
        }
        if (count >= 3 && args[off].isNumber()) {
            return new BlockTarget(InteractionBindingSupport.DEFAULT_DIMENSION,
                    args[off].asInt(), args[off + 1].asInt(), args[off + 2].asInt());
        }
        return null;
    }

    static String readTableDimension(ILuaTable pos) {
        ILuaValue dimVal = pos.rawget(InteractionBindingSupport.KEY_DIMENSION);
        if (dimVal == null || dimVal.isNil()) {
            dimVal = pos.rawget(InteractionBindingSupport.KEY_DIMENSION_UPPER);
        }
        if (dimVal != null && !dimVal.isNil()) {
            return dimVal.asString();
        }
        return InteractionBindingSupport.DEFAULT_DIMENSION;
    }

}
