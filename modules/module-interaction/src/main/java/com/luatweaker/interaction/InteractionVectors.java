package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

/** Vector and multi-position argument readers for the world binding collaborators. */
final class InteractionVectors {

    private InteractionVectors() {
    }

    record TransferEndpoints(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int cursor) {
    }

    record CuboidRegion(String dimension, int x1, int y1, int z1, int x2, int y2, int z2, int cursor) {
    }

    /** Reads a Vector3-shaped table; empty array when any component is missing or not a number. */
    static double[] readVector(ILuaValue value) {
        if (value == null || !value.isTable()) {
            return new double[0];
        }
        ILuaTable table = value.asTable();
        ILuaValue x = table.rawget(InteractionBindingSupport.KEY_X);
        ILuaValue y = table.rawget(InteractionBindingSupport.KEY_Y);
        ILuaValue z = table.rawget(InteractionBindingSupport.KEY_Z);
        if (x == null || !x.isNumber() || y == null || !y.isNumber() || z == null || !z.isNumber()) {
            return new double[0];
        }
        return new double[] { x.asDouble(), y.asDouble(), z.asDouble() };
    }

    static double[] readNumbers(ILuaValue[] args, int start, int count) {
        if (args.length - start < count) {
            return new double[0];
        }
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            ILuaValue value = args[start + i];
            if (value == null || !value.isNumber()) {
                return new double[0];
            }
            values[i] = value.asDouble();
        }
        return values;
    }

    /**
     * Reads {@code (fromPos, toPos)} table pair or twelve numbers for the energy/fluid/item
     * transfer calls; {@code null} when the shape is unusable.
     */
    static TransferEndpoints readTransferEndpoints(ILuaValue[] args, int off, int needNumbers) {
        int cursor = off;
        if (cursor < args.length && args[cursor].isTable()) {
            return readTableTransfer(args, cursor);
        }
        if (InteractionBindingSupport.hasMinArgs(args, off, needNumbers)) {
            return readNumbersTransfer(args, cursor);
        }
        return null;
    }

    private static TransferEndpoints readTableTransfer(ILuaValue[] args, int start) {
        int cursor = start;
        int[] from = InteractionParse.tablePosition(args[cursor++].asTable());
        if (from.length == 0) {
            return null;
        }
        if (cursor >= args.length || !args[cursor].isTable()) {
            return null;
        }
        int[] to = InteractionParse.tablePosition(args[cursor++].asTable());
        if (to.length == 0) {
            return null;
        }
        return new TransferEndpoints(from[0], from[1], from[2], to[0], to[1], to[2], cursor);
    }

    private static TransferEndpoints readNumbersTransfer(ILuaValue[] args, int start) {
        int cursor = start;
        int fromX = args[cursor++].asInt();
        int fromY = args[cursor++].asInt();
        int fromZ = args[cursor++].asInt();
        int toX = args[cursor++].asInt();
        int toY = args[cursor++].asInt();
        int toZ = args[cursor++].asInt();
        return new TransferEndpoints(fromX, fromY, fromZ, toX, toY, toZ, cursor);
    }

    /**
     * Reads an optional leading dimension plus two corner positions (tables or six numbers).
     * {@code null} when neither corner shape parses.
     */
    static CuboidRegion readCuboidRegion(ILuaValue[] args, int off) {
        String dimension = InteractionBindingSupport.DEFAULT_DIMENSION;
        int cursor = off;
        if (cursor < args.length && args[cursor].toJavaObject() instanceof String
                && !args[cursor].isTable()) {
            dimension = args[cursor].asString();
            cursor++;
        }
        if (cursor < args.length && args[cursor].isTable()) {
            return readTableCuboid(args, cursor, dimension);
        }
        if (cursor + 5 < args.length) {
            return readNumbersCuboid(args, cursor, dimension);
        }
        return null;
    }

    private static CuboidRegion readTableCuboid(ILuaValue[] args, int start, String dimension) {
        int cursor = start;
        int[] first = InteractionParse.tablePosition(args[cursor++].asTable());
        if (first.length == 0) {
            return null;
        }
        if (cursor >= args.length || !args[cursor].isTable()) {
            return null;
        }
        int[] second = InteractionParse.tablePosition(args[cursor++].asTable());
        if (second.length == 0) {
            return null;
        }
        return new CuboidRegion(dimension, first[0], first[1], first[2],
                second[0], second[1], second[2], cursor);
    }

    private static CuboidRegion readNumbersCuboid(ILuaValue[] args, int start, String dimension) {
        int cursor = start;
        int x1 = args[cursor++].asInt();
        int y1 = args[cursor++].asInt();
        int z1 = args[cursor++].asInt();
        int x2 = args[cursor++].asInt();
        int y2 = args[cursor++].asInt();
        int z2 = args[cursor++].asInt();
        return new CuboidRegion(dimension, x1, y1, z1, x2, y2, z2, cursor);
    }
}

