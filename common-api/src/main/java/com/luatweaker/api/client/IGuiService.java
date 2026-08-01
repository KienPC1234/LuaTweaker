package com.luatweaker.api.client;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Service for drawing on the client HUD.")
public interface IGuiService {
    @LuaDoc(
        description = "Draws a colored rectangle on the screen. Can only be used during GuiService.OnRenderHUD.",
        params = {"x: number", "y: number", "width: number", "height: number", "color: number"},
        returnType = "void"
    )
    void drawRect(int x, int y, int width, int height, int color);

    @LuaDoc(
        description = "Draws text on the screen. Can only be used during GuiService.OnRenderHUD.",
        params = {"text: string", "x: number", "y: number", "color: number", "dropShadow: boolean"},
        returnType = "void"
    )
    void drawText(String text, int x, int y, int color, boolean dropShadow);
}
