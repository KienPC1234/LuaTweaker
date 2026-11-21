package com.luatweaker.command;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaCommandWrapperAccessTest {

    private static final String OP = "op";
    private static final String PLAYER_ONLY = "player_only";
    private static final String OP_DEF =
        "{ PermissionLevel = 2, Handler = function(sender, args) _G._ran = true end }";
    private static final String PLAYER_ONLY_DEF =
        "{ ConsoleAllowed = false, Handler = function(sender, args) _G._ran = true end }";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void permissionDeniedReturnsFailure() {
        CommandRun run = CommandWrapperFixture.execute(OP, OP_DEF, new MockCommandSender(true, 0));

        assertEquals(0, run.result(), "a sender below the required op level must get failure");
    }

    @Test
    void permissionDeniedSendsOperatorError() {
        CommandRun run = CommandWrapperFixture.execute(OP, OP_DEF, new MockCommandSender(true, 0));

        assertTrue(run.sender().messages.stream()
                .anyMatch(m -> m.startsWith("ERR:") && m.contains("operator level 2")),
            "a denied sender must receive the permission error naming the level");
    }

    @Test
    void permissionDeniedSkipsHandler() {
        CommandRun run = CommandWrapperFixture.execute(OP, OP_DEF, new MockCommandSender(true, 0));

        assertFalse(CommandWrapperFixture.handlerRan(run.engine()),
            "the handler must not run when the permission check fails");
    }

    @Test
    void consoleDisallowedReturnsFailure() {
        CommandRun run = CommandWrapperFixture.execute(PLAYER_ONLY, PLAYER_ONLY_DEF,
            new MockCommandSender(false, 4));

        assertEquals(0, run.result(), "a console sender must fail when ConsoleAllowed is false");
    }

    @Test
    void consoleDisallowedSendsPlayerOnlyError() {
        CommandRun run = CommandWrapperFixture.execute(PLAYER_ONLY, PLAYER_ONLY_DEF,
            new MockCommandSender(false, 4));

        assertTrue(run.sender().messages.stream()
                .anyMatch(m -> m.startsWith("ERR:") && m.contains("players")),
            "the console sender must receive the player-only error message");
    }

    @Test
    void consoleDisallowedSkipsHandler() {
        CommandRun run = CommandWrapperFixture.execute(PLAYER_ONLY, PLAYER_ONLY_DEF,
            new MockCommandSender(false, 4));

        assertFalse(CommandWrapperFixture.handlerRan(run.engine()),
            "the handler must not run for a console sender when ConsoleAllowed is false");
    }

    @Test
    void consoleDisallowedAllowsPlayer() {
        CommandRun run = CommandWrapperFixture.execute(PLAYER_ONLY, PLAYER_ONLY_DEF,
            new MockCommandSender(true, 4));

        assertEquals(1, run.result(), "a player sender must pass a ConsoleAllowed = false definition");
    }
}
