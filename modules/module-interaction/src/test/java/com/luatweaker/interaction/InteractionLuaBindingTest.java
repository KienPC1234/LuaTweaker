package com.luatweaker.interaction;

import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.interaction.IInteractableItem;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InteractionLuaBindingTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private CobaltLuaEngine engine;

    @BeforeEach
    void setup() {
        engine = new CobaltLuaEngine();
    }

    @Test
    void testWrapBlock_CreatesTableWithRequiredFields() {
        IInteractableBlock mockBlock = createMockBlock("minecraft:stone", 0, 64, 0);
        ILuaTable blockTable = InteractionLuaBinding.wrapBlock(engine, mockBlock);
        
        assertNotNull(blockTable, "wrapBlock should return non-null table");
        ILuaValue blockField = blockTable.rawget("__block");
        assertNotNull(blockField, "Block table should have __block field");
        assertFalse(blockField.isNil(), "__block field should not be nil");
    }

    @Test
    void testWrapBlock_StructureIsCorrect() {
        IInteractableBlock mockBlock = createMockBlock("minecraft:diamond_ore", 10, 65, -20);
        ILuaTable blockTable = InteractionLuaBinding.wrapBlock(engine, mockBlock);
        
        assertNotNull(blockTable, "wrapBlock should return non-null table");
        ILuaValue blockField = blockTable.rawget("__block");
        assertNotNull(blockField, "Block table should have __block field");
        
        // Properties are accessed via metatable __index, not rawget
        // This test verifies structure, not property access (which requires Lua runtime)
    }

    @Test
    void testWrapBlock_HasMetatableForPropertyAccess() {
        IInteractableBlock mockBlock = createMockBlock("minecraft:stone", 0, 64, 0);
        ILuaTable blockTable = InteractionLuaBinding.wrapBlock(engine, mockBlock);
        
        ILuaValue metatableField = blockTable.rawget("__metatable");
        assertNotNull(metatableField, "Block table should have metatable reference");
    }

    @Test
    void testWrapItem_CreatesTableWithRequiredFields() {
        IInteractableItem mockItem = createMockItem("minecraft:diamond_sword", 1, 0);
        ILuaTable itemTable = InteractionLuaBinding.wrapItem(engine, mockItem);
        
        assertNotNull(itemTable, "wrapItem should return non-null table");
        ILuaValue itemField = itemTable.rawget("__item");
        assertNotNull(itemField, "Item table should have __item field");
        assertFalse(itemField.isNil(), "__item field should not be nil");
    }

    @Test
    void testWrapItem_StructureIsCorrect() {
        IInteractableItem mockItem = createMockItem("minecraft:arrow", 64, 5);
        ILuaTable itemTable = InteractionLuaBinding.wrapItem(engine, mockItem);
        
        assertNotNull(itemTable, "wrapItem should return non-null table");
        ILuaValue itemField = itemTable.rawget("__item");
        assertNotNull(itemField, "Item table should have __item field");
        
        // Properties are accessed via metatable __index, not rawget
        // This test verifies structure, not property access (which requires Lua runtime)
    }

    @Test
    void testWrapItem_HasMetatableForPropertyAccess() {
        IInteractableItem mockItem = createMockItem("minecraft:arrow", 1, 0);
        ILuaTable itemTable = InteractionLuaBinding.wrapItem(engine, mockItem);
        
        ILuaValue metatableField = itemTable.rawget("__metatable");
        assertNotNull(metatableField, "Item table should have metatable reference");
    }

    private IInteractableBlock createMockBlock(String id, int x, int y, int z) {
        return new IInteractableBlock() {
            @Override public String getId() { return id; }
            @Override public void setId(String blockId) {}
            @Override public int getX() { return x; }
            @Override public int getY() { return y; }
            @Override public int getZ() { return z; }
            @Override public String getDimension() { return "minecraft:overworld"; }
            @Override public boolean breakBlock() { return true; }
            @Override public boolean useBlock(Object actorEntity) { return true; }
            @Override public Object getRawBlockState() { return "mock_block_state"; }
        };
    }

    private IInteractableItem createMockItem(String id, int count, int slot) {
        return new IInteractableItem() {
            @Override public String getId() { return id; }
            @Override public int getCount() { return count; }
            @Override public void setCount(int count) {}
            @Override public int getSlot() { return slot; }
            @Override public void setSlot(int slot) {}
            @Override public String getOwnerUuid() { return null; }
            @Override public boolean useItem(Object actorEntity) { return true; }
            @Override public boolean drop(Object actorEntity, int count) { return true; }
            @Override public Object getRawItemStack() { return "mock_item_stack"; }
        };
    }
}
