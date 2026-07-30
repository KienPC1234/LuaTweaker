package com.luatweaker.api.wrapper;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Represents an input ingredient (item ID or #tag) in crafting recipes.")
public record IngredientWrapper(String descriptor) {
    public IngredientWrapper {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null");
        }
    }
}
