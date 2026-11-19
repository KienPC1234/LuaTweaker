package com.luatweaker.platform.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Custom screen driven by a Lua callback table.
 *
 * <p>Callbacks (all optional, first argument is always the screen table):
 * {@code OnRender(mouseX, mouseY, partialTick)}, {@code OnMouseClick(mouseX, mouseY, button)},
 * {@code OnMouseRelease(mouseX, mouseY, button)}, {@code OnMouseMove(mouseX, mouseY, button,
 * dragX, dragY)} (button is -1 while merely hovering), {@code OnScroll(mouseX, mouseY,
 * scrollX, scrollY)}, {@code OnKeyDown/OnKeyUp(keyCode, scanCode, modifiers)},
 * {@code OnCharTyped(codePoint, modifiers)}, {@code OnClose()}. Boolean callbacks consume the
 * event by returning true.</p>
 */
public class LuaCanvasScreen extends Screen {
    private final String screenId;
    private final ILuaEngine engine;
    private final ILuaTable screenTable;
    private final boolean pauseGame;

    public LuaCanvasScreen(@NotNull String screenId, @NotNull Component title,
                           @Nullable ILuaEngine engine, @Nullable ILuaTable screenTable,
                           boolean pauseGame) {
        super(title);
        this.screenId = screenId;
        this.engine = engine;
        this.screenTable = screenTable;
        this.pauseGame = pauseGame;
    }

    public String getScreenId() {
        return screenId;
    }

    @Override
    public boolean isPauseScreen() {
        return pauseGame;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (engine == null || screenTable == null) {
            return;
        }
        NeoForgeGuiService.setGraphics(graphics);
        try {
            callNumbers("OnRender", mouseX, mouseY, partialTick);
        } finally {
            NeoForgeGuiService.clearGraphics();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (callNumbers("OnMouseClick", mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (callNumbers("OnMouseRelease", mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        callNumbers("OnMouseMove", mouseX, mouseY, -1, 0, 0);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (callNumbers("OnMouseMove", mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (callNumbers("OnScroll", mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (callNumbers("OnKeyDown", keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (callNumbers("OnKeyUp", keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (callNumbers("OnCharTyped", codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        callCallback("OnClose");
        super.onClose();
    }

    private boolean callNumbers(@NotNull String callbackName, double... values) {
        if (engine == null || screenTable == null) {
            return false;
        }
        ILuaValue[] args = new ILuaValue[values.length];
        for (int i = 0; i < values.length; i++) {
            args[i] = engine.wrapNumber(values[i]);
        }
        return callCallback(callbackName, args);
    }

    /**
     * Invokes one Lua callback. Never throws into the render/input loop: a broken script
     * must not crash the game, but it is reported with the screen id and callback name.
     */
    private boolean callCallback(@NotNull String callbackName, @NotNull ILuaValue... args) {
        if (engine == null || screenTable == null) {
            return false;
        }
        ILuaValue callback = screenTable.rawget(callbackName);
        if (callback == null || !callback.isFunction()) {
            return false;
        }
        try {
            ILuaValue[] callArgs = new ILuaValue[args.length + 1];
            callArgs[0] = screenTable;
            System.arraycopy(args, 0, callArgs, 1, args.length);
            ILuaValue result = engine.callFunction(callback, callArgs);
            return result != null && result.isBoolean() && result.asBoolean();
        } catch (Throwable t) {
            // Render/input callbacks run inside Minecraft's frame loop; letting an error
            // escape would crash the client instead of failing one script.
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "CanvasScreen '" + screenId + "' " + callbackName + " error: " + t.getMessage());
            return false;
        }
    }
}
