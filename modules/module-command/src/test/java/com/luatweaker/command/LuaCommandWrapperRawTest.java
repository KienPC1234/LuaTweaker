package com.luatweaker.command;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuaCommandWrapperRawTest {

    private static final String ECHO = "echo";
    private static final String ECHO_DEF =
        "{ Handler = function(sender, args, raw) _G._raw = raw; _G._tokens = #args end }";
    private static final String RAW_INPUT = "  hello   world  ";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void executeRawReturnsSuccess() {
        int result = CommandWrapperFixture.executeRaw(ECHO, ECHO_DEF, new MockCommandSender(true, 4), RAW_INPUT)
            .result();

        assertEquals(1, result, "a successful raw execution must return Brigadier success");
    }

    @Test
    void executeRawPreservesExactTypedText() {
        CommandRun run = CommandWrapperFixture.executeRaw(ECHO, ECHO_DEF, new MockCommandSender(true, 4), RAW_INPUT);

        assertEquals(RAW_INPUT, CommandWrapperFixture.globalString(run.engine(), "_raw"),
            "the raw tail must reach the handler exactly as typed, spacing included");
    }

    @Test
    void executeRawSplitsTokenArray() {
        CommandRun run = CommandWrapperFixture.executeRaw(ECHO, ECHO_DEF, new MockCommandSender(true, 4), RAW_INPUT);

        assertEquals(2, CommandWrapperFixture.globalInt(run.engine(), "_tokens"),
            "the token array must still split the raw tail on whitespace");
    }
}
