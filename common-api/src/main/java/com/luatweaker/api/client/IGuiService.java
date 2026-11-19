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

    /**
     * Measures rendered text with the game font.
     *
     * @param text text that would be drawn (no newlines)
     * @return {@code {width, height}} in GUI pixels
     */
    @LuaDoc(
        description = "Measures rendered text: returns {width, height} ({1=width, 2=height, Width, Height}) so HUD layouts do not hardcode text widths.",
        params = {"text: string"},
        returnType = "table"
    )
    int[] measureText(String text);

    @LuaDoc(
        description = "Draws a full texture from the Minecraft texture registry. Can only be used during GuiService.OnRenderHUD.",
        params = {"textureId: string (resource location, e.g. 'minecraft:textures/gui/sprites/hud/experience_bar_background.png')",
                  "x: number", "y: number", "width: number", "height: number"},
        returnType = "void"
    )
    void drawTexture(String textureId, int x, int y, int width, int height);

    @LuaDoc(
        description = "Restricts drawing to a rectangle until popClip(). Calls nest; an unclosed clip is reset at the end of the frame.",
        params = {"x: integer", "y: integer", "width: integer", "height: integer"},
        returnType = "void"
    )
    void pushClip(int x, int y, int width, int height);

    @LuaDoc(
        description = "Removes the innermost pushClip() rectangle.",
        params = {},
        returnType = "void"
    )
    void popClip();

    @LuaDoc(
        description = "Draws a line between two points (any angle) with the given thickness in GUI pixels.",
        params = {"x1: integer", "y1: integer", "x2: integer", "y2: integer",
                  "color: integer", "[thickness: integer]"},
        returnType = "void"
    )
    void drawLine(int x1, int y1, int x2, int y2, int color, @com.luatweaker.api.annotation.LuaDefault("1") int thickness);

    @LuaDoc(
        description = "Draws an item icon (with stack count and durability decorations) at GUI coordinates.",
        params = {"itemId: string", "x: integer", "y: integer", "[count: integer]"},
        returnType = "void"
    )
    void drawItem(String itemId, int x, int y, @com.luatweaker.api.annotation.LuaDefault("1") int count);

    @LuaDoc(
        description = "Fills a rectangle with a vertical gradient from topColor to bottomColor.",
        params = {"x: integer", "y: integer", "width: integer", "height: integer",
                  "topColor: integer", "bottomColor: integer"},
        returnType = "void"
    )
    void drawGradient(int x, int y, int width, int height, int topColor, int bottomColor);

    @LuaDoc(
        description = "Draws a GUI sprite from the vanilla sprite atlas (e.g. 'minecraft:hud/experience_bar_background').",
        params = {"spriteId: string", "x: integer", "y: integer", "width: integer", "height: integer"},
        returnType = "void"
    )
    void drawSprite(String spriteId, int x, int y, int width, int height);

    @LuaDoc(
        description = "Draws a hover tooltip box with plain text at GUI coordinates.",
        params = {"text: string", "x: integer", "y: integer"},
        returnType = "void"
    )
    void drawTooltip(String text, int x, int y);

    @LuaDoc(
        description = "Pushes a matrix onto the HUD pose stack. Calls nest; an unclosed pushPose is reset at the end of the frame.",
        params = {},
        returnType = "void"
    )
    void pushPose();

    @LuaDoc(
        description = "Pops the matrix pushed by pushPose().",
        params = {},
        returnType = "void"
    )
    void popPose();

    @LuaDoc(
        description = "Translates subsequent HUD drawing by (x, y, z) GUI pixels.",
        params = {"x: number", "y: number", "z: number (optional, default 0)"},
        returnType = "void"
    )
    void translate(double x, double y, @com.luatweaker.api.annotation.LuaDefault("0") double z);

    @LuaDoc(
        description = "Scales subsequent HUD drawing around the current origin.",
        params = {"x: number", "y: number", "z: number (optional, default 1)"},
        returnType = "void"
    )
    void scale(double x, double y, @com.luatweaker.api.annotation.LuaDefault("1") double z);

    @LuaDoc(
        description = "Registers a screen definition table with lifecycle callbacks (OnRender, OnMouseClick, OnMouseRelease, OnMouseMove, OnScroll, OnKeyDown, OnClose).",
        params = {"screenId: string", "screenTable: table"},
        returnType = "void"
    )
    default void registerScreen(String screenId, @org.jetbrains.annotations.Nullable com.luatweaker.api.vm.ILuaTable screenTable) {}

    @LuaDoc(
        description = "Opens a custom interactive canvas screen with mouse cursor.",
        params = {"screenId: string", "[title: string]", "[pauseGame: boolean]"},
        returnType = "void"
    )
    default void openScreen(String screenId, @com.luatweaker.api.annotation.LuaDefault("") String title, @com.luatweaker.api.annotation.LuaDefault("false") boolean pauseGame) {}

    @LuaDoc(
        description = "Closes any currently active custom canvas screen.",
        params = {},
        returnType = "void"
    )
    default void closeScreen() {}

    @LuaDoc(
        description = "Returns true if a custom canvas screen is currently open.",
        params = {},
        returnType = "boolean"
    )
    default boolean isScreenOpen() { return false; }
}
