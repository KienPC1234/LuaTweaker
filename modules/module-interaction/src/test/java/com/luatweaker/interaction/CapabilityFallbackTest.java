package com.luatweaker.interaction;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins what a script observes when a capability call cannot be serviced: invalid sides and
 * malformed arguments are rejected before the platform, and a missing capability layer yields
 * nil for reads / 0 for writes instead of an exception.
 */
class CapabilityFallbackTest {

    private RecordingCapability capability;

    @BeforeEach
    void setUp() {
        capability = new RecordingCapability();
        Platform.setCapability(capability);
    }

    @AfterEach
    void tearDown() {
        Platform.setCapability(null);
    }

    private ILuaEngine engineWithBindings() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);
        return engine;
    }

    /** Runs every capability read/write against a platform that has no capability layer. */
    private void runWithoutCapability() {
        Platform.setCapability(null);
        ILuaEngine engine = engineWithBindings();
        engine.executeStringOrThrow("""
                assert(World:GetEnergy(0, 0, 0) == nil)
                assert(World:GetItemSlots(0, 0, 0) == nil)
                assert(World:GetFluid(0, 0, 0) == nil)
                assert(World:InsertEnergy(0, 0, 0, 10) == 0)
                assert(World:InsertItem(0, 0, 0, 'minecraft:coal', 1) == 0)
                assert(World:FillFluid(0, 0, 0, 'minecraft:water', 10) == 0)
                """, "capability_missing");
    }

    @Test
    void invalidSideIsRejectedBeforeThePlatform() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow("""
                assert(World:GetEnergy(0, 0, 0, 'sideways') == nil,
                    'a bad side must not fall back')
                assert(World:InsertEnergy(0, 0, 0, 10, 'sideways') == 0)
                """, "capability_bad_side");

        assertEquals(List.of(), capability.calls,
                "an invalid side must never reach the capability layer");
    }

    @Test
    void missingCapabilityKeepsReadsAndWritesUsable() {
        assertDoesNotThrow(this::runWithoutCapability,
                "a script must survive reads and writes when no capability layer is installed");
    }

    @Test
    void missingCapabilityNeverReachesThePlatform() {
        runWithoutCapability();
        assertEquals(List.of(), capability.calls,
                "a missing capability layer must not dispatch anything to the platform");
    }

    @Test
    void malformedArgumentsNeverReachThePlatform() {
        ILuaEngine engine = engineWithBindings();

        engine.executeStringOrThrow("""
                assert(World:GetEnergy(1, 2) == nil)
                assert(World:InsertItem(1, 2, 3, 5, 6) == 0,
                    'a non-string item id must be rejected')
                """, "capability_bad_args");

        assertEquals(List.of(), capability.calls,
                "malformed coordinates or item ids must be rejected before the capability layer");
    }
}
