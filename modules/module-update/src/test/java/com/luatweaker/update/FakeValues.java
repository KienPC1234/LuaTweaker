package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaValue;

import java.util.List;
import java.util.Map;

/**
 * Converts plain Java values into fake Lua values: maps and lists become fake tables, everything
 * else is wrapped as-is.
 */
final class FakeValues {

    private FakeValues() {
    }

    static ILuaValue toLuaValue(Object obj) {
        if (obj == null) {
            return FakeValue.NIL;
        }
        if (obj instanceof ILuaValue value) {
            return value;
        }
        if (obj instanceof Map<?, ?> map) {
            return tableFrom(map);
        }
        if (obj instanceof List<?> list) {
            return tableFrom(list);
        }
        return new FakeValue(obj);
    }

    private static FakeTable tableFrom(Map<?, ?> map) {
        FakeTable table = new FakeTable();
        map.forEach((key, value) -> table.rawset(String.valueOf(key), toLuaValue(value)));
        return table;
    }

    private static FakeTable tableFrom(List<?> list) {
        FakeTable table = new FakeTable();
        for (int index = 0; index < list.size(); index++) {
            table.rawset(index + 1, toLuaValue(list.get(index)));
        }
        return table;
    }
}
