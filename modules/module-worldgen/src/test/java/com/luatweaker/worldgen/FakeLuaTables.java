package com.luatweaker.worldgen;

import com.luatweaker.api.vm.ILuaValue;

/** Builders for {@link FakeLuaTable} fixtures. */
final class FakeLuaTables {

    private FakeLuaTables() {
    }

    /** Builds an array-style table from 1-based positional values. */
    static FakeLuaTable listOf(Object... values) {
        FakeLuaTable table = new FakeLuaTable();
        for (int index = 0; index < values.length; index++) {
            table.put(index + 1, values[index]);
        }
        return table;
    }

    static ILuaValue wrap(Object value) {
        if (value instanceof ILuaValue luaValue) {
            return luaValue;
        }
        return new FakeLuaValue(value);
    }
}
