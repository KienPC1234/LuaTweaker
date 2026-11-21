package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds block-state and block-entity data operations. */
final class WorldStateBinding {

    private WorldStateBinding() {
    }

    static void bindWorkspaceState(ILuaEngine engine, ILuaTable workspaceTable) {
        bindGetBlockState(engine, workspaceTable);
        bindSetBlockState(engine, workspaceTable);
        bindGetBlockEntityData(engine, workspaceTable);
        bindSetBlockEntityData(engine, workspaceTable);
        bindEjectContainerItem(engine, workspaceTable);
    }

    record DimensionCoords(String dimension, int off) {
    }

    /**
     * Reads an optional leading dimension string; the cursor points at the first
     * coordinate. Mirrors the repeated dimOff blocks of the state operations.
     */
    static DimensionCoords readDimensionCoords(ILuaValue... args) {
        int off = LuaBinder.getOffset(args);
        int dimOff = -1;
        if (InteractionBindingSupport.hasMinArgs(args, off, 1)
                && args[off].toJavaObject() instanceof String) {
            dimOff = off;
            off = off + 1;
        }
        String dimension =
                dimOff >= 0 ? args[dimOff].asString() : InteractionBindingSupport.DEFAULT_DIMENSION;
        return new DimensionCoords(dimension, off);
    }

    // ===== BlockState API (GetBlockState(x,y,z) or GetBlockState(dim,x,y,z)) =====
    private static void bindGetBlockState(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("GetBlockState", args -> {
            DimensionCoords coords = readDimensionCoords(args);
            if (!InteractionBindingSupport.hasMinArgs(args, coords.off(), 3)) {
                return engine.nilValue();
            }
            String dimension = coords.dimension();
            int off = coords.off();
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            java.util.Map<String, Object> state = com.luatweaker.api.pal.Platform.getInteraction()
                    .getBlockState(dimension, x, y, z);
            return state != null ? InteractionLuaBinding.wrapValue(engine, state) : engine.nilValue();
        });
        workspaceTable.rawset("getBlockState", workspaceTable.rawget("GetBlockState"));
    }

    private static void bindSetBlockState(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("SetBlockState", args -> {
            DimensionCoords coords = readDimensionCoords(args);
            if (!InteractionBindingSupport.hasMinArgs(args, coords.off(), 4)) {
                return engine.nilValue();
            }
            String dimension = coords.dimension();
            int off = coords.off();
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            String blockId = args[off + 3].asString();
            java.util.Map<String, Object> properties = null;
            if (InteractionBindingSupport.hasMinArgs(args, off, 5) && args[off + 4].isTable()) {
                properties = InteractionLuaBinding.toJavaMap(args[off + 4].asTable());
            }
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setBlockState(dimension, x, y, z, blockId, properties));
        });
        workspaceTable.rawset("setBlockState", workspaceTable.rawget("SetBlockState"));
    }

    // ===== BlockEntity NBT API (GetBlockEntityData / SetBlockEntityData) =====
    private static void bindGetBlockEntityData(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("GetBlockEntityData", args -> {
            DimensionCoords coords = readDimensionCoords(args);
            if (!InteractionBindingSupport.hasMinArgs(args, coords.off(), 3)) {
                return engine.nilValue();
            }
            String dimension = coords.dimension();
            int off = coords.off();
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            java.util.Map<String, Object> data = com.luatweaker.api.pal.Platform.getInteraction()
                    .getBlockEntityData(dimension, x, y, z);
            return data != null ? InteractionLuaBinding.wrapValue(engine, data) : engine.nilValue();
        });
        workspaceTable.rawset("getBlockEntityData", workspaceTable.rawget("GetBlockEntityData"));
    }

    private static void bindSetBlockEntityData(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("SetBlockEntityData", args -> {
            DimensionCoords coords = readDimensionCoords(args);
            if (!InteractionBindingSupport.hasMinArgs(args, coords.off(), 4)
                    || !args[coords.off() + 3].isTable()) {
                return engine.wrapBoolean(false);
            }
            String dimension = coords.dimension();
            int off = coords.off();
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            java.util.Map<String, Object> data = InteractionLuaBinding.toJavaMap(args[off + 3].asTable());
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setBlockEntityData(dimension, x, y, z, data));
        });
        workspaceTable.rawset("setBlockEntityData", workspaceTable.rawget("SetBlockEntityData"));
    }

    // ===== Container ejection (EjectContainerItem(x,y,z,slot,[count])) =====
    private static void bindEjectContainerItem(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("EjectContainerItem", args -> {
            DimensionCoords coords = readDimensionCoords(args);
            if (!InteractionBindingSupport.hasMinArgs(args, coords.off(), 4)) {
                return engine.wrapBoolean(false);
            }
            String dimension = coords.dimension();
            int off = coords.off();
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            int slot = args[off + 3].asInt();
            int count = InteractionBindingSupport.hasMinArgs(args, off, 5) ? args[off + 4].asInt() : 1;
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .ejectContainerItem(dimension, x, y, z, slot, count));
        });
        workspaceTable.rawset("ejectContainerItem", workspaceTable.rawget("EjectContainerItem"));
    }
}
