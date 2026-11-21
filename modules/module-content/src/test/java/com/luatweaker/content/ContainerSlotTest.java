package com.luatweaker.content;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Container slot textures, locks and per-slot positions round-trip. */
class ContainerSlotTest extends ContentFixture {

    private static final String SLOT_CRATE = "slot_crate";

    private static final String SLOT_CHUNK = "slot_chunk";
    private static final String SLOT_TEXTURE = "luatweaker:textures/gui/wood_crate_slot.png";
    private static final String SLOT_SCRIPT = """
            local Content = require("LuaTweaker.Content")
            local crate = Content.NewBlock("slot_crate")
                :Container(4, 6, "packed")
                :SlotTexture("luatweaker:textures/gui/wood_crate_slot.png")
                :LockSlot(0, true)
                :LockedSlots({ 5, 11 })
                :SlotPosition(23, 44, 89)
                :Register()
            local openCrate = Content.NewBlock("open_crate")
                :Container(2, 3, "spill")
                :LockSlot(2, true)
                :LockSlot(2, false)
                :Register()
        """;
    private static final int[] EXPECTED_SLOT_POS = {44, 89};

    private void runSlotScript() throws IOException {
        engine.executeScript(scriptFile(SLOT_SCRIPT), SLOT_CHUNK);
    }

    @Test
    void twoBlocksRegistered() throws IOException {
        runSlotScript();

        assertEquals(2, contentService.getRegisteredBlocks().size(),
                "both crates must be registered");
    }

    @Test
    void keepsSlotTexture() throws IOException {
        runSlotScript();

        assertEquals(SLOT_TEXTURE, block(SLOT_CRATE).getSlotTexture(),
                "the slot texture must be kept");
    }

    @Test
    void keepsLockedSlots() throws IOException {
        runSlotScript();

        assertEquals(Set.of(0, 5, 11), block(SLOT_CRATE).getLockedSlots(),
                "locked slots must accumulate");
    }

    @Test
    void keepsSlotPosition() throws IOException {
        runSlotScript();

        assertArrayEquals(EXPECTED_SLOT_POS, block(SLOT_CRATE).getSlotPositions().get(23),
                "custom slot position must be stored per index");
    }

    @Test
    void keepsSlotPositionCount() throws IOException {
        runSlotScript();

        assertEquals(1, block(SLOT_CRATE).getSlotPositions().size(),
                "exactly one custom slot position must be stored");
    }

    @Test
    void unlockEmptiesLockedSet() throws IOException {
        runSlotScript();

        assertTrue(block("open_crate").getLockedSlots().isEmpty(),
                "locking then unlocking a slot must remove it from the locked set");
    }

    @Test
    void slotCrateRegistered() throws IOException {
        runSlotScript();

        assertDoesNotThrow(() -> block(SLOT_CRATE),
                "customized container must have been registered");
    }

    @Test
    void unlockCrateRegistered() throws IOException {
        runSlotScript();

        assertDoesNotThrow(() -> block("open_crate"),
                "unlock container must have been registered");
    }
}
