package com.luatweaker.interaction;

import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class BlockLuaBindingTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
    }

    @Test
    void wrapBlockExposesPropertiesAndMethodsToLua() {
        MockBlock mock = new MockBlock("minecraft:furnace", 10, 64, 20);
        mock.properties.put("lit", "false");
        ILuaTable blockTable = InteractionLuaBinding.wrapBlock(engine, mock);
        engine.getGlobalEnvironment().rawset("testBlock", blockTable);

        Assertions.assertAll("wrapped block properties and methods",
                () -> Assertions.assertDoesNotThrow(() -> engine.executeStringOrThrow("""
                        assert(testBlock.Id == "minecraft:furnace", "block id mismatch")
                        assert(testBlock.Biome == "minecraft:plains", "biome mismatch")
                        assert(testBlock.RedstoneSignal == 15, "redstone signal mismatch")
                        assert(testBlock:getId() == "minecraft:furnace", "getId method mismatch")
                        assert(testBlock:GetId() == "minecraft:furnace", "GetId method mismatch")
                        assert(testBlock:getProperty("lit") == "false", "getProperty mismatch")
                        assert(testBlock:applyProperty("lit", "true") == true, "applyProperty failed")
                        assert(testBlock:getProperty("lit") == "true", "updated getProperty mismatch")
                        testBlock:explode(4.0)
                        """, "test_block_props"),
                        "a wrapped block must expose properties and methods to Lua"),
                () -> Assertions.assertTrue(mock.exploded, "block explode method must have been invoked"),
                () -> Assertions.assertEquals("true", mock.properties.get("lit"), "block property must be updated")
        );
    }

    private static final class MockBlock implements IInteractableBlock {
        private String id;
        private final int x;
        private final int y;
        private final int z;
        final Map<String, String> properties = new ConcurrentHashMap<>();
        boolean exploded;

        MockBlock(String id, int x, int y, int z) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override public @NotNull String getId() { return id; }
        @Override public void setId(@NotNull String blockId) { this.id = blockId; }
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int getZ() { return z; }
        @Override public @NotNull String getDimension() { return "minecraft:overworld"; }
        @Override public boolean breakBlock() { return true; }
        @Override public boolean useBlock(@NotNull Object actorEntity) { return true; }
        @Override public @NotNull Object getRawBlockState() { return "mock_state"; }

        @Override public int getRedstoneSignal() { return 15; }
        @Override public @NotNull String getBiome() { return "minecraft:plains"; }
        @Override public @Nullable String getProperty(@NotNull String name) { return properties.get(name); }
        @Override public boolean applyProperty(@NotNull String name, @NotNull String value) {
            properties.put(name, value);
            return true;
        }
        @Override public @NotNull Map<String, String> getProperties() { return Map.copyOf(properties); }
        @Override public void explode(float power, boolean causesFire, boolean breakBlocks) { exploded = true; }
    }
}
