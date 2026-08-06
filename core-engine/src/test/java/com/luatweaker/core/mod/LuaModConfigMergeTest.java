package com.luatweaker.core.mod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the default-config merge: user values survive, new default keys
 * are added (including nested sections), and malformed input falls back
 * safely.
 */
public class LuaModConfigMergeTest {

    @Test
    void merge_AddsNewTopLevelKeys() {
        String existing = "{\"mana\": {\"max_mana\": 200}}";
        String defaults = "{\"mana\": {\"max_mana\": 200, \"regen\": 5}, \"new_section\": {\"a\": 1}}";
        String merged = LuaMod.mergeConfigs(existing, defaults);
        assertTrue(merged.contains("\"regen\""), "new nested key must be added");
        assertTrue(merged.contains("\"new_section\""), "new top-level section must be added");
    }

    @Test
    void merge_PreservesUserValues() {
        String existing = "{\"mana\": {\"max_mana\": 500, \"regen\": 99}}";
        String defaults = "{\"mana\": {\"max_mana\": 200, \"regen\": 5, \"new_key\": true}}";
        String merged = LuaMod.mergeConfigs(existing, defaults);
        assertTrue(merged.contains("\"max_mana\": 500"), "user value must win");
        assertTrue(merged.contains("\"regen\": 99"), "user value must win");
        assertTrue(merged.contains("\"new_key\": true"), "new default key must be added");
    }

    @Test
    void merge_HandlesNestedObjectsRecursively() {
        String existing = "{\"skills\": {\"crystal_bolt\": {\"cost\": 15}}}";
        String defaults = "{\"skills\": {\"crystal_bolt\": {\"cost\": 10, \"cooldown\": 1.0}, \"frost_nova\": {\"cost\": 40}}}";
        String merged = LuaMod.mergeConfigs(existing, defaults);
        assertTrue(merged.contains("\"cost\": 15"), "nested user value must win");
        assertTrue(merged.contains("\"cooldown\": 1.0"), "nested default key must be added");
        assertTrue(merged.contains("\"frost_nova\""), "new nested section must be added");
    }

    @Test
    void merge_InvalidExistingFallsBackToDefaults() {
        String merged = LuaMod.mergeConfigs("not json{{", "{\"a\": 1}");
        assertTrue(merged.contains("\"a\""), "defaults must win when the existing config is malformed");
    }

    @Test
    void merge_InvalidDefaultsKeepExisting() {
        String merged = LuaMod.mergeConfigs("{\"a\": 1}", "not json{{");
        assertTrue(merged.contains("\"a\": 1"), "existing config must survive malformed defaults");
    }

    @Test
    void merge_PreservesArrayValues() {
        String existing = "{\"list\": [1, 2, 3]}";
        String defaults = "{\"list\": [9], \"other\": 1}";
        String merged = LuaMod.mergeConfigs(existing, defaults);
        assertTrue(merged.contains("1"), "existing array elements must be kept");
        assertTrue(merged.contains("2") && merged.contains("3"), "existing array must be replaced wholesale");
        assertFalse(merged.contains("\"9\"") || merged.matches(".*\\[\\s*9\\s*\\].*"),
                "default array must NOT replace the user array");
        assertTrue(merged.contains("\"other\": 1"));
    }
}
