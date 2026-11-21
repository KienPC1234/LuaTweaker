package com.luatweaker.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Stair shapes record their base block; the naming convention is the fallback. */
class StairsBlockTest extends ContentFixture {

    private static final String MARBLE_STEP = "luatweaker:marble_step";
    private static final String MARBLE_BLOCK = "luatweaker:marble_block";
    private static final String RUNE_WALL = "luatweaker:rune_wall";
    private static final String PLAIN_BLOCK = "luatweaker:plain";
    private static final String RUBY_STAIRS = "luatweaker:ruby_stairs";
    private static final String STAIRS_CHUNK = "stairs_shape";
    private static final String STAIRS_NO_BASE_CHUNK = "stairs_no_base";

    @Test
    void stairsShapeIsStairs() {
        engine.executeStringOrThrow(
                "startup:createStairs('" + MARBLE_STEP + "', '" + MARBLE_BLOCK + "')",
                STAIRS_CHUNK);

        assertEquals("STAIRS", block(MARBLE_STEP).getEffectiveShape(),
                "an explicit createStairs id must not need a _stairs suffix");
    }

    @Test
    void stairsBaseBlockRecorded() {
        engine.executeStringOrThrow(
                "startup:createStairs('" + MARBLE_STEP + "', '" + MARBLE_BLOCK + "')",
                STAIRS_CHUNK);

        assertEquals(MARBLE_BLOCK, block(MARBLE_STEP).getBaseBlockId(),
                "the base block must reach the registrar/assets, not be dropped");
    }

    @Test
    void wallSuffixKeepsWallShape() {
        contentService.createBlock(RUNE_WALL, builder -> { });

        assertEquals("WALL", block(RUNE_WALL).getEffectiveShape(),
                "legacy _wall ids must keep their shape");
    }

    @Test
    void plainIdHasBlockShape() {
        contentService.createBlock(PLAIN_BLOCK, builder -> { });

        assertEquals("BLOCK", block(PLAIN_BLOCK).getEffectiveShape(),
                "a plain id must default to the block shape");
    }

    @Test
    void stairsWithoutBaseLeavesNullBase() {
        engine.executeStringOrThrow("startup:createStairs('" + RUBY_STAIRS + "')", STAIRS_NO_BASE_CHUNK);

        assertNull(block(RUBY_STAIRS).getBaseBlockId(),
                "omitting the base must fall back to <id>_block textures, not repeat the stair id");
    }
}
