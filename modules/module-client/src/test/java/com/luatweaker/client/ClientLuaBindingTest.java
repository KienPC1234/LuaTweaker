package com.luatweaker.client;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClientLuaBindingTest {

    private static class MockGuiService implements IGuiService {
        final List<String> calls = new ArrayList<>();
        final int[] screenSize = new int[] { 1920, 1080 };

        @Override
        public void drawRect(int x, int y, int width, int height, int color) {
            calls.add("rect " + x + "," + y + " " + width + "x" + height);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean dropShadow) {
            calls.add("text " + text);
        }

        @Override
        public void drawTextCentered(String text, int centerX, int y, int color, boolean dropShadow) {
            calls.add("textCentered " + text + " @" + centerX + "," + y);
        }

        @Override
        public void drawOutline(int x, int y, int width, int height, int color) {
            calls.add("outline " + x + "," + y + " " + width + "x" + height);
        }

        @Override
        public void drawProgressBar(int x, int y, int width, int height, double fillPercent, int backgroundColor, int fillColor) {
            calls.add("progress " + fillPercent);
        }

        @Override
        public int[] getScreenSize() {
            return screenSize;
        }

        @Override
        public void drawTexture(String textureId, int x, int y, int width, int height) {
            calls.add("texture " + textureId);
        }
    }

    @Test
    public void guiServiceExpandedBindingsDispatchToImplementation() {
        ILuaEngine engine = new CobaltLuaEngine();
        MockGuiService gui = new MockGuiService();
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl(), new KeyBindServiceImpl(), gui);

        engine.executeString(
            "local size = GuiService:GetScreenSize()\n" +
            "assert(size.Width == 1920 and size.Height == 1080, 'GetScreenSize must return the screen table')\n" +
            "assert(size[1] == 1920 and size[2] == 1080, 'GetScreenSize must be indexable by 1,2')\n" +
            "GuiService:DrawTextCentered('Mana', 100, 50, 0xFFFFFFFF, true)\n" +
            "GuiService:DrawOutline(10, 10, 120, 40, 0xFF38BDF8)\n" +
            "GuiService:DrawProgressBar(0, 0, 100, 10, 0.5, 0xFF1E293B, 0xFF0284C7)\n" +
            "GuiService:DrawTexture('minecraft:textures/gui/foo.png', 1, 2, 16, 16)\n" +
            "GuiService:DrawRect(5, 5, 10, 10, 0xFF000000)\n" +
            "GuiService:DrawText('hello', 0, 0, 0xFFFFFFFF, false)\n",
            "GuiBindingTest"
        );

        assertTrue(gui.calls.contains("textCentered Mana @100,50"), "DrawTextCentered not dispatched: " + gui.calls);
        assertTrue(gui.calls.contains("outline 10,10 120x40"), "DrawOutline not dispatched: " + gui.calls);
        assertTrue(gui.calls.contains("progress 0.5"), "DrawProgressBar not dispatched: " + gui.calls);
        assertTrue(gui.calls.contains("texture minecraft:textures/gui/foo.png"), "DrawTexture not dispatched: " + gui.calls);
        assertTrue(gui.calls.contains("rect 5,5 10x10"), "DrawRect not dispatched: " + gui.calls);
        assertTrue(gui.calls.contains("text hello"), "DrawText not dispatched: " + gui.calls);
    }

    @Test
    public void clientTableKeepsOnKeyBindPressedSignalAfterBindingRegistration() {
        ILuaEngine engine = new CobaltLuaEngine();
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl());

        engine.executeString(
            "assert(Client.OnKeyBindPressed ~= nil, 'Client.OnKeyBindPressed must survive Java binding registration')\n" +
            "local capturedId = nil\n" +
            "local capturedPayload = nil\n" +
            "Client.OnKeyBindPressed:Connect(function(keyBindId, payload)\n" +
            "    capturedId = keyBindId\n" +
            "    capturedPayload = payload\n" +
            "end)\n" +
            "Client.OnKeyBindPressed:Fire('magic_staff_cast', 'StaffCastSkill')\n" +
            "require('LuaTweaker.Task')._tick()\n" +
            "assert(capturedId == 'magic_staff_cast', 'listener must receive the keybind id, got: ' .. tostring(capturedId))\n" +
            "assert(capturedPayload == 'StaffCastSkill', 'listener must receive the payload, got: ' .. tostring(capturedPayload))",
            "KeyBindSignalTest"
        );
    }
}
