package com.luatweaker.api.objects;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@LuaDoc(description = "Represents an item stack with an address/slot location in an inventory.")
public interface ILocatedItem {
    @LuaDoc(description = "Returns the item registry ID (e.g. 'minecraft:diamond').", returnType = "string")
    @NotNull
    String getId();

    @LuaDoc(description = "Returns the count of the item stack.", returnType = "integer")
    int getCount();

    @LuaDoc(description = "Returns the slot index of the item stack.", returnType = "integer")
    int getSlot();

    @LuaDoc(description = "Returns the owner entity's UUID, or null if stored in a block container.", returnType = "string")
    @Nullable
    String getOwnerUuid();

    @LuaDoc(description = "Returns the block X coordinate if stored in a container, or null.", returnType = "number")
    @Nullable
    Integer getBlockX();

    @LuaDoc(description = "Returns the block Y coordinate if stored in a container, or null.", returnType = "number")
    @Nullable
    Integer getBlockY();

    @LuaDoc(description = "Returns the block Z coordinate if stored in a container, or null.", returnType = "number")
    @Nullable
    Integer getBlockZ();

    @NotNull
    Object getRawItemStack();
}
