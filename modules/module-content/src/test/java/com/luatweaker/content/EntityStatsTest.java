package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Lua-defined entities keep combat stats and spawn-egg colors. */
class EntityStatsTest extends ContentFixture {

    @Test
    void keepsMaxHealth() throws IOException {
        assertEquals(50.0, customZombie().getMaxHealth(), "the max health must be kept");
    }

    @Test
    void keepsMovementSpeed() throws IOException {
        assertEquals(0.3, customZombie().getMovementSpeed(), "the movement speed must be kept");
    }

    @Test
    void keepsAttackDamage() throws IOException {
        assertEquals(8.0, customZombie().getAttackDamage(), "the attack damage must be kept");
    }

    @Test
    void hasSpawnEgg() throws IOException {
        assertTrue(customZombie().hasSpawnEgg(), "the spawn egg flag must be kept");
    }

    @Test
    void keepsPrimaryColor() throws IOException {
        assertEquals(0x00FF00, customZombie().getPrimaryColor(), "the primary egg color must be kept");
    }

    @Test
    void keepsSecondaryColor() throws IOException {
        assertEquals(0x0000FF, customZombie().getSecondaryColor(), "the secondary egg color must be kept");
    }
}
