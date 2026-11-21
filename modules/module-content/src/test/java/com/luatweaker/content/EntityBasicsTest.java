package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Lua-defined entities keep identity, category and dimensions. */
class EntityBasicsTest extends ContentFixture {

    @Test
    void oneEntityRegistered() throws IOException {
        customZombie();

        assertEquals(1, contentService.getRegisteredEntities().size(),
                "exactly one entity must be registered");
    }

    @Test
    void keepsId() throws IOException {
        assertEquals("custom_zombie", customZombie().getId(), "the entity id must be kept");
    }

    @Test
    void keepsCategory() throws IOException {
        assertEquals("MONSTER", customZombie().getCategory(), "the entity category must be kept");
    }

    @Test
    void keepsWidth() throws IOException {
        assertEquals(0.8f, customZombie().getWidth(), "the entity width must be kept");
    }

    @Test
    void keepsHeight() throws IOException {
        assertEquals(2.0f, customZombie().getHeight(), "the entity height must be kept");
    }
}
