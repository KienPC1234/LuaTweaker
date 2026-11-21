package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

/**
 * Lua {@code Region3} table: a plain axis-aligned box used for claim regions, spawn zones and
 * anti-cheat bounds. It only answers geometry questions; persistence and permissions stay in Lua
 * (for example via the Storage service).
 */
final class Region3LuaBinding {

    private static final String NEW = "new";
    private static final String FROM_COORDINATES = "FromCoordinates";
    private static final String KEY_CONTAINS = "Contains";
    private static final String KEY_INTERSECTS = "Intersects";
    private static final String KEY_EXPAND = "Expand";
    private static final String KEY_UNION = "Union";
    private static final String KEY_VOLUME = "Volume";
    private static final String KEY_CENTER = "Center";
    private static final String KEY_SIZE = "Size";

    private Region3LuaBinding() {
    }

    static void register(@NotNull ILuaEngine engine) {
        ILuaTable region = engine.createTable();
        region.rawset(NEW,
                args -> createRegion(engine, Region3Args.readNewBox(args, Region3Args.newOffset(args))));
        region.rawset(FROM_COORDINATES,
                args -> createRegion(engine, Region3Args.readNewBox(args, LuaBinder.getOffset(args))));
        engine.registerGlobal("Region3", region);
    }

    private static ILuaTable createRegion(ILuaEngine engine, Region3Math.Box box) {
        ILuaTable table = engine.createTable();
        table.rawset("MinX", box.minX());
        table.rawset("MinY", box.minY());
        table.rawset("MinZ", box.minZ());
        table.rawset("MaxX", box.maxX());
        table.rawset("MaxY", box.maxY());
        table.rawset("MaxZ", box.maxZ());
        table.rawset("Min", toVector(engine, box.minX(), box.minY(), box.minZ()));
        table.rawset("Max", toVector(engine, box.maxX(), box.maxY(), box.maxZ()));
        table.rawset(KEY_CONTAINS, args -> engine.wrapBoolean(Region3Args.readContains(args, box)));
        table.rawset(KEY_INTERSECTS, args -> engine.wrapBoolean(box.intersects(
                Region3Args.readBox(args, LuaBinder.getOffset(args), KEY_INTERSECTS))));
        table.rawset(KEY_EXPAND, args -> createRegion(engine, box.expand(
                Region3Args.readScalar(args, LuaBinder.getOffset(args), KEY_EXPAND))));
        table.rawset(KEY_UNION, args -> createRegion(engine, box.union(
                Region3Args.readBox(args, LuaBinder.getOffset(args), KEY_UNION))));
        table.rawset(KEY_VOLUME, args -> engine.wrapNumber(box.volume()));
        table.rawset(KEY_CENTER, args -> {
            Region3Math.Point center = box.center();
            return toVector(engine, center.x(), center.y(), center.z());
        });
        table.rawset(KEY_SIZE, args -> {
            Region3Math.Point size = box.size();
            return toVector(engine, size.x(), size.y(), size.z());
        });
        return table;
    }

    private static ILuaTable toVector(ILuaEngine engine, double x, double y, double z) {
        return VectorTables.createVector3(engine, x, y, z);
    }
}
