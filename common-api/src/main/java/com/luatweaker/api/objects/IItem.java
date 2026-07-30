package com.luatweaker.api.objects;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Represents a Minecraft item stack wrapper with item ID and count.")
public interface IItem {
    @LuaDoc(description = "Returns the registry ID of the item.", returnType = "string")
    String getId();

    @LuaDoc(description = "Returns the count of the item stack.", returnType = "integer")
    int getCount();

    @LuaDoc(description = "Checks if the item belongs to the specified tag.", params = {"tagId: string"}, returnType = "boolean")
    boolean hasTag(String tagId);

    Object getRawItemStack();
}
