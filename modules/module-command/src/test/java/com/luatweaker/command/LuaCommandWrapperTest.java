package com.luatweaker.command;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaCommandWrapperTest {

    private static final String HELLO = "hello";
    private static final String FAIL = "fail";
    private static final String SILENT = "silent";
    private static final String BOOM = "boom";
    private static final String PERMISSION_ONE = "perm";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void handlerTruthyReturnMapsToSuccess() {
        int result = CommandWrapperFixture.execute(HELLO, CommandTestFixture.HELLO_DEF,
            new MockCommandSender(true, 4), "world").result();

        assertEquals(1, result, "a truthy handler return must map to Brigadier success");
    }

    @Test
    void handlerFalseMapsToFailure() {
        int result = CommandWrapperFixture.execute(FAIL, CommandTestFixture.FAIL_DEF,
            new MockCommandSender(true, 4)).result();

        assertEquals(0, result, "a false handler return must map to Brigadier failure");
    }

    @Test
    void handlerNilMapsToSuccess() {
        int result = CommandWrapperFixture.execute(SILENT, CommandTestFixture.SILENT_DEF,
            new MockCommandSender(true, 4)).result();

        assertEquals(1, result, "a handler that returns nothing must count as success");
    }

    @Test
    void handlerLuaErrorMapsToFailure() {
        int result = CommandWrapperFixture.execute(BOOM, CommandTestFixture.BOOM_DEF,
            new MockCommandSender(true, 4)).result();

        assertEquals(0, result, "a Lua error inside the handler must map to command failure");
    }

    @Test
    void handlerLuaErrorIsDeterministicOnRepeat() {
        CommandRun run = CommandWrapperFixture.execute(BOOM, CommandTestFixture.BOOM_DEF,
            new MockCommandSender(true, 4));
        LuaCommandWrapper wrapper = new LuaCommandWrapper(BOOM);

        assertEquals(wrapper.execute(run.sender()), wrapper.execute(run.sender()),
            "repeated execution of a failing handler must stay deterministic");
    }

    @Test
    void executeRechecksPermissionAtRuntime() {
        CommandRun run = CommandWrapperFixture.execute(PERMISSION_ONE, CommandTestFixture.PERMISSION_ONE_DEF,
            new MockCommandSender(true, 4));

        assertTrue(run.sender().permissionChecks.contains(1),
            "the wrapper must re-check the declared permission level at execution time");
    }
}
