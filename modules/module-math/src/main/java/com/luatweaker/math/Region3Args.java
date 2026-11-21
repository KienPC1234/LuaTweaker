package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.Nullable;

/**
 * Argument parsing for the Lua {@code Region3} table. Kept apart from the binding so the binding
 * stays a small table factory while every call style (dot, colon, vector, flat numbers) is handled
 * in one place.
 */
final class Region3Args {

    private static final int ONE = 1;
    private static final int TWO = 2;
    private static final int THREE = 3;
    private static final int SIX = 6;
    private static final String MIN_X = "MinX";
    private static final String MIN_Y = "MinY";
    private static final String MIN_Z = "MinZ";
    private static final String MAX_X = "MaxX";
    private static final String MAX_Y = "MaxY";
    private static final String MAX_Z = "MaxZ";

    private Region3Args() {
    }

    /**
     * {@code Region3.new(minVector, maxVector)} passes tables as the first arguments, so the shared
     * self-detection heuristic would mistake {@code minVector} for {@code self}. Look from the end
     * instead: six trailing numbers or two trailing vectors.
     */
    static int newOffset(ILuaValue... args) {
        if (args.length >= SIX && VectorTables.isNumber(args[args.length - SIX])) {
            return args.length - SIX;
        }
        if (args.length >= TWO && args[args.length - TWO].isTable()
                && args[args.length - ONE].isTable()) {
            return args.length - TWO;
        }
        return 0;
    }

    static Region3Math.Box readNewBox(ILuaValue[] args, int off) {
        if (args.length - off >= SIX) {
            return Region3Math.create(args[off].asDouble(), args[off + 1].asDouble(),
                    args[off + 2].asDouble(), args[off + 3].asDouble(),
                    args[off + 4].asDouble(), args[off + 5].asDouble());
        }
        if (args.length - off >= TWO) {
            return Region3Math.create(readVector(args, off, "Region3.new"),
                    readVector(args, off + ONE, "Region3.new"));
        }
        throw new IllegalArgumentException("Region3.new needs (minVector, maxVector) or six coordinates");
    }

    static Region3Math.Box readBox(ILuaValue[] args, int off, String label) {
        return Region3Math.create(
                readFields(args, off, label, MIN_X, MIN_Y, MIN_Z),
                readFields(args, off, label, MAX_X, MAX_Y, MAX_Z));
    }

    static boolean readContains(ILuaValue[] args, Region3Math.Box box) {
        int off = LuaBinder.getOffset(args);
        if (args.length - off >= THREE) {
            return box.contains(args[off].asDouble(), args[off + ONE].asDouble(),
                    args[off + 2].asDouble());
        }
        Region3Math.Point point = readVector(args, off, "Region3.Contains");
        return box.contains(point.x(), point.y(), point.z());
    }

    static double readScalar(ILuaValue[] args, int off, String label) {
        if (args.length - off < ONE) {
            throw new IllegalArgumentException(label + " needs one number argument");
        }
        return args[off].asDouble();
    }

    private static Region3Math.Point readFields(ILuaValue[] args, int off, String label,
                                                String x, String y, String z) {
        if (args.length - off < ONE || !args[off].isTable()) {
            throw new IllegalArgumentException(label + " needs a Region3 table argument");
        }
        ILuaTable table = args[off].asTable();
        return readPoint(table.rawget(x), table.rawget(y), table.rawget(z), label);
    }

    private static Region3Math.Point readVector(ILuaValue[] args, int off, String label) {
        if (args.length - off < ONE || !args[off].isTable()) {
            throw new IllegalArgumentException(label + " needs a Vector3 table");
        }
        ILuaTable table = args[off].asTable();
        return readPoint(table.rawget("X"), table.rawget("Y"), table.rawget("Z"), label);
    }

    private static Region3Math.Point readPoint(@Nullable ILuaValue x, @Nullable ILuaValue y,
                                               @Nullable ILuaValue z, String label) {
        if (!VectorTables.isNumber(x) || !VectorTables.isNumber(y) || !VectorTables.isNumber(z)) {
            throw new IllegalArgumentException(label + " needs numeric X, Y and Z fields");
        }
        return new Region3Math.Point(x.asDouble(), y.asDouble(), z.asDouble());
    }
}
