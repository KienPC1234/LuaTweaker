package com.luatweaker.worldgen;

/**
 * Parsed placement fields shared by ore-like registrations (vanilla placement
 * modifiers: count/rarity, height provider, air-exposure thinning).
 */
record WorldgenPlacement(String step, int count, Integer rarity, int size,
                         int minHeight, int maxHeight, String heightType,
                         String anchorMin, String anchorMax,
                         double discardChance, int inner) {}
