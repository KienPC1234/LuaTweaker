package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds block-to-block energy/fluid/item transfers. */
final class WorldTransferBinding {

    private WorldTransferBinding() {
    }

    static void bindTransferCapabilities(ILuaEngine engine, ILuaTable workspaceTable) {
        // TransferEnergy(fromPos, toPos, maxAmount [, simulate]) -> transferred
        workspaceTable.rawset("TransferEnergy", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 3)) {
                return engine.wrapNumber(0);
            }
            InteractionVectors.TransferEndpoints endpoints =
                    InteractionVectors.readTransferEndpoints(args, off, 7);
            if (endpoints == null) {
                return engine.wrapNumber(0);
            }
            int cursor = endpoints.cursor();
            int amount = 0;
            Integer amountOpt = InteractionArgReaders.readIntArg(args, cursor, args.length);
            if (amountOpt != null) {
                amount = amountOpt;
                cursor++;
            }
            boolean sim = cursor < args.length && args[cursor].asBoolean();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction().transferEnergy(
                    InteractionBindingSupport.DEFAULT_DIMENSION,
                    endpoints.fromX(), endpoints.fromY(), endpoints.fromZ(),
                    endpoints.toX(), endpoints.toY(), endpoints.toZ(), amount, sim));
        });
        workspaceTable.rawset("transferEnergy", workspaceTable.rawget("TransferEnergy"));

        // TransferFluid(fromPos, toPos, maxAmount [, simulate]) -> transferred
        workspaceTable.rawset("TransferFluid", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 3)) {
                return engine.wrapNumber(0);
            }
            InteractionVectors.TransferEndpoints endpoints =
                    InteractionVectors.readTransferEndpoints(args, off, 7);
            if (endpoints == null) {
                return engine.wrapNumber(0);
            }
            int cursor = endpoints.cursor();
            int amount = 0;
            Integer fluidAmount = InteractionArgReaders.readIntArg(args, cursor, args.length);
            if (fluidAmount != null) {
                amount = fluidAmount;
                cursor++;
            }
            boolean sim = cursor < args.length && args[cursor].asBoolean();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction().transferFluid(
                    InteractionBindingSupport.DEFAULT_DIMENSION,
                    endpoints.fromX(), endpoints.fromY(), endpoints.fromZ(),
                    endpoints.toX(), endpoints.toY(), endpoints.toZ(), amount, sim));
        });
        workspaceTable.rawset("transferFluid", workspaceTable.rawget("TransferFluid"));

        bindTransferItem(engine, workspaceTable);
    }

    private record ItemTransfer(int fromX, int fromY, int fromZ, int fromSlot,
            int toX, int toY, int toZ, int toSlot, int cursor) {
    }

    private static void bindTransferItem(ILuaEngine engine, ILuaTable workspaceTable) {
        // TransferItem(fromPos, fromSlot, toPos, toSlot, maxCount [, simulate]) -> transferred
        workspaceTable.rawset("TransferItem", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 5)) {
                return engine.wrapNumber(0);
            }
            ItemTransfer transfer;
            if (args[off].isTable()) {
                transfer = readItemTransferTable(args, off);
            } else {
                transfer = readItemTransferNumbers(args, off);
            }
            if (transfer == null) {
                return engine.wrapNumber(0);
            }
            int cursor = transfer.cursor();
            int maxCount = 64;
            Integer countOpt = InteractionArgReaders.readIntArg(args, cursor, args.length);
            if (countOpt != null) {
                maxCount = countOpt;
                cursor++;
            }
            boolean sim = cursor < args.length && args[cursor].asBoolean();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction().transferItem(
                    InteractionBindingSupport.DEFAULT_DIMENSION,
                    transfer.fromX(), transfer.fromY(), transfer.fromZ(), transfer.fromSlot(),
                    transfer.toX(), transfer.toY(), transfer.toZ(), transfer.toSlot(), maxCount, sim));
        });
        workspaceTable.rawset("transferItem", workspaceTable.rawget("TransferItem"));
    }

    private static ItemTransfer readItemTransferTable(ILuaValue[] args, int off) {
        int cursor = off;
        int[] from = InteractionParse.tablePosition(args[cursor].asTable());
        if (from.length == 0) {
            return null;
        }
        cursor++;
        int fromSlot = args[cursor++].asInt();
        if (cursor >= args.length || !args[cursor].isTable()) {
            return null;
        }
        int[] to = InteractionParse.tablePosition(args[cursor].asTable());
        if (to.length == 0) {
            return null;
        }
        cursor++;
        int toSlot = -1;
        Integer toSlotOpt = InteractionArgReaders.readIntArg(args, cursor, args.length);
        if (toSlotOpt != null) {
            toSlot = toSlotOpt;
            cursor++;
        }
        return new ItemTransfer(from[0], from[1], from[2], fromSlot,
                to[0], to[1], to[2], toSlot, cursor);
    }

    private static ItemTransfer readItemTransferNumbers(ILuaValue[] args, int off) {
        if (!InteractionBindingSupport.hasMinArgs(args, off, 9)) {
            return null;
        }
        int cursor = off;
        int fromX = args[cursor++].asInt();
        int fromY = args[cursor++].asInt();
        int fromZ = args[cursor++].asInt();
        int fromSlot = args[cursor++].asInt();
        int toX = args[cursor++].asInt();
        int toY = args[cursor++].asInt();
        int toZ = args[cursor++].asInt();
        int toSlot = args[cursor++].asInt();
        return new ItemTransfer(fromX, fromY, fromZ, fromSlot, toX, toY, toZ, toSlot, cursor);
    }
}



