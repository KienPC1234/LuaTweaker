package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Lua {@code Curve} table: pure path evaluation for particle trails, projectile steering and
 * camera paths. It returns a fresh Vector3 table and never moves anything by itself.
 */
final class CurveLuaBinding {

    private CurveLuaBinding() {
    }

    static void register(@NotNull ILuaEngine engine) {
        ILuaTable curveTable = engine.createTable();
        curveTable.rawset("Line", args -> {
            int off = LuaBinder.getOffset(args);
            requireArgs(args, off, 3, "Curve.Line(t, from, to)");
            double t = args[off].asDouble();
            double[] from = readVector(args[off + 1], "Curve.Line from");
            double[] to = readVector(args[off + 2], "Curve.Line to");
            double[] point = Curves.line(t, from, to);
            return toVector(engine, point[0], point[1], point[2]);
        });
        curveTable.rawset("Bezier", args -> {
            int off = LuaBinder.getOffset(args);
            requireArgs(args, off, 2, "Curve.Bezier(t, controlPoints)");
            double t = args[off].asDouble();
            List<double[]> points = readPoints(args[off + 1], "Curve.Bezier");
            double[] point = Curves.bezier(t, points);
            return toVector(engine, point[0], point[1], point[2]);
        });
        curveTable.rawset("CatmullRom", args -> {
            int off = LuaBinder.getOffset(args);
            requireArgs(args, off, 2, "Curve.CatmullRom(t, points)");
            double t = args[off].asDouble();
            List<double[]> points = readPoints(args[off + 1], "Curve.CatmullRom");
            double[] point = Curves.catmullRom(t, points);
            return toVector(engine, point[0], point[1], point[2]);
        });
        engine.registerGlobal("Curve", curveTable);
    }

    private static ILuaValue toVector(ILuaEngine engine, double x, double y, double z) {
        return VectorTables.createVector3(engine, x, y, z);
    }

    private static List<double[]> readPoints(@NotNull ILuaValue value, @NotNull String label) {
        if (value == null || !value.isTable()) {
            throw new IllegalArgumentException(label + " needs a 1-based table of Vector3 points");
        }
        ILuaTable table = value.asTable();
        int length = table.length();
        List<double[]> points = new ArrayList<>(length);
        for (int index = 1; index <= length; index++) {
            points.add(readVector(table.rawget(index), label + " point #" + index));
        }
        return points;
    }

    private static double[] readVector(ILuaValue value, @NotNull String label) {
        if (value == null || !value.isTable()) {
            throw new IllegalArgumentException(label + " must be a Vector3 table");
        }
        ILuaTable table = value.asTable();
        ILuaValue x = table.rawget("X");
        ILuaValue y = table.rawget("Y");
        ILuaValue z = table.rawget("Z");
        return toPoint(x, y, z, label);
    }

    private static double[] toPoint(ILuaValue x, ILuaValue y, ILuaValue z, String label) {
        if (!isNumber(x) || !isNumber(y) || !isNumber(z)) {
            throw new IllegalArgumentException(label + " needs numeric X, Y and Z fields");
        }
        return new double[] { x.asDouble(), y.asDouble(), z.asDouble() };
    }

    private static boolean isNumber(ILuaValue value) {
        return value != null && value.isNumber();
    }

    private static void requireArgs(ILuaValue[] args, int off, int required, String usage) {
        if (args.length - off < required) {
            throw new IllegalArgumentException(usage + " requires " + required + " arguments");
        }
    }
}
