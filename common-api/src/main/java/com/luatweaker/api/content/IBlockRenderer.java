package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Low-level dynamic rendering context for custom block entities (BER).")
public interface IBlockRenderer {
    @LuaDoc(description = "Pushes the current transformation matrix.", params = {}, returnType = "void")
    void pushPose();

    @LuaDoc(description = "Pops the current transformation matrix.", params = {}, returnType = "void")
    void popPose();

    @LuaDoc(description = "Translates the render origin.", params = {"x: number", "y: number", "z: number"}, returnType = "void")
    void translate(double x, double y, double z);

    @LuaDoc(description = "Rotates the model around an axis (in degrees).", params = {"degrees: number", "axisX: number", "axisY: number", "axisZ: number"}, returnType = "void")
    void rotate(float degrees, float axisX, float axisY, float axisZ);

    @LuaDoc(description = "Scales the model along X, Y, Z axes.", params = {"x: number", "y: number", "z: number"}, returnType = "void")
    void scale(float x, float y, float z);

    @LuaDoc(description = "Renders an item stack floating/positioned at the current matrix.", params = {"itemId: string", "count: integer", "light?: integer"}, returnType = "void")
    void renderItem(String itemId, int count, int light);

    @LuaDoc(description = "Renders the item stack currently stored in the block container slot.", params = {"slotIndex: integer", "light?: integer"}, returnType = "void")
    void renderItemFromSlot(int slotIndex, int light);

    @LuaDoc(description = "Renders another block at the current transformation matrix.", params = {"blockId: string", "light?: integer"}, returnType = "void")
    void renderBlock(String blockId, int light);

    @LuaDoc(description = "Gets the packed light at the block position.", params = {}, returnType = "integer")
    int getLight();

    @LuaDoc(description = "Gets the game client tick/animation time in ticks.", params = {}, returnType = "number")
    double getTime();
}
