package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IBlockBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** :ManualSlots() opts a machine GUI into explicit-only slots. */
class ContainerManualSlotsTest extends ContentFixture {

    private static final String MANUAL_CHUNK = "manual_chunk";
    private static final String MANUAL_SCRIPT = """
            local Content = require("LuaTweaker.Content")
            local machine = Content.NewBlock("manual_machine")
                :Container(3, 9, "spill")
                :ManualSlots()
                :SlotPosition(0, 44, 30)
                :SlotPosition(1, 116, 30)
                :Register()
            local chest = Content.NewBlock("plain_chest")
                :Container(3, 9, "spill")
                :SlotPosition(0, 44, 30)
                :Register()
        """;

    private IBlockBuilder manualMachine() throws IOException {
        engine.executeScript(scriptFile(MANUAL_SCRIPT), MANUAL_CHUNK);
        return block("manual_machine");
    }

    private IBlockBuilder plainChest() throws IOException {
        engine.executeScript(scriptFile(MANUAL_SCRIPT), MANUAL_CHUNK);
        return block("plain_chest");
    }

    @Test
    void manualFlagIsKept() throws IOException {
        assertTrue(manualMachine().isManualSlots(),
                "ManualSlots must survive the builder");
    }

    @Test
    void gridStaysDefault() throws IOException {
        assertFalse(plainChest().isManualSlots(),
                "containers without the flag must keep the automatic grid");
    }

    @Test
    void positionsStillStored() throws IOException {
        assertEquals(2, manualMachine().getSlotPositions().size(),
                "explicit positions must be stored alongside the flag");
    }
}
