package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;

/** Binds cuboid validation and box-wide block-state updates. */
final class WorldValidateBinding {

    private WorldValidateBinding() {
    }

    static void bindWorkspaceValidation(ILuaEngine engine, ILuaTable workspaceTable) {
        // ValidateCuboid([dim, ] posA, posB, specTable) or (x1, y1, z1, x2, y2, z2, specTable)
        workspaceTable.rawset("ValidateCuboid", args -> {
            int off = LuaBinder.getOffset(args);
            InteractionVectors.CuboidRegion region = InteractionVectors.readCuboidRegion(args, off);
            if (region == null) {
                return engine.createTable();
            }
            java.util.Map<String, Object> spec = java.util.Map.of();
            if (region.cursor() < args.length && args[region.cursor()].isTable()) {
                spec = InteractionLuaBinding.toJavaMap(args[region.cursor()].asTable());
            }
            java.util.Map<String, Object> result = com.luatweaker.api.pal.Platform.getInteraction()
                    .validateCuboid(region.dimension(), region.x1(), region.y1(), region.z1(),
                            region.x2(), region.y2(), region.z2(), spec);
            return InteractionLuaBinding.wrapValue(engine, result);
        });
        workspaceTable.rawset("validateCuboid", workspaceTable.rawget("ValidateCuboid"));

        // SetBlockStatesInBox([dim, ] posA, posB, [filterBlockId, ] propertiesTable)
        workspaceTable.rawset("SetBlockStatesInBox", args -> {
            int off = LuaBinder.getOffset(args);
            InteractionVectors.CuboidRegion region = InteractionVectors.readCuboidRegion(args, off);
            if (region == null) {
                return engine.wrapNumber(0);
            }
            int cursor = region.cursor();
            String filter = null;
            if (cursor < args.length && args[cursor].toJavaObject() instanceof String
                    && !args[cursor].isTable()) {
                filter = args[cursor].asString();
                cursor++;
            }
            java.util.Map<String, Object> props = java.util.Map.of();
            if (cursor < args.length && args[cursor].isTable()) {
                props = InteractionLuaBinding.toJavaMap(args[cursor].asTable());
            }
            int updated = com.luatweaker.api.pal.Platform.getInteraction().setBlockStatesInBox(
                    region.dimension(), region.x1(), region.y1(), region.z1(),
                    region.x2(), region.y2(), region.z2(), filter, props);
            return engine.wrapNumber(updated);
        });
        workspaceTable.rawset("setBlockStatesInBox", workspaceTable.rawget("SetBlockStatesInBox"));
    }
}
