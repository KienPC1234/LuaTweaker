package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

/** Binds redstone, energy and progress machine-state operations. */
final class WorldUtilityBinding {

    private WorldUtilityBinding() {
    }

    static void bindWorkspaceUtility(ILuaEngine engine, ILuaTable workspaceTable) {
        bindSetEnergy(engine, workspaceTable);
        bindProduceEnergy(engine, workspaceTable);
        bindSetProgress(engine, workspaceTable);
        bindGetProgress(engine, workspaceTable);
        bindGetRedstoneSignal(engine, workspaceTable);
    }

    private static Integer readTrailingInt(ILuaValue[] args, InteractionBindingSupport.CoordinateArgs pos) {
        if (pos.cursor() >= args.length) {
            return null;
        }
        return args[pos.cursor()].asInt();
    }

    // SetEnergy([dim, ] x, y, z, amount) or (pos, amount) -> boolean
    private static void bindSetEnergy(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("SetEnergy", args -> {
            InteractionBindingSupport.CoordinateArgs pos =
                    InteractionParse.readCoordinates(args, "World:SetEnergy");
            Integer amount = pos == null ? null : readTrailingInt(args, pos);
            if (pos == null || amount == null) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setEnergy(pos.dimension(), pos.x(), pos.y(), pos.z(), amount));
        });
        workspaceTable.rawset("setEnergy", workspaceTable.rawget("SetEnergy"));
    }

    // ProduceEnergy([dim, ] x, y, z, amount) or (pos, amount) -> actualProduced
    private static void bindProduceEnergy(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("ProduceEnergy", args -> {
            InteractionBindingSupport.CoordinateArgs pos =
                    InteractionParse.readCoordinates(args, "World:ProduceEnergy");
            Integer amount = pos == null ? null : readTrailingInt(args, pos);
            if (pos == null || amount == null) {
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .produceEnergy(pos.dimension(), pos.x(), pos.y(), pos.z(), amount));
        });
        workspaceTable.rawset("produceEnergy", workspaceTable.rawget("ProduceEnergy"));
    }

    // SetProgress([dim, ] x, y, z, progress) or (pos, progress) -> boolean
    private static void bindSetProgress(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("SetProgress", args -> {
            InteractionBindingSupport.CoordinateArgs pos =
                    InteractionParse.readCoordinates(args, "World:SetProgress");
            if (pos == null || pos.cursor() >= args.length) {
                return engine.wrapBoolean(false);
            }
            float progress = (float) args[pos.cursor()].asDouble();
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setProgress(pos.dimension(), pos.x(), pos.y(), pos.z(), progress));
        });
        workspaceTable.rawset("setProgress", workspaceTable.rawget("SetProgress"));
    }

    // GetProgress([dim, ] x, y, z) or (pos) -> progress
    private static void bindGetProgress(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("GetProgress", args -> {
            InteractionBindingSupport.CoordinateArgs pos =
                    InteractionParse.readCoordinates(args, "World:GetProgress");
            if (pos == null) {
                return engine.wrapNumber(0.0);
            }
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .getProgress(pos.dimension(), pos.x(), pos.y(), pos.z()));
        });
        workspaceTable.rawset("getProgress", workspaceTable.rawget("GetProgress"));
    }

    // GetRedstoneSignal(x, y, z [, direction]) -> 0..15
    private static void bindGetRedstoneSignal(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("GetRedstoneSignal", args -> {
            InteractionBindingSupport.CoordinateArgs pos =
                    InteractionParse.readCoordinates(args, "World:GetRedstoneSignal");
            if (pos == null) {
                return engine.wrapNumber(0);
            }
            String dir = pos.cursor() < args.length && !args[pos.cursor()].isNil()
                    ? args[pos.cursor()].asString() : null;
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction().getRedstoneSignal(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), dir));
        });
        workspaceTable.rawset("getRedstoneSignal", workspaceTable.rawget("GetRedstoneSignal"));
    }
}

