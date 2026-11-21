package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds block energy-storage and machine-bar builder methods. */
final class ContentBlockMachineBinding {

    private ContentBlockMachineBinding() {
    }

    static void bindBlockEnergy(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "energyStorage", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 3)) {
                builder.energyStorage(args[off].asInt(), args[off + 1].asInt(), args[off + 2].asInt());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "fluidStorage", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                builder.fluidStorage(args[off].asInt());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "guiBar", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 7)) {
                builder.guiBar(args[off].asString(), args[off + 1].asInt(), args[off + 2].asInt(),
                        args[off + 3].asInt(), args[off + 4].asInt(), args[off + 5].asString(),
                        args[off + 6].asInt());
            }
            return table;
        });
        bindCustomBar(table, builder);
    }

    private record BarOptions(String currentKey, String maxKey, int color, String tooltip) {
    }

    private static void bindCustomBar(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "customBar", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 5)) {
                BarOptions options = readBarOptions(args, off);
                if (options != null && options.currentKey() != null && options.maxKey() != null) {
                    builder.customBar(args[off].asString(), args[off + 1].asInt(), args[off + 2].asInt(),
                            args[off + 3].asInt(), args[off + 4].asInt(),
                            options.currentKey(), options.maxKey(), options.color(), options.tooltip());
                }
            }
            return table;
        });
    }

    private static BarOptions readBarOptions(ILuaValue[] args, int off) {
        if (ContentBindingSupport.hasMinArgs(args, off, 6) && args[off + 5].isTable()) {
            return readBarOptionsTable(args[off + 5].asTable());
        }
        if (ContentBindingSupport.hasMinArgs(args, off, 7)) {
            String currentKey = args[off + 5].asString();
            String maxKey = args[off + 6].asString();
            int color = ContentBindingSupport.hasMinArgs(args, off, 8) ? args[off + 7].asInt() : 0;
            String tooltip = ContentBindingSupport.hasMinArgs(args, off, 9) ? args[off + 8].asString() : null;
            return new BarOptions(currentKey, maxKey, color, tooltip);
        }
        return null;
    }

    private static BarOptions readBarOptionsTable(ILuaTable opts) {
        ILuaValue currentVal = opts.rawget("currentKey");
        if (currentVal.isNil()) {
            currentVal = opts.rawget("current");
        }
        ILuaValue maxVal = opts.rawget("maxKey");
        if (maxVal.isNil()) {
            maxVal = opts.rawget("max");
        }
        int color = 0;
        ILuaValue colorVal = opts.rawget("color");
        if (!colorVal.isNil()) {
            color = colorVal.asInt();
        }
        ILuaValue tooltipVal = opts.rawget("tooltip");
        if (tooltipVal.isNil()) {
            tooltipVal = opts.rawget("tooltipFormat");
        }
        return new BarOptions(
                currentVal.isNil() ? null : currentVal.asString(),
                maxVal.isNil() ? null : maxVal.asString(),
                color,
                tooltipVal.isNil() ? null : tooltipVal.asString());
    }
}


