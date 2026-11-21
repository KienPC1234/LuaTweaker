package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds block state, tick, GUI and signal builder methods. */
final class ContentBlockRuntimeBinding {

    private ContentBlockRuntimeBinding() {
    }

    static void bindBlockState(ILuaEngine engine, ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "booleanState", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 3)) {
                builder.booleanState(args[off].asString(), args[off + 1].asString(), args[off + 2].asString());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "connectionState", args -> {
            int off = LuaBinder.getOffset(args);
            boolean enable = !ContentBindingSupport.hasMinArgs(args, off, 1) || args[off].asBoolean();
            builder.connectionState(enable);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onTick", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onTick((data, ignored) -> {
                    try {
                        ILuaTable dataTable = buildTickDataTable(engine, data);
                        if (dataTable == null) {
                            LuaTweakerLog.get().error(LogStage.SYSTEM,
                                    "Error executing block onTick callback: malformed tick data");
                            return;
                        }
                        engine.callFunction(func, dataTable);
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing block onTick callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });
    }

    private static ILuaTable buildTickDataTable(ILuaEngine engine, Object data) {
        if (!(data instanceof java.util.Map<?, ?> dataMap) || !hasTickNumbers(dataMap)) {
            return null;
        }
        ILuaTable dataTable = engine.createTable();
        dataTable.rawset("X", engine.wrapNumber(intOf(dataMap, "X")));
        dataTable.rawset("Y", engine.wrapNumber(intOf(dataMap, "Y")));
        dataTable.rawset("Z", engine.wrapNumber(intOf(dataMap, "Z")));
        dataTable.rawset("Energy", engine.wrapNumber(intOf(dataMap, "Energy")));
        dataTable.rawset("EnergyCapacity", engine.wrapNumber(intOf(dataMap, "EnergyCapacity")));
        dataTable.rawset("FluidId", engine.wrapString(String.valueOf(dataMap.get("FluidId"))));
        dataTable.rawset("FluidAmount", engine.wrapNumber(intOf(dataMap, "FluidAmount")));
        dataTable.rawset("FluidCapacity", engine.wrapNumber(intOf(dataMap, "FluidCapacity")));
        dataTable.rawset("Progress", engine.wrapNumber(doubleOf(dataMap, "Progress")));
        return dataTable;
    }

    private static final String[] TICK_NUMERIC_KEYS = {"X", "Y", "Z", "Energy", "EnergyCapacity",
            "FluidAmount", "FluidCapacity", "Progress"};

    private static boolean hasTickNumbers(java.util.Map<?, ?> dataMap) {
        for (String key : TICK_NUMERIC_KEYS) {
            if (!(dataMap.get(key) instanceof Number)) {
                return false;
            }
        }
        return true;
    }

    private static int intOf(java.util.Map<?, ?> dataMap, String key) {
        return ((Number) dataMap.get(key)).intValue();
    }

    private static double doubleOf(java.util.Map<?, ?> dataMap, String key) {
        return ((Number) dataMap.get(key)).doubleValue();
    }

    static void bindBlockGuiShapes(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "box", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 6)) {
                builder.box(args[off].asDouble(), args[off + 1].asDouble(), args[off + 2].asDouble(),
                        args[off + 3].asDouble(), args[off + 4].asDouble(), args[off + 5].asDouble());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "facing", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                builder.facing(args[off].asString());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "button", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 6)) {
                builder.button(args[off].asString(), args[off + 1].asInt(), args[off + 2].asInt(),
                        args[off + 3].asInt(), args[off + 4].asInt(), args[off + 5].asString());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "label", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 3)) {
                int color = ContentBindingSupport.hasMinArgs(args, off, 4) ? args[off + 3].asInt() : 0xFFFFFF;
                builder.label(args[off].asInt(), args[off + 1].asInt(), args[off + 2].asString(), color);
            }
            return table;
        });
    }

    static void bindBlockRender(ILuaEngine engine, ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "onRender", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onRender((rendererObj, dataObj) -> {
                    try {
                        ILuaValue rendererVal = rendererObj
                                instanceof com.luatweaker.api.content.IBlockRenderer renderer
                                ? LuaBinder.bindTable(engine, renderer,
                                        com.luatweaker.api.content.IBlockRenderer.class)
                                : engine.toLuaValue(rendererObj);
                        ILuaValue dataVal = engine.toLuaValue(dataObj);
                        engine.callFunction(func, rendererVal, dataVal);
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing block onRender callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });
    }

    static void bindBlockSignals(ILuaEngine engine, ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "onButtonClick", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onButtonClick((posObj, buttonId) -> {
                    try {
                        ILuaValue posVal = engine.toLuaValue(posObj);
                        engine.callFunction(func, posVal, engine.wrapString(buttonId));
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing block onButtonClick callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onNeighborChanged", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onNeighborChanged((posObj, neighborPosObj) -> {
                    try {
                        ILuaValue posVal = engine.toLuaValue(posObj);
                        ILuaValue neighborVal = engine.toLuaValue(neighborPosObj);
                        engine.callFunction(func, posVal, neighborVal);
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing block onNeighborChanged callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });
    }
}


