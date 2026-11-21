package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventPayloadBuilderTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
    }

    @Test
    void buildPosTablePopulatesCoordinatesBothCasings() {
        BlockPos pos = new BlockPos(100, 64, -200);
        ILuaTable posTable = PayloadBlocks.buildPosTable(engine, pos);

        Assertions.assertAll("position table must contain coordinates in both casings",
            () -> Assertions.assertEquals(100, posTable.rawget("X").asInt(), "X must be 100"),
            () -> Assertions.assertEquals(64, posTable.rawget("Y").asInt(), "Y must be 64"),
            () -> Assertions.assertEquals(-200, posTable.rawget("Z").asInt(), "Z must be -200"),
            () -> Assertions.assertEquals(100, posTable.rawget("x").asInt(), "x must be 100"),
            () -> Assertions.assertEquals(64, posTable.rawget("y").asInt(), "y must be 64"),
            () -> Assertions.assertEquals(-200, posTable.rawget("z").asInt(), "z must be -200"));
    }

    @Test
    void wrapEntityReturnsNullForNullInput() {
        Assertions.assertNull(PayloadEntities.wrap(engine, null),
                "null entity must result in null Lua table");
    }
}
