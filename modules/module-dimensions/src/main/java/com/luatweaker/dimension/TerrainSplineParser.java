package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses spline point lists ({@code {input, output}} control points).
 */
final class TerrainSplineParser {

    private TerrainSplineParser() {
    }

    static List<TerrainSpec.SplinePoint> parseSpline(String dimensionId, ILuaValue value, String key) {
        if (value == null || value.isNil()) {
            return List.of();
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException("terrain spec '" + key + "' for '" + dimensionId
                    + "' must be an array of {input, output} points");
        }
        ILuaTable table = value.asTable();
        List<TerrainSpec.SplinePoint> points = new ArrayList<>(table.length());
        for (int i = 1; i <= table.length(); i++) {
            points.add(parseSplinePoint(table.rawget(i), key, i));
        }
        return points;
    }

    private static TerrainSpec.SplinePoint parseSplinePoint(@Nullable ILuaValue entry,
            @NotNull String key, int index) {
        String where = key + "[" + index + "]";
        ILuaTable point = requireSplineTable(entry, where);
        Double input = splineNumber(coalesce(point.rawget("input"), point.rawget(1)));
        Double output = splineNumber(coalesce(point.rawget("output"), point.rawget(2)));
        if (input == null || output == null) {
            throw new IllegalArgumentException(where + " must provide numeric input and output");
        }
        try {
            return new TerrainSpec.SplinePoint(input, output);
        } catch (IllegalArgumentException e) {
            throw LuaTableValues.invalid(where, e);
        }
    }

    @NotNull
    private static ILuaTable requireSplineTable(@Nullable ILuaValue entry, @NotNull String where) {
        if (entry == null || entry.isNil() || !entry.isTable()) {
            throw new IllegalArgumentException(where + " must be a table {input, output}");
        }
        return entry.asTable();
    }

    @Nullable
    private static ILuaValue coalesce(@Nullable ILuaValue first, @Nullable ILuaValue second) {
        if (first != null && !first.isNil()) {
            return first;
        }
        return second;
    }

    @Nullable
    private static Double splineNumber(@Nullable ILuaValue value) {
        if (value == null || !value.isNumber()) {
            return null;
        }
        return value.asDouble();
    }
}
