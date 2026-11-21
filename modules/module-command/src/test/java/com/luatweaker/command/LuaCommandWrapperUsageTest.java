package com.luatweaker.command;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaCommandWrapperUsageTest {

    private static final String UNKNOWN = "ghost";
    private static final String DEPLOY = "deploy";
    private static final String BOOM = "boom2";
    private static final String QUIET = "quiet";

    private static final String DEPLOY_DEF =
        "{ Usage = '/deploy <target>', Handler = function(sender, args) return false end }";
    private static final String BOOM_DEF =
        "{ Usage = '/boom2', Handler = function(sender, args) error('kaboom') end }";
    private static final String QUIET_DEF = "{ Handler = function(sender, args) return false end }";

    private static final String DEPLOY_USAGE_MESSAGE = "MSG:Usage: /deploy <target>";
    private static final String BOOM_USAGE_MESSAGE = "MSG:Usage: /boom2";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void unregisteredCommandReturnsFailure() {
        int result = new LuaCommandWrapper(UNKNOWN).execute(new MockCommandSender(true, 4));

        assertEquals(0, result, "executing an unregistered command must return failure");
    }

    @Test
    void unregisteredCommandSendsNotRegisteredError() {
        MockCommandSender sender = new MockCommandSender(true, 4);
        new LuaCommandWrapper(UNKNOWN).execute(sender);

        assertTrue(sender.messages.stream()
                .anyMatch(m -> m.startsWith("ERR:") && m.contains("not registered")),
            "an unregistered command must explain that no mod registered it");
    }

    @Test
    void usageHintSentWhenHandlerFails() {
        CommandRun run = CommandWrapperFixture.execute(DEPLOY, DEPLOY_DEF, new MockCommandSender(true, 4));

        assertTrue(run.sender().messages.contains(DEPLOY_USAGE_MESSAGE),
            "a failing handler must surface the declared Usage hint");
    }

    @Test
    void usageHintSentWhenHandlerErrors() {
        CommandRun run = CommandWrapperFixture.execute(BOOM, BOOM_DEF, new MockCommandSender(true, 4));

        assertTrue(run.sender().messages.contains(BOOM_USAGE_MESSAGE),
            "a handler that raises a Lua error must also surface the Usage hint");
    }

    @Test
    void noUsageHintWhenAbsent() {
        CommandRun run = CommandWrapperFixture.execute(QUIET, QUIET_DEF, new MockCommandSender(true, 4));

        assertFalse(run.sender().messages.stream().anyMatch(m -> m.contains("Usage:")),
            "a definition without Usage must not send a usage hint");
    }
}
