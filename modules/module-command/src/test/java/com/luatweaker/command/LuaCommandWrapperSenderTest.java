package com.luatweaker.command;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaCommandWrapperSenderTest {

    private static final String HELLO = "hello";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    private CommandRun runHello() {
        return CommandWrapperFixture.execute(HELLO, CommandTestFixture.HELLO_DEF,
            new MockCommandSender(true, 4), "world");
    }

    @Test
    void senderReceivesSendSuccess() {
        assertTrue(runHello().sender().messages.contains("OK:Hi world"),
            "sender:SendSuccess must reach the command sender verbatim");
    }

    @Test
    void senderPayloadCarriesName() {
        assertEquals("TestPlayer", CommandWrapperFixture.globalString(runHello().engine(), "_senderName"),
            "the sender table must expose the sender name");
    }

    @Test
    void senderPayloadCarriesPlayerFlag() {
        assertTrue(CommandWrapperFixture.globalBoolean(runHello().engine(), "_senderIsPlayer"),
            "the sender table must expose the is-player flag as a Lua boolean");
    }

    @Test
    void senderPayloadCarriesHeldItemId() {
        assertEquals("minecraft:diamond_sword",
            CommandWrapperFixture.globalString(runHello().engine(), "_senderHeldItem"),
            "the sender table must expose the held item id");
    }

    @Test
    void senderPayloadCarriesPermissionResult() {
        assertTrue(CommandWrapperFixture.globalBoolean(runHello().engine(), "_senderHasPerm"),
            "sender:HasPermission must forward to the underlying sender");
    }

    @Test
    void senderPayloadCarriesArgumentCount() {
        assertEquals(1, CommandWrapperFixture.globalInt(runHello().engine(), "_argCount"),
            "the args table must contain exactly the typed tokens");
    }

    @Test
    void senderPayloadCarriesFirstArgument() {
        assertEquals("world", CommandWrapperFixture.globalString(runHello().engine(), "_arg1"),
            "the args table must carry each typed token by position");
    }
}
