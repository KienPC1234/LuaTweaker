package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Parses coordinate arguments shared by the world binding collaborators. */
final class InteractionParse {

    private InteractionParse() {
    }

    /**
     * Reads {@code [dimension,] x, y, z} and returns the cursor of the next argument.
     * A leading string is a dimension (numbered coordinates can never be one).
     */
    static InteractionBindingSupport.CoordinateArgs readCoordinates(ILuaValue[] args, String call) {
        int off = LuaBinder.getOffset(args);
        int dimOff = -1;
        if (InteractionBindingSupport.hasMinArgs(args, off, 2)
                && args[off].toJavaObject() instanceof String
                && args[off + 1].isNumber()) {
            dimOff = off;
            off++;
        }
        if (InteractionBindingSupport.hasMinArgs(args, off, 1) && args[off] != null && args[off].isTable()) {
            InteractionBindingSupport.CoordinateArgs tableArgs = readTableCoordinates(args, off, dimOff);
            if (tableArgs != null) {
                return tableArgs;
            }
        }
        InteractionBindingSupport.CoordinateArgs numberArgs = readNumberCoordinates(args, off, dimOff);
        if (numberArgs == null) {
            InteractionBindingSupport.warn(call + " requires (x, y, z) block coordinates");
            return null;
        }
        return numberArgs;
    }

    private static boolean allNumbers(ILuaValue[] args, int off) {
        return args[off].isNumber() && args[off + 1].isNumber() && args[off + 2].isNumber();
    }

    private static InteractionBindingSupport.CoordinateArgs readNumberCoordinates(ILuaValue[] args, int off, int dimOff) {
        if (!InteractionBindingSupport.hasMinArgs(args, off, 3) || !allNumbers(args, off)) {
            return null;
        }
        String dimension = dimOff >= 0 ? args[dimOff].asString()
                : InteractionBindingSupport.DEFAULT_DIMENSION;
        int x = (int) Math.floor(args[off].asDouble());
        int y = (int) Math.floor(args[off + 1].asDouble());
        int z = (int) Math.floor(args[off + 2].asDouble());
        return new InteractionBindingSupport.CoordinateArgs(dimension, x, y, z, off + 3);
    }

    private static InteractionBindingSupport.CoordinateArgs readTableCoordinates(ILuaValue[] args, int off, int dimOff) {
        ILuaTable posTable = args[off].asTable();
        int[] xyz = tablePosition(posTable);
        if (xyz.length == 0) {
            return null;
        }
        ILuaValue dimVal = posTable.rawget(InteractionBindingSupport.KEY_DIMENSION);
        if (dimVal == null || dimVal.isNil()) {
            dimVal = posTable.rawget(InteractionBindingSupport.KEY_DIMENSION_UPPER);
        }
        String dimension = dimVal != null && !dimVal.isNil() ? dimVal.asString()
                : dimOff >= 0 ? args[dimOff].asString() : InteractionBindingSupport.DEFAULT_DIMENSION;
        return new InteractionBindingSupport.CoordinateArgs(dimension, xyz[0], xyz[1], xyz[2], off + 1);
    }

    /** Reads X/Y/Z (upper- or lowercase) from a position table; empty array when absent. */
    private static final String[][] POSITION_KEYS = {{"X", "x"}, {"Y", "y"}, {"Z", "z"}};

    static int[] tablePosition(ILuaTable posTable) {
        int[] xyz = new int[3];
        for (int i = 0; i < POSITION_KEYS.length; i++) {
            ILuaValue val = posTable.rawget(POSITION_KEYS[i][0]);
            if (val == null || val.isNil()) {
                val = posTable.rawget(POSITION_KEYS[i][1]);
            }
            if (val == null || val.isNil()) {
                return new int[0];
            }
            xyz[i] = (int) Math.floor(val.asDouble());
        }
        return xyz;
    }
}

