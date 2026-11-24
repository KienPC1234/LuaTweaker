package com.luatweaker.common.command.core;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DimensionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the console-capable /lt dimension tp path. */
class DimensionCommandTest {
    private static final String DIMENSION = "luatweaker:tp_test";

    private Object previousService;

    @BeforeEach
    void setup() {
        previousService = LuaServiceRegistry.get("DimensionServiceImpl");
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionServiceImpl service = new DimensionServiceImpl(engine);
        service.create(DIMENSION, engine.createTable());
        LuaServiceRegistry.register("DimensionServiceImpl", service);
    }

    @AfterEach
    void restoreRegistry() {
        if (previousService != null) {
            LuaServiceRegistry.register("DimensionServiceImpl", previousService);
        }
    }

    @Test
    void missingArgsAreRejected() {
        MockCommandSender sender = new MockCommandSender();

        assertEquals(0, new DimensionCommand().execute(sender, new String[0]),
                "a call without a sub-command must fail");
    }

    @Test
    void consoleTeleportNeedsPlayerName() {
        MockCommandSender sender = new MockCommandSender();
        new DimensionCommand().execute(sender, new String[]{"tp", DIMENSION});

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("Console needs a player name")),
                "a console caller must be told to supply a player name, got: " + sender.messages);
    }

    @Test
    void unknownDimensionIsRejectedBeforePlayerLookup() {
        MockCommandSender sender = new MockCommandSender();
        new DimensionCommand().execute(sender, new String[]{"tp", "luatweaker:missing", "AnyPlayer"});

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("Unknown dimension")),
                "an unknown dimension must be rejected before looking up a player, got: " + sender.messages);
    }
}
