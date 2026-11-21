package com.luatweaker.interaction;

import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds block energy/item/fluid capability methods. */
final class BlockCapabilityBinding {

    private BlockCapabilityBinding() {
    }

    private static String readSideName(ILuaValue[] args, int off) {
        if (InteractionBindingSupport.hasMinArgs(args, off, 1) && args[off].isString()) {
            return args[off].asString();
        }
        return null;
    }

    static void bindBlockEnergyCaps(ILuaTable methods, ILuaEngine engine, IInteractableBlock block) {
        methods.rawset("GetEnergy", args -> {
            int off = LuaBinder.getOffset(args);
            String side = readSideName(args, off);
            var data = block.getEnergy(side);
            return data != null ? InteractionLuaBinding.wrapValue(engine, data) : engine.nilValue();
        });
        methods.rawset("getEnergy", methods.rawget("GetEnergy"));

        methods.rawset("InsertEnergy", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1) || !args[off].isNumber()) {
                return engine.wrapNumber(0);
            }
            int amount = args[off].asInt();
            String side = readSideName(args, off + 1);
            return engine.wrapNumber(block.insertEnergy(amount, side));
        });
        methods.rawset("insertEnergy", methods.rawget("InsertEnergy"));

        methods.rawset("ExtractEnergy", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1) || !args[off].isNumber()) {
                return engine.wrapNumber(0);
            }
            int amount = args[off].asInt();
            String side = readSideName(args, off + 1);
            return engine.wrapNumber(block.extractEnergy(amount, side));
        });
        methods.rawset("extractEnergy", methods.rawget("ExtractEnergy"));
    }

    static void bindBlockItemCaps(ILuaTable methods, ILuaEngine engine, IInteractableBlock block) {
        methods.rawset("GetItemSlots", args -> {
            int off = LuaBinder.getOffset(args);
            String side = readSideName(args, off);
            var slots = block.getItemSlots(side);
            return slots != null ? InteractionLuaBinding.wrapValue(engine, slots) : engine.nilValue();
        });
        methods.rawset("getItemSlots", methods.rawget("GetItemSlots"));

        methods.rawset("InsertItem", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                return engine.wrapNumber(0);
            }
            String itemId = args[off].asString();
            int count = args[off + 1].asInt();
            
            String side = readSideName(args, off + 2);
            return engine.wrapNumber(block.insertItem(itemId, count, side));
        });
        methods.rawset("insertItem", methods.rawget("InsertItem"));

        methods.rawset("ExtractItem", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                return engine.nilValue();
            }
            int slot = args[off].asInt();
            int count = args[off + 1].asInt();
            
            String side = readSideName(args, off + 2);
            var data = block.extractItem(slot, count, side);
            return data != null ? InteractionLuaBinding.wrapValue(engine, data) : engine.nilValue();
        });
        methods.rawset("extractItem", methods.rawget("ExtractItem"));
    }

    static void bindBlockFluidCaps(ILuaTable methods, ILuaEngine engine, IInteractableBlock block) {
        methods.rawset("GetFluid", args -> {
            int off = LuaBinder.getOffset(args);
            String side = readSideName(args, off);
            var data = block.getFluid(side);
            return data != null ? InteractionLuaBinding.wrapValue(engine, data) : engine.nilValue();
        });
        methods.rawset("getFluid", methods.rawget("GetFluid"));

        methods.rawset("FillFluid", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                return engine.wrapNumber(0);
            }
            String fluidId = args[off].asString();
            int amount = args[off + 1].asInt();
            String side = readSideName(args, off + 2);
            return engine.wrapNumber(block.fillFluid(fluidId, amount, side));
        });
        methods.rawset("fillFluid", methods.rawget("FillFluid"));

        methods.rawset("DrainFluid", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                return engine.wrapNumber(0);
            }
            String fluidId = InteractionBindingSupport.hasMinArgs(args, off, 2) && args[off].isString()
                    ? args[off].asString() : "*";
            int amount = InteractionBindingSupport.hasMinArgs(args, off, 2)
                    ? args[off + 1].asInt() : args[off].asInt();
            String side = readSideName(args, off + 2);
            return engine.wrapNumber(block.drainFluid(fluidId, amount, side));
        });
        methods.rawset("drainFluid", methods.rawget("DrainFluid"));
    }

}
