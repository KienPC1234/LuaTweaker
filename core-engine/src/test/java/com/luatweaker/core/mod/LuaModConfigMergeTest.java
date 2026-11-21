package com.luatweaker.core.mod;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the default-config merge: user values survive, new default keys
 * are added (including nested sections), and malformed input falls back
 * safely.
 */
class LuaModConfigMergeTest {

    private static final String ARRAY_CONFIG = "{\"list\": [1, 2, 3]}";
    private static final String ARRAY_DEFAULTS = "{\"list\": [9], \"other\": 1}";

    @Nested
    class TopLevelMerge {

        @Test
        void mergeAddsNewTopLevelKeysRegen() {
            String existing = "{\"mana\": {\"max_mana\": 200}}";
            String defaults = "{\"mana\": {\"max_mana\": 200, \"regen\": 5}, \"new_section\": {\"a\": 1}}";
            String merged = LuaMod.mergeConfigs(existing, defaults);
            assertTrue(merged.contains("\"regen\""), "new nested key must be added");
        }

        @Test
        void mergeAddsNewTopLevelKeysNewSection() {
            String existing = "{\"mana\": {\"max_mana\": 200}}";
            String defaults = "{\"mana\": {\"max_mana\": 200, \"regen\": 5}, \"new_section\": {\"a\": 1}}";
            String merged = LuaMod.mergeConfigs(existing, defaults);
            assertTrue(merged.contains("\"new_section\""), "new top-level section must be added");
        }
    }

    @Nested
    class UserValueMerge {

        @Test
        void mergePreservesUserValuesMaxMana500() {
            String existing = "{\"mana\": {\"max_mana\": 500, \"regen\": 99}}";
            String defaults = "{\"mana\": {\"max_mana\": 200, \"regen\": 5, \"new_key\": true}}";
            String merged = LuaMod.mergeConfigs(existing, defaults);
            assertTrue(merged.contains("\"max_mana\": 500"), "the user's max_mana must win over the default");
        }

        @Test
        void mergePreservesUserValuesRegen99() {
            String existing = "{\"mana\": {\"max_mana\": 500, \"regen\": 99}}";
            String defaults = "{\"mana\": {\"max_mana\": 200, \"regen\": 5, \"new_key\": true}}";
            String merged = LuaMod.mergeConfigs(existing, defaults);
            assertTrue(merged.contains("\"regen\": 99"), "the user's regen must win over the default");
        }

        @Test
        void mergePreservesUserValuesNewKeyTrue() {
            String existing = "{\"mana\": {\"max_mana\": 500, \"regen\": 99}}";
            String defaults = "{\"mana\": {\"max_mana\": 200, \"regen\": 5, \"new_key\": true}}";
            String merged = LuaMod.mergeConfigs(existing, defaults);
            assertTrue(merged.contains("\"new_key\": true"), "a key absent from the user config must be added");
        }
    }

    @Nested
    class NestedSectionMerge {

        @Test
        void mergeHandlesNestedObjectsRecursivelyCost15() {
            String existing = "{\"skills\": {\"crystal_bolt\": {\"cost\": 15}}}";
            String defaults = "{\"skills\": {\"crystal_bolt\": {\"cost\": 10, \"cooldown\": 1.0},"
                    + " \"frost_nova\": {\"cost\": 40}}}";
            String merged = LuaMod.mergeConfigs(existing, defaults);
            assertTrue(merged.contains("\"cost\": 15"), "a nested user value must win over the default");
        }

        @Test
        void mergeHandlesNestedObjectsRecursivelyCooldown10() {
            String existing = "{\"skills\": {\"crystal_bolt\": {\"cost\": 15}}}";
            String defaults = "{\"skills\": {\"crystal_bolt\": {\"cost\": 10, \"cooldown\": 1.0},"
                    + " \"frost_nova\": {\"cost\": 40}}}";
            String merged = LuaMod.mergeConfigs(existing, defaults);
            assertTrue(merged.contains("\"cooldown\": 1.0"),
                    "a nested default key missing from the user config must be added");
        }

        @Test
        void mergeHandlesNestedObjectsRecursivelyFrostNova() {
            String existing = "{\"skills\": {\"crystal_bolt\": {\"cost\": 15}}}";
            String defaults = "{\"skills\": {\"crystal_bolt\": {\"cost\": 10, \"cooldown\": 1.0},"
                    + " \"frost_nova\": {\"cost\": 40}}}";
            String merged = LuaMod.mergeConfigs(existing, defaults);
            assertTrue(merged.contains("\"frost_nova\""),
                    "a nested section present only in the defaults must be added");
        }
    }

    @Nested
    class MalformedInput {

        @Test
        void mergeInvalidExistingFallsBackToDefaults() {
            String merged = LuaMod.mergeConfigs("not json{{", "{\"a\": 1}");
            assertTrue(merged.contains("\"a\""), "defaults must win when the existing config is malformed");
        }

        @Test
        void mergeInvalidDefaultsKeepExisting() {
            String merged = LuaMod.mergeConfigs("{\"a\": 1}", "not json{{");
            assertTrue(merged.contains("\"a\": 1"), "existing config must survive malformed defaults");
        }
    }

    @Nested
    class ArrayMerge {

        @Test
        void mergePreservesArrayValues1() {
            String merged = LuaMod.mergeConfigs(ARRAY_CONFIG, ARRAY_DEFAULTS);
            assertTrue(merged.contains("1"), "the first user array element must be kept");
        }

        @Test
        void mergePreservesArrayValues2() {
            String merged = LuaMod.mergeConfigs(ARRAY_CONFIG, ARRAY_DEFAULTS);
            assertTrue(merged.contains("2") && merged.contains("3"),
                    "the remaining user array elements must be kept, not replaced");
        }

        @Test
        void mergePreservesArrayValues9() {
            String merged = LuaMod.mergeConfigs(ARRAY_CONFIG, ARRAY_DEFAULTS);
            assertFalse(merged.contains("\"9\"") || merged.matches(".*\\[\\s*9\\s*\\].*"),
                    "the default array must not replace the user array");
        }

        @Test
        void mergePreservesArrayValuesOther1() {
            String merged = LuaMod.mergeConfigs(ARRAY_CONFIG, ARRAY_DEFAULTS);
            assertTrue(merged.contains("\"other\": 1"),
                    "a key only present in the defaults must be merged next to the user array");
        }
    }
}
