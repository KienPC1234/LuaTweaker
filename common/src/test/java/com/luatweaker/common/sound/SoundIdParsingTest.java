package com.luatweaker.common.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Sound ids must parse with a minecraft default namespace. */
class SoundIdParsingTest {

    private static final String HARP_ID = "block.note_block.harp";

    @Test
    void unnamespacedIdIsNotNull() {
        assertNotNull(SoundHelper.parseId(HARP_ID),
                "a plain sound path must parse to a resource id");
    }

    @Test
    void unnamespacedIdDefaultsToMinecraft() {
        assertEquals("minecraft", SoundHelper.parseId(HARP_ID).getNamespace(),
                "a plain sound path must default to the minecraft namespace");
    }

    @Test
    void unnamespacedIdKeepsPath() {
        assertEquals(HARP_ID, SoundHelper.parseId(HARP_ID).getPath(),
                "a plain sound path must keep its full path");
    }

    @Test
    void namespacedIdIsNotNull() {
        assertNotNull(SoundHelper.parseId("mymod:ruby_equip"),
                "a namespaced sound id must parse to a resource id");
    }

    @Test
    void namespacedIdKeepsNamespace() {
        assertEquals("mymod", SoundHelper.parseId("mymod:ruby_equip").getNamespace(),
                "a namespaced sound id must keep its namespace");
    }

    @Test
    void namespacedIdKeepsPath() {
        assertEquals("ruby_equip", SoundHelper.parseId("mymod:ruby_equip").getPath(),
                "a namespaced sound id must keep its path");
    }
}
