package com.luatweaker.platform.integration;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationLifecycleTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        IntegrationManager.clear();
    }

    private record LifecyclePair(IPlatformIntegration available, IPlatformIntegration unavailable,
            AtomicBoolean engineSetupAvailable, AtomicBoolean engineSetupUnavailable,
            AtomicBoolean stoppingAvailable) {
    }

    private static LifecyclePair registerLifecyclePair() {
        AtomicBoolean engineSetupA = new AtomicBoolean(false);
        AtomicBoolean engineSetupB = new AtomicBoolean(false);
        AtomicBoolean stoppingA = new AtomicBoolean(false);

        IPlatformIntegration modA = new IPlatformIntegration() {
            @Override public @NotNull String modId() { return "mod_a"; }
            @Override public @NotNull String displayName() { return "Mod A"; }
            @Override public boolean isAvailable() { return true; }
            @Override public void onEngineSetup(@NotNull ILuaEngine engine) { engineSetupA.set(true); }
            @Override public void onServerStopping() { stoppingA.set(true); }
        };

        IPlatformIntegration modB = new IPlatformIntegration() {
            @Override public @NotNull String modId() { return "mod_b"; }
            @Override public @NotNull String displayName() { return "Mod B"; }
            @Override public boolean isAvailable() { return false; }
            @Override public void onEngineSetup(@NotNull ILuaEngine engine) { engineSetupB.set(true); }
        };

        IntegrationManager.register(modA);
        IntegrationManager.register(modB);

        ILuaEngine engine = LuaEngineFactory.create(false);
        IntegrationManager.initEngine(engine);
        return new LifecyclePair(modA, modB, engineSetupA, engineSetupB, stoppingA);
    }

    @Test
    void availableIntegrationReceivesEngineSetup() {
        assertTrue(registerLifecyclePair().engineSetupAvailable().get(),
                "Available integration should receive engine setup");
    }

    @Test
    void unavailableIntegrationSkipsEngineSetup() {
        assertFalse(registerLifecyclePair().engineSetupUnavailable().get(),
                "Unavailable integration should NOT receive engine setup");
    }

    @Test
    void availableIntegrationReceivesServerStopping() {
        LifecyclePair pair = registerLifecyclePair();
        IntegrationManager.onServerStopping();

        assertTrue(pair.stoppingAvailable().get(),
                "Available integration should receive server stopping");
    }

    private static AtomicInteger runEngineSetupWithBrokenFirst() {
        AtomicInteger callCount = new AtomicInteger(0);

        IPlatformIntegration broken = new IPlatformIntegration() {
            @Override public @NotNull String modId() { return "broken"; }
            @Override public @NotNull String displayName() { return "Broken Mod"; }
            @Override public boolean isAvailable() { return true; }
            @Override public void onEngineSetup(@NotNull ILuaEngine engine) {
                throw new IllegalStateException("Simulated integration failure");
            }
        };

        IPlatformIntegration normal = new IPlatformIntegration() {
            @Override public @NotNull String modId() { return "normal"; }
            @Override public @NotNull String displayName() { return "Normal Mod"; }
            @Override public boolean isAvailable() { return true; }
            @Override public void onEngineSetup(@NotNull ILuaEngine engine) {
                callCount.incrementAndGet();
            }
        };

        IntegrationManager.register(broken);
        IntegrationManager.register(normal);

        ILuaEngine engine = LuaEngineFactory.create(false);
        assertDoesNotThrow(() -> IntegrationManager.initEngine(engine),
                "a throwing integration must not break engine setup");
        return callCount;
    }

    @Test
    void brokenIntegrationDoesNotHaltOthers() {
        assertEquals(1, runEngineSetupWithBrokenFirst().get(),
                "Normal integration must execute even if preceding integration threw an exception");
    }
}
