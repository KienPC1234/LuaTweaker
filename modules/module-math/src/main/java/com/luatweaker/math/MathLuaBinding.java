package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public final class MathLuaBinding {

    private static final String NEW_KEY = "new";
    private static final int MIN_ONE_ARG = 1;
    private static final int MIN_TWO_ARGS = 2;
    private static final int MIN_THREE_ARGS = 3;

    private MathLuaBinding() {}

    public static void registerBindings(@NotNull ILuaEngine engine) {
        registerVector3(engine);
        registerVector2(engine);
        registerColor3(engine);
        registerInstance(engine);
        registerMathExtensions(engine);
        registerStringExtensions(engine);
        CurveLuaBinding.register(engine);
        Region3LuaBinding.register(engine);

        // Register bootstrapped Roblox task and Signal as services
        ILuaValue taskService = engine.getGlobalEnvironment().rawget("task");
        if (taskService != null && !taskService.isNil()) {
            engine.registerService("Task", taskService);
        }
        ILuaValue sigService = engine.getGlobalEnvironment().rawget("Signal");
        if (sigService != null && !sigService.isNil()) {
            engine.registerService("Signal", sigService);
        }
    }

    private static void registerVector3(ILuaEngine engine) {
        ILuaTable v3Table = engine.createTable();
        v3Table.rawset(NEW_KEY, args -> {
            int off = LuaBinder.getOffset(args);
            double x = args.length - off >= 1 ? args[off].asDouble() : 0;
            double y = args.length - off >= 2 ? args[off + 1].asDouble() : 0;
            double z = args.length - off >= 3 ? args[off + 2].asDouble() : 0;
            return VectorTables.createVector3(engine, x, y, z);
        });
        v3Table.rawset("zero", VectorTables.createVector3(engine, 0, 0, 0));
        v3Table.rawset("one", VectorTables.createVector3(engine, 1, 1, 1));
        engine.registerGlobal("Vector3", v3Table);
    }

    private static void registerVector2(ILuaEngine engine) {
        ILuaTable v2Table = engine.createTable();
        v2Table.rawset(NEW_KEY, args -> {
            int off = LuaBinder.getOffset(args);
            double x = args.length - off >= 1 ? args[off].asDouble() : 0;
            double y = args.length - off >= 2 ? args[off + 1].asDouble() : 0;
            return VectorTables.createVector2(engine, x, y);
        });
        v2Table.rawset("zero", VectorTables.createVector2(engine, 0, 0));
        v2Table.rawset("one", VectorTables.createVector2(engine, 1, 1));
        engine.registerGlobal("Vector2", v2Table);
    }

    private static void registerColor3(ILuaEngine engine) {
        ILuaTable c3Table = engine.createTable();
        c3Table.rawset(NEW_KEY, args -> {
            int off = LuaBinder.getOffset(args);
            double r = args.length - off >= 1 ? args[off].asDouble() : 0;
            double g = args.length - off >= 2 ? args[off + 1].asDouble() : 0;
            double b = args.length - off >= 3 ? args[off + 2].asDouble() : 0;
            return createColor3(engine, r, g, b);
        });
        c3Table.rawset("fromRGB", args -> {
            int off = LuaBinder.getOffset(args);
            double r = args.length - off >= 1 ? args[off].asDouble() : 0;
            double g = args.length - off >= 2 ? args[off + 1].asDouble() : 0;
            double b = args.length - off >= 3 ? args[off + 2].asDouble() : 0;
            return createColor3(engine, r / 255.0, g / 255.0, b / 255.0);
        });
        c3Table.rawset("fromHex", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return createColor3(engine, 0, 0, 0);
            }
            Color3 color = Color3.fromHex(args[off].asString());
            return createColor3(engine, color.r(), color.g(), color.b());
        });
        c3Table.rawset("black", createColor3(engine, 0, 0, 0));
        c3Table.rawset("white", createColor3(engine, 1, 1, 1));
        engine.registerGlobal("Color3", c3Table);
    }

    private static void registerInstance(ILuaEngine engine) {
        ILuaTable instConstructor = engine.createTable();
        instConstructor.rawset(NEW_KEY, args -> {
            if (args.length < MIN_TWO_ARGS) {
                throw new IllegalArgumentException("Instance.new requires a className string");
            }
            String className = args[1].asString();
            LuaInstance parent = InstanceTables.parseInstanceParent(args);
            LuaInstance inst = new LuaInstance(className, parent);
            return InstanceTables.createInstance(engine, inst);
        });
        engine.registerGlobal("Instance", instConstructor);
    }

    private static void registerMathExtensions(ILuaEngine engine) {
        ILuaValue globalMath = engine.getGlobalEnvironment().rawget("math");
        if (globalMath == null || !globalMath.isTable()) {
            return;
        }
        ILuaTable mathTbl = globalMath.asTable();
        mathTbl.rawset("clamp", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_THREE_ARGS) {
                return engine.wrapNumber(0);
            }
            double val = args[off].asDouble();
            double min = args[off + 1].asDouble();
            double max = args[off + 2].asDouble();
            return engine.wrapNumber(Math.clamp(val, min, max));
        });
        mathTbl.rawset("sign", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber(Math.signum(args[off].asDouble()));
        });
        mathTbl.rawset("round", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber((double) Math.round(args[off].asDouble()));
        });
        mathTbl.rawset("lerp", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_THREE_ARGS) {
                return engine.wrapNumber(0);
            }
            double a = args[off].asDouble();
            double b = args[off + 1].asDouble();
            double t = args[off + 2].asDouble();
            return engine.wrapNumber(a + (b - a) * t);
        });
    }

    private static void registerStringExtensions(ILuaEngine engine) {
        ILuaValue globalString = engine.getGlobalEnvironment().rawget("string");
        if (globalString == null || !globalString.isTable()) {
            return;
        }
        ILuaTable strTbl = globalString.asTable();
        strTbl.rawset("split", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.createTable();
            }
            String input = args[off].asString();
            String sep = args.length - off >= 2 ? args[off + 1].asString() : ",";
            String[] parts = input.split(Pattern.quote(sep));
            ILuaTable result = engine.createTable();
            for (int i = 0; i < parts.length; i++) {
                result.rawset(i + 1, engine.wrapString(parts[i]));
            }
            return result;
        });
        strTbl.rawset("trim", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.wrapString("");
            }
            return engine.wrapString(args[off].asString().trim());
        });
    }

    public static ILuaTable createColor3(ILuaEngine engine, double r, double g, double b) {
        ILuaTable colorTable = engine.createTable();
        colorTable.rawset("R", r);
        colorTable.rawset("G", g);
        colorTable.rawset("B", b);
        colorTable.rawset("r", r);
        colorTable.rawset("g", g);
        colorTable.rawset("b", b);

        colorTable.rawset("toRGBInt", args -> {
            int rInt = (int) Math.clamp(r * 255.0, 0, 255);
            int gInt = (int) Math.clamp(g * 255.0, 0, 255);
            int bInt = (int) Math.clamp(b * 255.0, 0, 255);
            return engine.wrapNumber((rInt << 16) | (gInt << 8) | bInt);
        });

        colorTable.rawset("Lerp", args -> {
            ILuaTable other = args[1].asTable();
            double alpha = args[2].asDouble();
            double oR = other.rawget("R").asDouble();
            double oG = other.rawget("G").asDouble();
            double oB = other.rawget("B").asDouble();
            return createColor3(engine, r + (oR - r) * alpha, g + (oG - g) * alpha, b + (oB - b) * alpha);
        });

        ILuaTable metaTable = engine.createTable();
        metaTable.rawset("__index", colorTable);
        metaTable.rawset("__eq", args -> {
            ILuaTable a = args[0].asTable();
            ILuaTable bVal = args[1].asTable();
            return engine.wrapBoolean(a.rawget("R").asDouble() == bVal.rawget("R").asDouble() &&
                                      a.rawget("G").asDouble() == bVal.rawget("G").asDouble() &&
                                      a.rawget("B").asDouble() == bVal.rawget("B").asDouble());
        });
        metaTable.rawset("__tostring", args -> {
            ILuaTable self = args[0].asTable();
            return engine.wrapString(self.rawget("R").asDouble() + ", " + self.rawget("G").asDouble() + ", " + self.rawget("B").asDouble());
        });
        metaTable.rawset("__newindex", args -> {
            throw new IllegalArgumentException("Color3 properties are read-only");
        });

        colorTable.setMetatable(metaTable);
        return colorTable;
    }
}
