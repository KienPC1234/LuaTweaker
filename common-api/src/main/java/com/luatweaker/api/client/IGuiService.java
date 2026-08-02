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

    @LuaDoc(
        description = "Draws text horizontally centered around the given x position. Can only be used during GuiService.OnRenderHUD.",
        params = {"text: string", "centerX: number", "y: number", "color: number", "dropShadow: boolean"},
        returnType = "void"
    )
    void drawTextCentered(String text, int centerX, int y, int color, boolean dropShadow);

    @LuaDoc(
        description = "Draws a 1-pixel colored border rectangle. Can only be used during GuiService.OnRenderHUD.",
        params = {"x: number", "y: number", "width: number", "height: number", "color: number"},
        returnType = "void"
    )
    void drawOutline(int x, int y, int width, int height, int color);

    @LuaDoc(
        description = "Draws a progress bar with a background track and a filled portion. Can only be used during GuiService.OnRenderHUD.",
        params = {"x: number", "y: number", "width: number", "height: number", "fillPercent: number",
                  "backgroundColor: number", "fillColor: number"},
        returnType = "void"
    )
    void drawProgressBar(int x, int y, int width, int height, double fillPercent, int backgroundColor, int fillColor);

    @LuaDoc(
        description = "Returns the current GUI scaled screen dimensions as a table {Width, Height}.",
        params = {},
        returnType = "table"
    )
    int[] getScreenSize();

    @LuaDoc(
        description = "Draws a full texture from the Minecraft texture registry. Can only be used during GuiService.OnRenderHUD.",
        params = {"textureId: string (resource location, e.g. 'minecraft:textures/gui/sprites/hud/experience_bar_background.png')",
                  "x: number", "y: number", "width: number", "height: number"},
        returnType = "void"
    )
    void drawTexture(String textureId, int x, int y, int width, int height);
}
