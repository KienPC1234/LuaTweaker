package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Builder interface for creating custom Creative Mode Tabs in Minecraft.")
public interface ICreativeTabBuilder {
    @LuaDoc(description = "Sets the display title of the creative tab.", params = {"title: string"}, returnType = "ICreativeTabBuilder")
    ICreativeTabBuilder title(String title);

    @LuaDoc(description = "Sets the icon item for the tab (e.g. 'luatweaker:custom_ruby' or 'luatweaker:magic_staff').", params = {"itemId: string"}, returnType = "ICreativeTabBuilder")
    ICreativeTabBuilder icon(String itemId);

    String getId();
    String getTitle();
    String getIconItem();
}
