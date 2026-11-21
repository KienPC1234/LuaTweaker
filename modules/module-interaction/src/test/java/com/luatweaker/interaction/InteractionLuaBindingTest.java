package com.luatweaker.interaction;

import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the Lua shape of {@code InteractionLuaBinding.wrapBlock}: the backing {@code __block} field,
 * the metatable hooking property access, and the Id/Dimension properties scripts read.
 */
class InteractionLuaBindingTest {

    private static final String STONE = "minecraft:stone";
    private static final String DIAMOND_ORE = "minecraft:diamond_ore";

    private ILuaEngine engine;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
    }

    private ILuaTable wrapBlock(String id, int x, int y, int z) {
        return InteractionLuaBinding.wrapBlock(engine, createMockBlock(id, x, y, z));
    }

    @Test
    void wrapBlockReturnsATable() {
        assertNotNull(wrapBlock(STONE, 0, 64, 0),
                "wrapBlock must return a Lua table instead of a raw userdata object");
    }

    @Test
    void wrapBlockExposesRawBlockField() {
        ILuaTable blockTable = wrapBlock(STONE, 0, 64, 0);
        assertNotNull(blockTable.rawget("__block"),
                "a wrapped block must keep its Java object in the __block field");
    }

    @Test
    void wrapBlockRawBlockFieldIsNotNil() {
        ILuaTable blockTable = wrapBlock(STONE, 0, 64, 0);
        assertFalse(blockTable.rawget("__block").isNil(),
                "the __block field must hold a real value, not Lua nil");
    }

    @Test
    void wrapBlockHasMetatableForPropertyAccess() {
        ILuaTable blockTable = wrapBlock(STONE, 0, 64, 0);
        assertNotNull(blockTable.rawget("__metatable"),
                "a wrapped block must reference its property-access metatable");
    }

    @Test
    void wrapBlockExposesIdAndDimensionToLua() {
        ILuaTable blockTable = wrapBlock(DIAMOND_ORE, 10, 65, -20);
        engine.getGlobalEnvironment().rawset("testBlock", blockTable);
        assertDoesNotThrow(() -> engine.executeStringOrThrow("""
                assert(testBlock.Id == "minecraft:diamond_ore", "id mismatch")
                assert(testBlock.Dimension == "minecraft:overworld", "dimension mismatch")
                """, "test_block_props"),
                "a wrapped block must expose its id and dimension as Lua properties");
    }

    private IInteractableBlock createMockBlock(String id, int x, int y, int z) {
        return new IInteractableBlock() {
            @Override public String getId() { return id; }
            @Override public void setId(String blockId) { }
            @Override public int getX() { return x; }
            @Override public int getY() { return y; }
            @Override public int getZ() { return z; }
            @Override public String getDimension() { return "minecraft:overworld"; }
            @Override public boolean breakBlock() { return true; }
            @Override public boolean useBlock(Object actorEntity) { return true; }
            @Override public Object getRawBlockState() { return "mock_block_state"; }
        };
    }
}
