package com.luatweaker.common.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the "no server" contract of the server primitives. */
class ServerServiceImplTest {

    private final ServerServiceImpl service = new ServerServiceImpl();

    @Test
    void noServerReportsZeroCounters() {
        assertZeroCounters();
    }

    @Test
    void broadcastWithoutServerFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> service.broadcast("hello"),
                "broadcast must fail instead of silently discarding the message");
    }

    @Test
    void kickWithoutServerFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> service.kick("Steve", "test"),
                "kick must fail instead of pretending to disconnect a player");
    }

    private void assertZeroCounters() {
        assertEquals(0, service.getPlayerCount(), "player count must be 0 without a server");
        assertEquals(0, service.getMaxPlayers(), "max players must be 0 without a server");
        assertEquals(0.0, service.getTps(), "TPS must be 0 without a server");
        assertEquals(0.0, service.getMspt(), "MSPT must be 0 without a server");
        assertEquals(0L, service.getTickCount(), "tick count must be 0 without a server");
    }
}
