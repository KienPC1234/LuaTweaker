package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the canvas-screen lifecycle ({@code CreateScreen/Open/Close}) and the re-registration rule:
 * a binding pass without a GUI implementation must not wipe the working {@code GuiService} table.
 */
class GuiScreenBindingTest extends GuiTestFixture {

    @Test
    void canvasScreenRegistersOpensAndCloses() {
        engine.executeString("""
                local screen = GuiService:CreateScreen('spell_book', 'Spell Book')
                assert(type(screen) == 'table', 'screen must be a table')
                assert(screen.Id == 'spell_book', 'screen.Id mismatch')
                screen:Open()
                screen:Close()
                """, "ScreenTest");
        String error = engine.getAndClearLastExecutionError();
        assertEquals(List.of(true, true, true, true),
                List.of(error == null,
                        gui.calls.contains("register spell_book"),
                        gui.calls.contains("open spell_book (Spell Book)"),
                        gui.calls.contains("close")),
                "CreateScreen/Open/Close must register and drive the canvas screen: " + gui.calls);
    }

    @Test
    void rebindingWithoutGuiServiceKeepsTheWorkingTable() {
        // This is the shape that used to wipe GuiService on FMLClientSetup: a second
        // registration without an IGuiService. The existing table must survive.
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl());
        run("GuiService:DrawRect(1, 2, 3, 4, 0xFF000000)", "gui_survives_rebind");
        assertTrue(gui.calls.contains("rect 1,2 3x4"),
                "a re-registration without a GUI implementation must not replace GuiService: "
                        + gui.calls);
    }
}
