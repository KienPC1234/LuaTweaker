package com.luatweaker.content;

import com.luatweaker.api.content.ICreativeTabBuilder;

public class CreativeTabBuilderImpl implements ICreativeTabBuilder {
    private final String id;
    private String title;
    private String iconItem;

    public CreativeTabBuilderImpl(String id) {
        this.id = id;
    }

    @Override
    public ICreativeTabBuilder title(String title) {
        this.title = title;
        return this;
    }

    @Override
    public ICreativeTabBuilder icon(String itemId) {
        this.iconItem = itemId;
        return this;
    }

    @Override public String getId() { return id; }
    @Override public String getTitle() { return title; }
    @Override public String getIconItem() { return iconItem; }
}
