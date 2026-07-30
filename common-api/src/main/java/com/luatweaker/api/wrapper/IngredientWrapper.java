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
        if (alternatives == null) alternatives = List.of();
    }

    public IngredientWrapper(String descriptor) {
        this(normalizeDescriptor(descriptor), List.of());
    }

    public IngredientWrapper or(IngredientWrapper other) {
        List<IngredientWrapper> list = new ArrayList<>(this.alternatives);
        list.add(other);
        return new IngredientWrapper(this.descriptor, list);
    }

    public IngredientWrapper or(String otherDescriptor) {
        return or(new IngredientWrapper(otherDescriptor));
    }

    public IngredientWrapper orIngredient(IngredientWrapper other) {
        return or(other);
    }

    public IngredientWrapper orIngredient(String otherDescriptor) {
        return or(otherDescriptor);
    }

    public IngredientWrapper alt(IngredientWrapper other) {
        return or(other);
    }

    public IngredientWrapper alt(String otherDescriptor) {
        return or(otherDescriptor);
    }

    public static String normalizeDescriptor(String desc) {
        if (desc == null) return "";
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
