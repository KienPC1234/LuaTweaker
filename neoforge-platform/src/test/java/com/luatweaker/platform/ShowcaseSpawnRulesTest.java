package com.luatweaker.platform;

import com.luatweaker.spawn.SpawnRuleServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The showcase worlds must load their spawn rules from the config. */
class ShowcaseSpawnRulesTest {

    private static SpawnRuleServiceImpl spawnRules;

    @BeforeAll
    static void loadMods() {
        AutonomousModsFixture.ensureLoaded();
        spawnRules = AutonomousModsFixture.getSpawnRules();
    }

    @Test
    void spawnRuleServiceIsRegistered() {
        assertNotNull(spawnRules,
                "SpawnRuleServiceImpl must be registered");
    }

    @Test
    void grandWildsLoadsFiveSpawnRules() {
        assertEquals(5, spawnRules.rulesFor(AutonomousModsFixture.GRAND_WILDS).size(),
                "grand_wilds must load its spawn_rules from the config");
    }

    @Test
    void alpineSpecLoadsTwoSpawnRules() {
        assertEquals(2, spawnRules.rulesFor(AutonomousModsFixture.ALPINE_SPEC).size(),
                "alpine_spec must load its spawn_rules from the config");
    }
}
