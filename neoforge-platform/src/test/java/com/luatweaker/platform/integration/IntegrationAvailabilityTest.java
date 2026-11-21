package com.luatweaker.platform.integration;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationAvailabilityTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        IntegrationManager.clear();
    }

    private static void registerAvailabilityPair() {
        IPlatformIntegration available = new IPlatformIntegration() {
            @Override public @NotNull String modId() { return "mod_a"; }
            @Override public @NotNull String displayName() { return "Mod A"; }
            @Override public boolean isAvailable() { return true; }
        };
        IPlatformIntegration unavailable = new IPlatformIntegration() {
            @Override public @NotNull String modId() { return "mod_b"; }
            @Override public @NotNull String displayName() { return "Mod B"; }
            @Override public boolean isAvailable() { return false; }
        };

        IntegrationManager.register(available);
        IntegrationManager.register(unavailable);
    }

    @Test
    void availableIntegrationReportsAvailable() {
        registerAvailabilityPair();

        assertTrue(IntegrationManager.isAvailable("mod_a"),
                "an available integration must report available");
    }

    @Test
    void unavailableIntegrationReportsUnavailable() {
        registerAvailabilityPair();

        assertFalse(IntegrationManager.isAvailable("mod_b"),
                "an unavailable integration must report unavailable");
    }

    @Test
    void unknownIntegrationReportsUnavailable() {
        registerAvailabilityPair();

        assertFalse(IntegrationManager.isAvailable("mod_c"),
                "an unregistered integration must report unavailable");
    }
}
