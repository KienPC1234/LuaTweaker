package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies the camelCase/PascalCase field contract every payload builder relies on. */
class PayloadTablesTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
    }

    @Test
    void putBothWritesBothSpellings() {
        ILuaTable table = engine.createTable();
        PayloadTables.putBoth(table, "dimension", engine.wrapString("minecraft:overworld"));

        Assertions.assertAll("both spellings must resolve to the same value",
                () -> Assertions.assertEquals("minecraft:overworld",
                        table.rawget("dimension").asString(), "lowercase key must exist"),
                () -> Assertions.assertEquals("minecraft:overworld",
                        table.rawget("Dimension").asString(), "PascalCase alias must exist"));
    }

    @Test
    void putPascalWritesOnlyTheAlias() {
        ILuaTable table = engine.createTable();
        PayloadTables.putPascal(table, "removeBlock", engine.wrapString("fn"));

        Assertions.assertAll("only the PascalCase alias is written",
                () -> Assertions.assertTrue(isAbsent(table.rawget("removeBlock")),
                        "the lowercase key must stay untouched"),
                () -> Assertions.assertEquals("fn", table.rawget("RemoveBlock").asString(),
                        "the PascalCase alias must exist"));
    }

    private static boolean isAbsent(com.luatweaker.api.vm.ILuaValue value) {
        return value == null || value.isNil();
    }
}
