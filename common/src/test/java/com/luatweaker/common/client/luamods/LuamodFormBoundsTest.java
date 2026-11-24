package com.luatweaker.common.client.luamods;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies responsive indentation and input-box sizing in LuamodConfigFormList. */
class LuamodFormBoundsTest {

    @Test
    void capsDeepJsonNestingIndentationToQuarterRowWidth() {
        int rowWidth = 160;
        int indent = LuamodConfigFormList.clampedIndent(10, rowWidth);

        assertTrue(indent <= rowWidth / 4,
                "deeply nested JSON keys must cap indentation so label and EditBox fit inside narrow rows");
    }

    @Test
    void keepsResponsiveEditBoxWidthWithinRowBounds() {
        int narrowEdit = LuamodConfigFormList.responsiveEditWidth(120);
        int wideEdit = LuamodConfigFormList.responsiveEditWidth(480);

        assertTrue(narrowEdit == 60 && wideEdit == 160,
                "EditBox width must scale between 60px and 160px to preserve label space");
    }
}
