package com.luatweaker.api.wrapper;

public record RecipeIdentifier(String id) {
    public RecipeIdentifier {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
    }
}
