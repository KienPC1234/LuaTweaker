package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

/**
 * Lua table factories for {@code Vector3} and {@code Vector2}: component fields,
 * derived {@code Magnitude}/{@code Unit}, vector methods and operator metamethods.
 */
public final class VectorTables {

    private static final String MAGNITUDE_KEY = "Magnitude";
    private static final String DOT_KEY = "Dot";
    private static final String LERP_KEY = "Lerp";

    private VectorTables() {}

    static boolean isNumber(ILuaValue val) {
        return val != null && val.toJavaObject() instanceof Number;
    }

    public static ILuaTable createVector3(ILuaEngine engine, double x, double y, double z) {
        ILuaTable vecTable = engine.createTable();
        vecTable.rawset("X", x);
        vecTable.rawset("Y", y);
        vecTable.rawset("Z", z);
        vecTable.rawset("x", x);
        vecTable.rawset("y", y);
        vecTable.rawset("z", z);

        double mag = Math.sqrt(x * x + y * y + z * z);
        vecTable.rawset(MAGNITUDE_KEY, mag);
        vecTable.rawset("magnitude", mag);

        double uX = mag == 0 ? 0 : x / mag;
        double uY = mag == 0 ? 0 : y / mag;
        double uZ = mag == 0 ? 0 : z / mag;

        ILuaTable unitTable = engine.createTable();
        unitTable.rawset("X", uX);
        unitTable.rawset("Y", uY);
        unitTable.rawset("Z", uZ);
        unitTable.rawset(MAGNITUDE_KEY, 1.0);
        vecTable.rawset("Unit", unitTable);
        vecTable.rawset("unit", unitTable);

        addVector3Methods(engine, vecTable, x, y, z, mag);
        ILuaTable metaTable = engine.createTable();
        metaTable.rawset("__index", vecTable);
        addVector3Metamethods(engine, metaTable);

        vecTable.setMetatable(metaTable);
        unitTable.setMetatable(metaTable);
        return vecTable;
    }

    private static void addVector3Methods(ILuaEngine engine, ILuaTable vecTable,
                                          double x, double y, double z, double mag) {
        vecTable.rawset(DOT_KEY, args -> {
            int off = (args.length >= 2 && args[0].isTable()) ? 1 : 0;
            double[] other = readVector3Components(args[off].asTable());
            return engine.wrapNumber(x * other[0] + y * other[1] + z * other[2]);
        });
        vecTable.rawset("dot", vecTable.rawget(DOT_KEY));

        vecTable.rawset("Cross", args -> {
            int off = (args.length >= 2 && args[0].isTable()) ? 1 : 0;
            double[] other = readVector3Components(args[off].asTable());
            return createVector3(engine,
                y * other[2] - z * other[1],
                z * other[0] - x * other[2],
                x * other[1] - y * other[0]
            );
        });
        vecTable.rawset("cross", vecTable.rawget("Cross"));

        vecTable.rawset(LERP_KEY, args -> {
            int off = (args.length >= 3 && args[0].isTable()) ? 1 : 0;
            double[] other = readVector3Components(args[off].asTable());
            double alpha = args[off + 1].asDouble();
            return createVector3(engine,
                x + (other[0] - x) * alpha,
                y + (other[1] - y) * alpha,
                z + (other[2] - z) * alpha
            );
        });
        vecTable.rawset("lerp", vecTable.rawget(LERP_KEY));

        vecTable.rawset("Angle", args -> angleBetween(engine, x, y, z, mag, args));
        vecTable.rawset("angle", vecTable.rawget("Angle"));
    }

    private static ILuaValue angleBetween(ILuaEngine engine, double x, double y, double z,
                                          double mag, ILuaValue... args) {
        int off = (args.length >= 2 && args[0].isTable()) ? 1 : 0;
        double[] other = readVector3Components(args[off].asTable());
        double mag2 = Math.sqrt(other[0] * other[0] + other[1] * other[1] + other[2] * other[2]);
        if (mag == 0 || mag2 == 0) {
            return engine.wrapNumber(0);
        }
        double cos = (x * other[0] + y * other[1] + z * other[2]) / (mag * mag2);
        return engine.wrapNumber(Math.acos(Math.clamp(cos, -1.0, 1.0)));
    }

    private static void addVector3Metamethods(ILuaEngine engine, ILuaTable metaTable) {
        metaTable.rawset("__add", args -> {
            double[] a = readVector3Components(args[0].asTable());
            double[] b = readVector3Components(args[1].asTable());
            return createVector3(engine, a[0] + b[0], a[1] + b[1], a[2] + b[2]);
        });
        metaTable.rawset("__sub", args -> {
            double[] a = readVector3Components(args[0].asTable());
            double[] b = readVector3Components(args[1].asTable());
            return createVector3(engine, a[0] - b[0], a[1] - b[1], a[2] - b[2]);
        });
        metaTable.rawset("__mul", args -> {
            ILuaValue first = args[0];
            ILuaValue second = args[1];
            if (first instanceof ILuaTable && isNumber(second)) {
                double[] a = readVector3Components(first.asTable());
                double factor = second.asDouble();
                return createVector3(engine,
                    a[0] * factor,
                    a[1] * factor,
                    a[2] * factor
                );
            } else if (isNumber(first) && second instanceof ILuaTable) {
                double factor = first.asDouble();
                double[] a = readVector3Components(second.asTable());
                return createVector3(engine,
                    a[0] * factor,
                    a[1] * factor,
                    a[2] * factor
                );
            }
            throw new IllegalArgumentException("Vector3 multiplication requires a vector and a scalar number");
        });
        metaTable.rawset("__div", args -> {
            double[] a = readVector3Components(args[0].asTable());
            double factor = args[1].asDouble();
            if (factor == 0) {
                return createVector3(engine, 0, 0, 0);
            }
            return createVector3(engine, a[0] / factor, a[1] / factor, a[2] / factor);
        });
        metaTable.rawset("__unm", args -> {
            double[] a = readVector3Components(args[0].asTable());
            return createVector3(engine, -a[0], -a[1], -a[2]);
        });
        metaTable.rawset("__eq", args -> {
            double[] a = readVector3Components(args[0].asTable());
            double[] b = readVector3Components(args[1].asTable());
            return engine.wrapBoolean(a[0] == b[0] && a[1] == b[1] && a[2] == b[2]);
        });
        metaTable.rawset("__tostring", args -> {
            double[] self = readVector3Components(args[0].asTable());
            return engine.wrapString(self[0] + ", " + self[1] + ", " + self[2]);
        });
        metaTable.rawset("__newindex", args -> {
            throw new IllegalArgumentException("Vector3 properties are read-only");
        });
    }

    private static double[] readVector3Components(ILuaTable other) {
        return new double[]{
            other.rawget("X").asDouble(),
            other.rawget("Y").asDouble(),
            other.rawget("Z").asDouble()
        };
    }

    public static ILuaTable createVector2(ILuaEngine engine, double x, double y) {
        ILuaTable vecTable = engine.createTable();
        vecTable.rawset("X", x);
        vecTable.rawset("Y", y);
        vecTable.rawset("x", x);
        vecTable.rawset("y", y);

        double mag = Math.sqrt(x * x + y * y);
        vecTable.rawset(MAGNITUDE_KEY, mag);

        double uX = mag == 0 ? 0 : x / mag;
        double uY = mag == 0 ? 0 : y / mag;
        ILuaTable unitTable = engine.createTable();
        unitTable.rawset("X", uX);
        unitTable.rawset("Y", uY);
        unitTable.rawset(MAGNITUDE_KEY, 1.0);
        vecTable.rawset("Unit", unitTable);

        addVector2Methods(engine, vecTable, x, y);
        ILuaTable metaTable = engine.createTable();
        metaTable.rawset("__index", vecTable);
        addVector2Metamethods(engine, metaTable);

        vecTable.setMetatable(metaTable);
        unitTable.setMetatable(metaTable);
        return vecTable;
    }

    private static void addVector2Methods(ILuaEngine engine, ILuaTable vecTable, double x, double y) {
        vecTable.rawset(DOT_KEY, args -> {
            int off = (args.length >= 2 && args[0].isTable()) ? 1 : 0;
            double[] other = readVector2Components(args[off].asTable());
            return engine.wrapNumber(x * other[0] + y * other[1]);
        });
        vecTable.rawset("dot", vecTable.rawget(DOT_KEY));

        vecTable.rawset(LERP_KEY, args -> {
            int off = (args.length >= 3 && args[0].isTable()) ? 1 : 0;
            double[] other = readVector2Components(args[off].asTable());
            double alpha = args[off + 1].asDouble();
            return createVector2(engine, x + (other[0] - x) * alpha, y + (other[1] - y) * alpha);
        });
        vecTable.rawset("lerp", vecTable.rawget(LERP_KEY));
    }

    private static void addVector2Metamethods(ILuaEngine engine, ILuaTable metaTable) {
        metaTable.rawset("__add", args -> {
            double[] a = readVector2Components(args[0].asTable());
            double[] b = readVector2Components(args[1].asTable());
            return createVector2(engine, a[0] + b[0], a[1] + b[1]);
        });
        metaTable.rawset("__sub", args -> {
            double[] a = readVector2Components(args[0].asTable());
            double[] b = readVector2Components(args[1].asTable());
            return createVector2(engine, a[0] - b[0], a[1] - b[1]);
        });
        metaTable.rawset("__mul", args -> {
            ILuaValue first = args[0];
            ILuaValue second = args[1];
            if (first instanceof ILuaTable && isNumber(second)) {
                double[] a = readVector2Components(first.asTable());
                double factor = second.asDouble();
                return createVector2(engine, a[0] * factor, a[1] * factor);
            } else if (isNumber(first) && second instanceof ILuaTable) {
                double factor = first.asDouble();
                double[] a = readVector2Components(second.asTable());
                return createVector2(engine, a[0] * factor, a[1] * factor);
            }
            throw new IllegalArgumentException("Vector2 multiplication requires a vector and a scalar number");
        });
        metaTable.rawset("__div", args -> {
            double[] a = readVector2Components(args[0].asTable());
            double factor = args[1].asDouble();
            if (factor == 0) {
                return createVector2(engine, 0, 0);
            }
            return createVector2(engine, a[0] / factor, a[1] / factor);
        });
        metaTable.rawset("__unm", args -> {
            double[] a = readVector2Components(args[0].asTable());
            return createVector2(engine, -a[0], -a[1]);
        });
        metaTable.rawset("__eq", args -> {
            double[] a = readVector2Components(args[0].asTable());
            double[] b = readVector2Components(args[1].asTable());
            return engine.wrapBoolean(a[0] == b[0] && a[1] == b[1]);
        });
        metaTable.rawset("__tostring", args -> {
            double[] self = readVector2Components(args[0].asTable());
            return engine.wrapString(self[0] + ", " + self[1]);
        });
        metaTable.rawset("__newindex", args -> {
            throw new IllegalArgumentException("Vector2 properties are read-only");
        });
    }

    private static double[] readVector2Components(ILuaTable other) {
        return new double[]{
            other.rawget("X").asDouble(),
            other.rawget("Y").asDouble()
        };
    }
}
