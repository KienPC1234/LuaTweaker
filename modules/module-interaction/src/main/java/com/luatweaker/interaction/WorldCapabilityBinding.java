package com.luatweaker.interaction;

import com.luatweaker.api.pal.IPlatformCapability;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

/** Binds cross-mod capability primitives (energy, items, fluids) and block-to-block transfers. */
final class WorldCapabilityBinding {

    private WorldCapabilityBinding() {
    }

    static void bindWorkspaceCapabilities(ILuaEngine engine, ILuaTable workspaceTable) {
        final boolean[] capabilityWarned = { false };

        bindMapCapability(engine, workspaceTable, capabilityWarned,
                "GetEnergy", "World:GetEnergy",
                (capability, pos, side) -> capability.getEnergy(
                        pos.dimension(), pos.x(), pos.y(), pos.z(), side));
        bindAmountCapability(engine, workspaceTable, capabilityWarned,
                "InsertEnergy", "World:InsertEnergy",
                (capability, pos, amount, side) -> capability.insertEnergy(
                        pos.dimension(), pos.x(), pos.y(), pos.z(), amount, side));
        bindAmountCapability(engine, workspaceTable, capabilityWarned,
                "ExtractEnergy", "World:ExtractEnergy",
                (capability, pos, amount, side) -> capability.extractEnergy(
                        pos.dimension(), pos.x(), pos.y(), pos.z(), amount, side));
        bindMapCapability(engine, workspaceTable, capabilityWarned,
                "GetItemSlots", "World:GetItemSlots",
                (capability, pos, side) -> capability.getItemSlots(
                        pos.dimension(), pos.x(), pos.y(), pos.z(), side));
        bindStringIntCapability(engine, workspaceTable, capabilityWarned,
                "InsertItem", "World:InsertItem", " requires (itemId, count)",
                (capability, pos, itemId, count, side) -> capability.insertItem(
                        pos.dimension(), pos.x(), pos.y(), pos.z(), itemId, count, side));
        bindExtractItem(engine, workspaceTable, capabilityWarned);
        bindMapCapability(engine, workspaceTable, capabilityWarned,
                "GetFluid", "World:GetFluid",
                (capability, pos, side) -> capability.getFluid(
                        pos.dimension(), pos.x(), pos.y(), pos.z(), side));
        bindStringIntCapability(engine, workspaceTable, capabilityWarned,
                "FillFluid", "World:FillFluid", " requires (fluidId, amount)",
                (capability, pos, fluidId, amount, side) -> capability.fillFluid(
                        pos.dimension(), pos.x(), pos.y(), pos.z(), fluidId, amount, side));
        bindStringIntCapability(engine, workspaceTable, capabilityWarned,
                "DrainFluid", "World:DrainFluid", " requires (fluidId, amount); use \"*\" for any fluid",
                (capability, pos, fluidId, amount, side) -> capability.drainFluid(
                        pos.dimension(), pos.x(), pos.y(), pos.z(), fluidId, amount, side));
        WorldTransferBinding.bindTransferCapabilities(engine, workspaceTable);
    }

    private record CapRequest(InteractionBindingSupport.CoordinateArgs pos, IPlatformCapability capability) {
    }

    private static CapRequest prepareCapability(ILuaValue[] args, String call, boolean... warned) {
        InteractionBindingSupport.CoordinateArgs pos = InteractionParse.readCoordinates(args, call);
        if (pos == null) {
            return null;
        }
        IPlatformCapability capability = InteractionBindingSupport.capability(warned);
        if (capability == null) {
            return null;
        }
        return new CapRequest(pos, capability);
    }

    private static String lowerFirst(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    @FunctionalInterface
    private interface MapCapabilityCall {
        Object fetch(IPlatformCapability capability, InteractionBindingSupport.CoordinateArgs pos, String side);
    }

    private static void bindMapCapability(ILuaEngine engine, ILuaTable workspaceTable, boolean[] warned,
            String methodName, String callName, MapCapabilityCall fetch) {
        workspaceTable.rawset(methodName, args -> {
            CapRequest req = prepareCapability(args, callName, warned);
            if (req == null) {
                return engine.nilValue();
            }
            InteractionBindingSupport.SideParse side =
                    InteractionArgReaders.readSide(args, req.pos().cursor(), args.length);
            if (side.error()) {
                InteractionBindingSupport.warnSide(callName);
                return engine.nilValue();
            }
            Object data = fetch.fetch(req.capability(), req.pos(), side.name());
            return data != null ? InteractionLuaBinding.wrapValue(engine, data) : engine.nilValue();
        });
        workspaceTable.rawset(lowerFirst(methodName), workspaceTable.rawget(methodName));
    }

    @FunctionalInterface
    private interface AmountCapabilityCall {
        int fetch(IPlatformCapability capability, InteractionBindingSupport.CoordinateArgs pos, int amount, String side);
    }

    private static void bindAmountCapability(ILuaEngine engine, ILuaTable workspaceTable, boolean[] warned,
            String methodName, String callName, AmountCapabilityCall fetch) {
        workspaceTable.rawset(methodName, args -> {
            CapRequest req = prepareCapability(args, callName, warned);
            if (req == null) {
                return engine.wrapNumber(0);
            }
            Integer amount = InteractionArgReaders.readIntArg(args, req.pos().cursor(), args.length);
            if (amount == null) {
                InteractionBindingSupport.warn(callName + " requires an integer amount");
                return engine.wrapNumber(0);
            }
            InteractionBindingSupport.SideParse side =
                    InteractionArgReaders.readSide(args, req.pos().cursor() + 1, args.length);
            if (side.error()) {
                InteractionBindingSupport.warnSide(callName);
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber(fetch.fetch(req.capability(), req.pos(), amount, side.name()));
        });
        workspaceTable.rawset(lowerFirst(methodName), workspaceTable.rawget(methodName));
    }

    @FunctionalInterface
    private interface StringIntCapabilityCall {
        int fetch(IPlatformCapability capability, InteractionBindingSupport.CoordinateArgs pos,
                String first, int second, String side);
    }

    private static void bindStringIntCapability(ILuaEngine engine, ILuaTable workspaceTable, boolean[] warned,
            String methodName, String callName, String denySuffix, StringIntCapabilityCall fetch) {
        workspaceTable.rawset(methodName, args -> {
            CapRequest req = prepareCapability(args, callName, warned);
            if (req == null) {
                return engine.wrapNumber(0);
            }
            String first = InteractionArgReaders.readStringArg(args, req.pos().cursor(), args.length);
            Integer second = InteractionArgReaders.readIntArg(args, req.pos().cursor() + 1, args.length);
            if (first == null || second == null) {
                InteractionBindingSupport.warn(callName + denySuffix);
                return engine.wrapNumber(0);
            }
            InteractionBindingSupport.SideParse side =
                    InteractionArgReaders.readSide(args, req.pos().cursor() + 2, args.length);
            if (side.error()) {
                InteractionBindingSupport.warnSide(callName);
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber(fetch.fetch(req.capability(), req.pos(), first, second, side.name()));
        });
        workspaceTable.rawset(lowerFirst(methodName), workspaceTable.rawget(methodName));
    }

    private static void bindExtractItem(ILuaEngine engine, ILuaTable workspaceTable, boolean... warned) {
        // ExtractItem(x, y, z, slot, count [, side]) -> { Slot, Id, Count } | nil
        workspaceTable.rawset("ExtractItem", args -> {
            CapRequest req = prepareCapability(args, "World:ExtractItem", warned);
            if (req == null) {
                return engine.nilValue();
            }
            Integer slot = InteractionArgReaders.readIntArg(args, req.pos().cursor(), args.length);
            Integer count = InteractionArgReaders.readIntArg(args, req.pos().cursor() + 1, args.length);
            if (slot == null || count == null) {
                InteractionBindingSupport.warn("World:ExtractItem requires (slot, count)");
                return engine.nilValue();
            }
            InteractionBindingSupport.SideParse side =
                    InteractionArgReaders.readSide(args, req.pos().cursor() + 2, args.length);
            if (side.error()) {
                InteractionBindingSupport.warnSide("World:ExtractItem");
                return engine.nilValue();
            }
            java.util.Map<String, Object> data = req.capability().extractItem(
                    req.pos().dimension(), req.pos().x(), req.pos().y(), req.pos().z(),
                    slot, count, side.name());
            return data != null ? InteractionLuaBinding.wrapValue(engine, data) : engine.nilValue();
        });
        workspaceTable.rawset("extractItem", workspaceTable.rawget("ExtractItem"));
    }


}


