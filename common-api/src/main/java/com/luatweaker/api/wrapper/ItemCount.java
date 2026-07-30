package com.luatweaker.api.wrapper;

public record ItemCount(String itemId, int count) {
    public ItemCount {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId cannot be null");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
    }
}
