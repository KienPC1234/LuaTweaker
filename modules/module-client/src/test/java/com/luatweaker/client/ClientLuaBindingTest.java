package com.luatweaker.client;

import com.luatweaker.core.vm.LuaEngineFactory;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.vm.ILuaEngine;
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
        public int[] measureText(String text) {
            calls.add("measure " + text);
            // Deterministic fake: 6px per character, 9px line height.
            return new int[] { text == null ? 0 : text.length() * 6, 9 };
        }

        @Override
        public void drawTexture(String textureId, int x, int y, int width, int height) {
            calls.add("texture " + textureId);
        }

        @Override
        public void pushClip(int x, int y, int width, int height) {
            calls.add("clipPush " + x + "," + y + " " + width + "x" + height);
        }

        @Override
        public void popClip() {
            calls.add("clipPop");
        }

        @Override
        public void drawLine(int x1, int y1, int x2, int y2, int color, int thickness) {
            calls.add("line " + x1 + "," + y1 + " -> " + x2 + "," + y2 + " t" + thickness);
        }

        @Override
        public void drawItem(String itemId, int x, int y, int count) {
            calls.add("item " + itemId + "x" + count + " @" + x + "," + y);
        }

        @Override
        public void drawGradient(int x, int y, int width, int height, int topColor, int bottomColor) {
            calls.add("gradient " + x + "," + y + " " + width + "x" + height);
        }

        @Override
        public void drawSprite(String spriteId, int x, int y, int width, int height) {
            calls.add("sprite " + spriteId);
        }

        @Override
        public void drawTooltip(String text, int x, int y) {
            calls.add("tooltip " + text);
        }

        @Override
        public void pushPose() {
            calls.add("posePush");
        }

        @Override
        public void popPose() {
            calls.add("posePop");
        }

        @Override
        public void translate(double x, double y, double z) {
            calls.add("translate " + x + "," + y + "," + z);
        }

        @Override
        public void scale(double x, double y, double z) {
            calls.add("scale " + x + "," + y + "," + z);
        }

        @Override
        public void registerScreen(String screenId, com.luatweaker.api.vm.ILuaTable screenTable) {
            calls.add("register " + screenId);
        }

        @Override
        public void openScreen(String screenId, String title, boolean pauseGame) {
            calls.add("open " + screenId + " (" + title + ")");
        }

        @Override
        public void closeScreen() {
            calls.add("close");
        }

        @Override
        public boolean isScreenOpen() {
            return calls.contains("open");
        }
    }

    @Test
    public void guiServiceExpandedBindingsDispatchToImplementation() {
        ILuaEngine engine = LuaEngineFactory.create(false);
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
        ILuaEngine engine = LuaEngineFactory.create(false);
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

    @Test
    public void clientServicesUseOneCanonicalBindingPerService() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl());
        engine.getAndClearLastExecutionError();

        engine.executeString(
            "assert(Camera ~= nil, 'Camera must be bound')\n" +
            "assert(type(Camera.Shake) == 'function', 'Camera.Shake must be available')\n" +
            "assert(ClientEffects ~= nil, 'ClientEffects must be bound')\n" +
            "assert(type(ClientEffects.PlaySound) == 'function', 'ClientEffects.PlaySound must be available')\n" +
            "assert(TweenService ~= nil, 'TweenService must be bound')\n" +
            "assert(type(TweenService.Create) == 'function', 'TweenService.Create must be available')\n" +
            "assert(CameraService == nil, 'undocumented CameraService alias must be removed')\n" +
            "assert(ClientEffectsService == nil, 'undocumented ClientEffectsService alias must be removed')\n" +
            "assert(Mod:GetService('Camera') == Camera, 'Camera global and service must be one table')\n" +
            "assert(Mod:GetService('ClientEffects') == ClientEffects, 'ClientEffects global and service must be one table')\n" +
            "assert(Mod:GetService('TweenService') == TweenService, 'TweenService global and service must be one table')\n",
            "ClientSingleBindingTest"
        );
        String bindingError = engine.getAndClearLastExecutionError();
        assertNull(bindingError, "Lua assertions in ClientSingleBindingTest must pass: " + bindingError);
    }

    @Test
    public void canonicalTweenServiceStillTicksTweens() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl());
        engine.getAndClearLastExecutionError();

        engine.executeString(
            "tweenInstance = { Value = 0 }\n" +
            "tweenHandle = TweenService:Create(tweenInstance, { Duration = 1.0 }, { Value = 10.0 })\n" +
            "assert(type(tweenHandle.Play) == 'function', 'Play must exist')\n" +
            "assert(type(tweenHandle.Pause) == 'function', 'Pause must exist')\n" +
            "assert(type(tweenHandle.Cancel) == 'function', 'Cancel must exist')\n" +
            "tweenHandle:Play()\n",
            "TweenCreateTest"
        );
        String createError = engine.getAndClearLastExecutionError();
        assertNull(createError, "Lua assertions in TweenCreateTest must pass: " + createError);

        TweenServiceImpl.tickAll(0.5);

        engine.executeString(
            "assert(math.abs(tweenInstance.Value - 5.0) < 1e-6,\n" +
            "    'tween must interpolate to 5.0, got ' .. tostring(tweenInstance.Value))\n",
            "TweenTickTest"
        );
        String tickError = engine.getAndClearLastExecutionError();
        assertNull(tickError, "Lua assertions in TweenTickTest must pass: " + tickError);
    }

    @Test
    public void guiPrimitivesDispatchToTheImplementation() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockGuiService gui = new MockGuiService();
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl(), new KeyBindServiceImpl(), gui);

        engine.executeStringOrThrow(
            "GuiService:PushClip(10, 20, 100, 50)\n" +
            "GuiService:DrawLine(0, 0, 50, 25, 0xFFFFFFFF)\n" +
            "GuiService:DrawLine(0, 0, 50, 25, 0xFFFFFFFF, 3)\n" +
            "GuiService:DrawItem('minecraft:diamond', 8, 8)\n" +
            "GuiService:DrawItem('minecraft:diamond', 8, 8, 5)\n" +
            "GuiService:PopClip()",
            "gui_primitives");

        assertTrue(gui.calls.contains("clipPush 10,20 100x50"), "PushClip: " + gui.calls);
        assertTrue(gui.calls.contains("line 0,0 -> 50,25 t1"), "DrawLine default thickness: " + gui.calls);
        assertTrue(gui.calls.contains("line 0,0 -> 50,25 t3"), "DrawLine explicit thickness: " + gui.calls);
        assertTrue(gui.calls.contains("item minecraft:diamondx1 @8,8"), "DrawItem default count: " + gui.calls);
        assertTrue(gui.calls.contains("item minecraft:diamondx5 @8,8"), "DrawItem with count: " + gui.calls);
        assertTrue(gui.calls.contains("clipPop"), "PopClip: " + gui.calls);
    }

    @Test
    public void guiGradientSpriteTooltipAndPoseDispatch() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockGuiService gui = new MockGuiService();
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl(), new KeyBindServiceImpl(), gui);

        engine.executeStringOrThrow(
            "GuiService:PushPose()\n" +
            "GuiService:Translate(10, 20)\n" +
            "GuiService:Scale(2, 2, 1)\n" +
            "GuiService:DrawGradient(0, 0, 100, 10, 0xFF00FF00, 0xFFFF0000)\n" +
            "GuiService:DrawSprite('minecraft:hud/experience_bar_background', 0, 0, 16, 16)\n" +
            "GuiService:DrawTooltip('Ruby', 5, 5)\n" +
            "GuiService:PopPose()",
            "gui_new_primitives");

        assertTrue(gui.calls.contains("posePush"), "PushPose: " + gui.calls);
        assertTrue(gui.calls.contains("translate 10.0,20.0,0.0"), "Translate default z: " + gui.calls);
        assertTrue(gui.calls.contains("scale 2.0,2.0,1.0"), "Scale default z: " + gui.calls);
        assertTrue(gui.calls.contains("gradient 0,0 100x10"), "DrawGradient: " + gui.calls);
        assertTrue(gui.calls.contains("sprite minecraft:hud/experience_bar_background"), "DrawSprite: " + gui.calls);
        assertTrue(gui.calls.contains("tooltip Ruby"), "DrawTooltip: " + gui.calls);
        assertTrue(gui.calls.contains("posePop"), "PopPose: " + gui.calls);
    }

    @Test
    public void rebindingWithoutGuiServiceKeepsTheWorkingTable() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockGuiService gui = new MockGuiService();
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl(), new KeyBindServiceImpl(), gui);

        // This is the shape that used to wipe GuiService on FMLClientSetup: a second
        // registration without an IGuiService. The existing table must survive.
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl());

        engine.executeStringOrThrow("GuiService:DrawRect(1, 2, 3, 4, 0xFF000000)", "gui_survives_rebind");
        assertTrue(gui.calls.contains("rect 1,2 3x4"),
                "a re-registration without a GUI implementation must not replace GuiService: " + gui.calls);
    }

    @Test
    public void measureTextReturnsWidthAndHeight() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockGuiService gui = new MockGuiService();
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl(), new KeyBindServiceImpl(), gui);

        engine.executeStringOrThrow(
            "local size = GuiService:MeasureText('Mana')\n" +
            "assert(size ~= nil, 'MeasureText must return a table')\n" +
            "assert(size.Width == 24 and size.Height == 9, 'Width/Height mismatch')\n" +
            "assert(size[1] == 24 and size[2] == 9, 'MeasureText must be indexable by 1,2')\n" +
            "assert(GuiService:measureText('Mana').Width == 24, 'lowercase alias must exist')",
            "MeasureTextTest"
        );
        assertTrue(gui.calls.contains("measure Mana"), "MeasureText must reach the implementation: " + gui.calls);
    }

    @Test
    public void runServiceRoleFollowsThePlatform() {
        try {
            ILuaEngine engine = LuaEngineFactory.create(false);
            com.luatweaker.api.pal.Platform.setContent(new FakeContent(false, true, true));
            ClientLuaBinding.registerBindings(engine, new ClientServiceImpl());

            engine.executeStringOrThrow(
                "assert(RunService:IsClient() == true, 'a client process must report IsClient')\n" +
                "assert(RunService:IsServer() == true, 'single-player hosts an integrated server')",
                "RunServiceSpTest");

            com.luatweaker.api.pal.Platform.setContent(new FakeContent(false, true, false));
            engine.executeStringOrThrow(
                "assert(RunService:IsClient() == true)\n" +
                "assert(RunService:IsServer() == false, 'a remote client hosts no server')",
                "RunServiceRemoteTest");

            com.luatweaker.api.pal.Platform.setContent(new FakeContent(true, false, false));
            engine.executeStringOrThrow(
                "assert(RunService:IsClient() == false, 'dedicated servers are not clients')\n" +
                "assert(RunService:IsServer() == true)",
                "RunServiceDedicatedTest");
        } finally {
            // Platform is global static state shared by the whole test JVM; leaving a dedicated
            // fake behind would push every later client test onto the dummy GUI branch.
            com.luatweaker.api.pal.Platform.setContent(null);
        }
    }

    /** Minimal PAL content double: dedicated/client/local-server flags only. */
    private record FakeContent(boolean dedicated, boolean client, boolean localServer)
            implements com.luatweaker.api.pal.IPlatformContent {
        @Override
        public com.luatweaker.api.objects.IItem createItem(String itemId, int count) {
            return null;
        }

        @Override
        public boolean itemExists(String itemId) {
            return false;
        }

        @Override
        public boolean blockExists(String blockId) {
            return false;
        }

        @Override
        public boolean fluidExists(String fluidId) {
            return false;
        }

        @Override
        public boolean tagExists(String tagId) {
            return false;
        }

        @Override
        public boolean isModLoaded(String modId) {
            return false;
        }

        @Override
        public boolean isClient() {
            return client;
        }

        @Override
        public boolean isDedicatedServer() {
            return dedicated;
        }

        @Override
        public boolean hasLocalServer() {
            return localServer;
        }

        @Override
        public String getPlatformName() {
            return "Fake";
        }

        @Override
        public java.util.Set<String> getSupportedMobParents() {
            return java.util.Set.of();
        }

        @Override
        public java.util.List<com.luatweaker.api.objects.IRecipe> getAllRecipes() {
            return java.util.List.of();
        }
    }

    @Test
    public void testCanvasScreenBindings() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockGuiService gui = new MockGuiService();
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl(), new KeyBindServiceImpl(), gui);

        engine.executeString(
            "local screen = GuiService:CreateScreen('spell_book', 'Spell Book')\n" +
            "assert(type(screen) == 'table', 'screen must be a table')\n" +
            "assert(screen.Id == 'spell_book', 'screen.Id mismatch')\n" +
            "screen:Open()\n" +
            "screen:Close()\n",
            "ScreenTest"
        );
        String err = engine.getAndClearLastExecutionError();
        assertNull(err, "ScreenTest must succeed: " + err);

        assertTrue(gui.calls.contains("register spell_book"), "MockGuiService should have received register: " + gui.calls);
        assertTrue(gui.calls.contains("open spell_book (Spell Book)"), "MockGuiService should have received open: " + gui.calls);
        assertTrue(gui.calls.contains("close"), "MockGuiService should have received close: " + gui.calls);
    }
}
