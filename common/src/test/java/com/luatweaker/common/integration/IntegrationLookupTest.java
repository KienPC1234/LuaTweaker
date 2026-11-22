package com.luatweaker.common.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class IntegrationLookupTest {

    private static final String MEKANISM_ID = "Mekanism";

    @BeforeEach
    @AfterEach
    void cleanup() {
        IntegrationManager.clear();
    }

    private static IPlatformIntegration mekanism() {
        return new GenericPlatformIntegration(MEKANISM_ID, MEKANISM_ID);
    }

    @Test
    void lookupIsCaseInsensitiveLowercase() {
        IPlatformIntegration integration = mekanism();
        IntegrationManager.register(integration);

        assertSame(integration, IntegrationManager.get("mekanism"),
                "lookup must ignore case so Lua scripts can use any casing");
    }

    @Test
    void lookupIsCaseInsensitiveUppercase() {
        IPlatformIntegration integration = mekanism();
        IntegrationManager.register(integration);

        assertSame(integration, IntegrationManager.get("MEKANISM"),
                "lookup must ignore case so Lua scripts can use any casing");
    }

    @Test
    void lookupFindsExactCasing() {
        IPlatformIntegration integration = mekanism();
        IntegrationManager.register(integration);

        assertSame(integration, IntegrationManager.get(MEKANISM_ID),
                "lookup must find the registered integration by its exact id");
    }

    @Test
    void unknownIdYieldsNull() {
        IntegrationManager.register(mekanism());

        assertNull(IntegrationManager.get("unknown_mod"),
                "an unregistered id must yield null instead of throwing");
    }

    @Test
    void logStatusDoesNotThrow() {
        IntegrationManager.register(new GenericPlatformIntegration("mekanism", "Mekanism"));

        assertDoesNotThrow(IntegrationManager::logStatus,
                "logging integration status must never throw");
    }
}
