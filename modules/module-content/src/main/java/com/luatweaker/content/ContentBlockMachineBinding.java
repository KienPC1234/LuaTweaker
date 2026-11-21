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
                String anchor = ContentBindingSupport.hasMinArgs(args, off, 8)
                        ? args[off + 7].asString() : "topleft";
                builder.guiBar(args[off].asString(), args[off + 1].asInt(), args[off + 2].asInt(),
                        args[off + 3].asInt(), args[off + 4].asInt(), args[off + 5].asString(),
                        args[off + 6].asInt(), anchor);
            }
            return table;
        });
        bindCustomBar(table, builder);
    }

    private record BarOptions(String currentKey, String maxKey, int color, String tooltip, String anchor) {
    }

    private static void bindCustomBar(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "customBar", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 5)) {
                BarOptions options = readBarOptions(args, off);
                if (options != null && options.currentKey() != null && options.maxKey() != null) {
                    builder.customBar(args[off].asString(), args[off + 1].asInt(), args[off + 2].asInt(),
                            args[off + 3].asInt(), args[off + 4].asInt(),
                            options.currentKey(), options.maxKey(), options.color(), options.tooltip(),
                            options.anchor());
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
            String anchor = ContentBindingSupport.hasMinArgs(args, off, 10) ? args[off + 9].asString() : "topleft";
            return new BarOptions(currentKey, maxKey, color, tooltip, anchor);
        }
        return null;
    }

    private static BarOptions readBarOptionsTable(ILuaTable opts) {
        return new BarOptions(
                textOf(opts, "currentKey", "current", null),
                textOf(opts, "maxKey", "max", null),
                intOf(opts, "color"),
                textOf(opts, "tooltip", "tooltipFormat", null),
                textOf(opts, "anchor", null, "topleft"));
    }

    private static String textOf(ILuaTable opts, String primary, String fallback, String missing) {
        ILuaValue value = opts.rawget(primary);
        if (value.isNil() && fallback != null) {
            value = opts.rawget(fallback);
        }
        return value.isNil() ? missing : value.asString();
    }

    private static int intOf(ILuaTable opts, String key) {
        ILuaValue value = opts.rawget(key);
        if (value.isNil()) {
            return 0;
        }
        return value.asInt();
    }
}


