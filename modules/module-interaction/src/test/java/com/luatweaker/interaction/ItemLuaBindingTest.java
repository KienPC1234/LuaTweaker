package com.luatweaker.interaction;

import com.luatweaker.api.interaction.IInteractableItem;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the Lua shape of {@code InteractionLuaBinding.wrapItem}: the backing {@code __item} field,
 * the metatable hooking property access, and the Id/Count/Slot properties scripts read.
 */
class ItemLuaBindingTest {

    private static final String DIAMOND_SWORD = "minecraft:diamond_sword";
    private static final String ARROW = "minecraft:arrow";

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
    }

    private ILuaTable wrapItem(String id, int count, int slot) {
        return InteractionLuaBinding.wrapItem(engine, createMockItem(id, count, slot));
    }

    @Test
    void wrapItemReturnsATable() {
        assertNotNull(wrapItem(DIAMOND_SWORD, 1, 0),
                "wrapItem must return a Lua table instead of a raw userdata object");
    }

    @Test
    void wrapItemExposesRawItemField() {
        ILuaTable itemTable = wrapItem(DIAMOND_SWORD, 1, 0);
        assertNotNull(itemTable.rawget("__item"),
                "a wrapped item must keep its Java object in the __item field");
    }

    @Test
    void wrapItemRawItemFieldIsNotNil() {
        ILuaTable itemTable = wrapItem(DIAMOND_SWORD, 1, 0);
        assertFalse(itemTable.rawget("__item").isNil(),
                "the __item field must hold a real value, not Lua nil");
    }

    @Test
    void wrapItemHasMetatableForPropertyAccess() {
        ILuaTable itemTable = wrapItem(ARROW, 1, 0);
        assertNotNull(itemTable.rawget("__metatable"),
                "a wrapped item must reference its property-access metatable");
    }

    @Test
    void wrapItemExposesIdCountAndSlotToLua() {
        ILuaTable itemTable = wrapItem(ARROW, 64, 5);
        engine.getGlobalEnvironment().rawset("testItem", itemTable);
        assertDoesNotThrow(() -> engine.executeStringOrThrow("""
                assert(testItem.Id == "minecraft:arrow", "item id mismatch")
                assert(testItem.Count == 64, "item count mismatch")
                assert(testItem.Slot == 5, "item slot mismatch")
                assert(testItem:getId() == "minecraft:arrow", "item:getId method mismatch")
                assert(testItem:GetId() == "minecraft:arrow", "item:GetId method mismatch")
                assert(testItem.Rarity == "COMMON", "rarity mismatch")
                assert(testItem.MaxStackSize == 64, "max stack size mismatch")
                assert(testItem.IsEmpty == false, "isEmpty mismatch")
                """, "test_item_props"),
                "a wrapped item must expose its id, count and slot as Lua properties");
    }

    private IInteractableItem createMockItem(String id, int count, int slot) {
        return new IInteractableItem() {
            @Override public String getId() { return id; }
            @Override public int getCount() { return count; }
            @Override public void setCount(int count) { }
            @Override public int getSlot() { return slot; }
            @Override public void setSlot(int slot) { }
            @Override public String getOwnerUuid() { return null; }
            @Override public boolean useItem(Object actorEntity) { return true; }
            @Override public boolean drop(Object actorEntity, int count) { return true; }
            @Override public Object getRawItemStack() { return "mock_item_stack"; }
        };
    }
}
