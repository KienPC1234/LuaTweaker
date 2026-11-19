package com.luatweaker.platform.sound;

import net.minecraft.ResourceLocationException;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SoundHelperTest {

    @Test
    public void unnamespacedIdsDefaultToMinecraft() {
        var id = SoundHelper.parseId("block.note_block.harp");
        assertNotNull(id);
        assertEquals("minecraft", id.getNamespace());
        assertEquals("block.note_block.harp", id.getPath());
    }

    @Test
    public void namespacedIdsArePreserved() {
        var id = SoundHelper.parseId("mymod:ruby_equip");
        assertNotNull(id);
        assertEquals("mymod", id.getNamespace());
        assertEquals("ruby_equip", id.getPath());
    }

    @Test
    public void invalidIdsReturnNullInsteadOfThrowing() {
        assertNull(SoundHelper.parseId(null));
        assertNull(SoundHelper.parseId("   "));
        assertNull(SoundHelper.parseId("Not A Valid Id") , "spaces must be rejected");
        // The reason this matters: NeoForgeEntityWrapper used ResourceLocation.parse(), which
        // threw ResourceLocationException straight into the Lua call.
        assertThrows(ResourceLocationException.class, () -> net.minecraft.resources.ResourceLocation.parse("Not A Valid Id"));
    }

    @Test
    public void sourceParsingAcceptsAnyCaseAndFallsBack() {
        assertEquals(SoundSource.PLAYERS, SoundHelper.parseSource("players", SoundSource.NEUTRAL));
        assertEquals(SoundSource.HOSTILE, SoundHelper.parseSource("HOSTILE", SoundSource.NEUTRAL));
        assertEquals(SoundSource.BLOCKS, SoundHelper.parseSource(null, SoundSource.BLOCKS));
        assertEquals(SoundSource.NEUTRAL, SoundHelper.parseSource("nonsense", SoundSource.NEUTRAL));
    }

    @Test
    public void volumeAndPitchStayAudible() {
        assertEquals(0.0f, SoundHelper.volume(-3.0), 1e-9);
        assertEquals(2.5f, SoundHelper.volume(2.5), 1e-9);
        assertEquals(SoundHelper.MIN_PITCH, SoundHelper.pitch(0.0), 1e-9);
        assertEquals(SoundHelper.MIN_PITCH, SoundHelper.pitch(-1.0), 1e-9);
        assertEquals(1.4f, SoundHelper.pitch(1.4), 1e-9);
    }
}
