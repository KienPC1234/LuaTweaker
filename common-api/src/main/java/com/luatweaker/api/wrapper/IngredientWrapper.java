package com.luatweaker.api.wrapper;

import com.luatweaker.api.annotation.LuaDoc;

import java.util.ArrayList;
import java.util.List;

@LuaDoc(description = "Represents an input ingredient (item ID, #tag, or oredict) in crafting recipes.")
public record IngredientWrapper(String descriptor, List<IngredientWrapper> alternatives) {

    public IngredientWrapper {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null");
        }
        if (alternatives == null) { alternatives = List.of(); }
    }

    public IngredientWrapper(String descriptor) {
        this(normalizeDescriptor(descriptor), List.of());
    }

    /**
     * Adds one alternative accepted by this ingredient slot.
     *
     * @param other alternative ingredient
     * @return a new ingredient listing every alternative, this one first
     */
    public IngredientWrapper orIngredient(IngredientWrapper other) {
        List<IngredientWrapper> list = new ArrayList<>(this.alternatives);
        list.add(other);
        return new IngredientWrapper(this.descriptor, list);
    }

    public IngredientWrapper orIngredient(String otherDescriptor) {
        return orIngredient(new IngredientWrapper(otherDescriptor));
    }

    public IngredientWrapper alt(IngredientWrapper other) {
        return orIngredient(other);
    }

    public IngredientWrapper alt(String otherDescriptor) {
        return orIngredient(otherDescriptor);
    }

    public static String normalizeDescriptor(String desc) {
        if (desc == null) { return ""; }
        String s = desc.trim();
        if (s.startsWith("oredict:")) {
            String tagPath = s.substring("oredict:".length());
            return tagPath.startsWith("#") ? tagPath : "#" + tagPath;
        }
        if (s.startsWith("tag:")) {
            String tagPath = s.substring("tag:".length());
            return tagPath.startsWith("#") ? tagPath : "#" + tagPath;
        }
        return s;
    }
}
