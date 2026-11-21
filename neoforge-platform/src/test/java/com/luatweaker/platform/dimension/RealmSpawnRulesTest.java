package com.luatweaker.platform.dimension;

import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.spawn.SpawnRuleServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The SpawnRules module keeps full-control handlers (no rule-based API). */
class RealmSpawnRulesTest {

    private static SpawnRuleServiceImpl spawnRules;

    @BeforeAll
    static void ensureLoaded() {
        DimensionShowcaseFixture.ensureLoaded();
        Object spawnObj = LuaServiceRegistry.get("SpawnRuleServiceImpl");
        assertTrue(spawnObj instanceof SpawnRuleServiceImpl,
                "SpawnRuleServiceImpl must be registered");
        spawnRules = (SpawnRuleServiceImpl) spawnObj;
    }

    @Test
    void realmSpawnHandlerIsRegistered() {
        assertNotNull(spawnRules.getHandler(DimensionShowcaseFixture.REALM_ID),
                "realm spawn handler must be registered");
    }

    @Test
    void skylandsSpawnHandlerIsRegistered() {
        assertNotNull(spawnRules.getHandler(DimensionShowcaseFixture.SKYLANDS_ID),
                "skylands spawn handler must be registered");
    }
}
