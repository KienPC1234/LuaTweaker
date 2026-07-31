package com.luatweaker.api.objects;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;

@LuaDoc(description = "Represents a block in the world with its server coordinates and block state.")
public interface IWorldBlock {
    @LuaDoc(description = "Returns the block type registry ID (e.g. 'minecraft:stone').", returnType = "string")
    @NotNull
    String getId();

    @LuaDoc(description = "Returns the X coordinate of the block.", returnType = "integer")
    int getX();

    @LuaDoc(description = "Returns the Y coordinate of the block.", returnType = "integer")
    int getY();

    @LuaDoc(description = "Returns the Z coordinate of the block.", returnType = "integer")
    int getZ();

    @LuaDoc(description = "Returns the dimension ID (e.g. 'minecraft:overworld').", returnType = "string")
    @NotNull
    String getDimension();

    @NotNull
    Object getRawBlockState();
}
