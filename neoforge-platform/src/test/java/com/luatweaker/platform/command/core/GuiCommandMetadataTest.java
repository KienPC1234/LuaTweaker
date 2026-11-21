package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.CoreCommandNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiCommandMetadataTest {

    @Test
    void metadataNameIsGui() {
        assertEquals("gui", new GuiCommand().getName(),
                "the GUI editor command must be named gui");
    }

    @Test
    void metadataIsNotConsoleAllowed() {
        assertFalse(new GuiCommand().isConsoleAllowed(),
                "Console cannot open GUI Editor");
    }

    @Test
    void metadataHasDescription() {
        assertNotNull(new GuiCommand().getDescription(),
                "the GUI editor command must document itself");
    }

    @Test
    void metadataDescriptionIsNotBlank() {
        assertFalse(new GuiCommand().getDescription().isBlank(),
                "the GUI editor description must not be blank");
    }

    @Test
    void metadataIsReservedCoreCommand() {
        assertTrue(CoreCommandNames.RESERVED.contains("gui"),
                "gui must be a reserved core command");
    }
}
