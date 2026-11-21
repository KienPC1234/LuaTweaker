package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins World:FillBlocks / World:ReplaceBlocks: argument parsing (optional dimension prefix and
 * properties map), count round-trip, the loud -1 failure path and the camel-case aliases.
 */
class WorldBulkEditBindingTest extends WorldEditFixture {

    @Test
    void fillBlocksRecordsCornersBlockIdAndProperties() {
        executeLua(engine, """
                local count = World:FillBlocks(1, 2, 3, 4, 5, 6, 'minecraft:stone',
                        { waterlogged = 'true' })
                assert(count == 42, 'count must round-trip: ' .. tostring(count))
                """, "fill_test");

        assertEquals(new WorldEditRecorder.FillCall(OVERWORLD, List.of(1, 2, 3, 4, 5, 6),
                        "minecraft:stone", Map.of("waterlogged", "true")),
                recorder.fillCalls.get(0),
                "FillBlocks must forward both corners, the block id and the property map "
                        + "with the overworld default");
    }

    @Test
    void fillBlocksAcceptsADimensionPrefix() {
        executeLua(engine, """
                local count = World:FillBlocks('minecraft:the_nether', 0, 0, 0, 1, 1, 1,
                        'minecraft:netherrack')
                assert(count == 42)
                """, "fill_dim_test");

        assertEquals(NETHER, recorder.fillCalls.get(0).dimension(),
                "an explicit dimension prefix must replace the overworld default");
    }

    @Test
    void fillBlocksPrefixKeepsTheBlockIdAfterTheDimension() {
        executeLua(engine, """
                local count = World:FillBlocks('minecraft:the_nether', 0, 0, 0, 1, 1, 1,
                        'minecraft:netherrack')
                assert(count == 42)
                """, "fill_dim_test");

        assertEquals("minecraft:netherrack", recorder.fillCalls.get(0).blockId(),
                "the dimension prefix must not shift the block id argument");
    }

    @Test
    void replaceBlocksRecordsCornersAndBothIds() {
        executeLua(engine, """
                local count = World:ReplaceBlocks(0, 0, 0, 2, 2, 2, 'minecraft:stone',
                        'minecraft:cobblestone')
                assert(count == 7, 'count must round-trip: ' .. tostring(count))
                """, "replace_test");

        assertEquals(new WorldEditRecorder.ReplaceCall(OVERWORLD, List.of(0, 0, 0, 2, 2, 2),
                        "minecraft:stone", "minecraft:cobblestone"),
                recorder.replaceCalls.get(0),
                "ReplaceBlocks must forward both corners and both block ids "
                        + "with the overworld default");
    }

    @Test
    void replaceBlocksAcceptsADimensionPrefix() {
        executeLua(engine, """
                local count = World:ReplaceBlocks('minecraft:the_nether', 0, 0, 0, 1, 1, 1,
                        'minecraft:stone', 'minecraft:cobblestone')
                assert(count == 7)
                """, "replace_dim_test");

        assertEquals(NETHER, recorder.replaceCalls.get(0).dimension(),
                "an explicit dimension prefix must replace the overworld default");
    }

    @Test
    void missingFillAndReplaceArgumentsReturnMinusOne() {
        executeLua(engine, """
                assert(World:FillBlocks(1, 2, 3) == -1, 'too few fill args must return -1')
                assert(World:ReplaceBlocks(1, 2, 3) == -1, 'too few replace args must return -1')
                assert(World:FillBlocks() == -1, 'no args must return -1')
                """, "fill_missing_args_test");

        assertTrue(recorder.fillCalls.isEmpty() && recorder.replaceCalls.isEmpty(),
                "malformed bulk edits must be rejected before they reach the platform");
    }

    @Test
    void rejectedFillIsForwardedOnce() {
        recorder.fillResult = -1;
        recorder.replaceResult = -1;
        executeLua(engine, """
                assert(World:FillBlocks(0, 0, 0, 1, 1, 1, 'minecraft:stone') == -1)
                assert(World:ReplaceBlocks(0, 0, 0, 1, 1, 1, 'minecraft:stone',
                        'minecraft:cobblestone') == -1)
                """, "fill_reject_test");
        assertEquals(1, recorder.fillCalls.size(),
                "the -1 rejection must come from a single FillBlocks call that reached the platform");
    }

    @Test
    void rejectedReplaceIsForwardedOnce() {
        recorder.fillResult = -1;
        recorder.replaceResult = -1;
        executeLua(engine, """
                assert(World:FillBlocks(0, 0, 0, 1, 1, 1, 'minecraft:stone') == -1)
                assert(World:ReplaceBlocks(0, 0, 0, 1, 1, 1, 'minecraft:stone',
                        'minecraft:cobblestone') == -1)
                """, "fill_reject_test");
        assertEquals(1, recorder.replaceCalls.size(),
                "the -1 rejection must come from a single ReplaceBlocks call that reached the platform");
    }

    @Test
    void fillBlocksAliasIsRegistered() {
        ILuaValue fill = worldFunction("fillBlocks");
        assertTrue(fill != null && fill.isFunction(),
                "World.fillBlocks camel-case alias must be registered as a function");
    }

    @Test
    void replaceBlocksAliasIsRegistered() {
        ILuaValue replace = worldFunction("replaceBlocks");
        assertTrue(replace != null && replace.isFunction(),
                "World.replaceBlocks camel-case alias must be registered as a function");
    }
}
