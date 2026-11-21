package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Expiry and explicit clearing of partially assembled messages. */
class ChunkAssemblerLifecycleTest {

    private static final String KEY = "S:player";
    private static final String OTHER_KEY = "S:two";
    private static final String CHANNEL = "chan";
    private static final String KEEP_CHANNEL = "keep";
    private static final String DROP_CHANNEL = "drop";

    @Test
    void pendingAssemblyIsCounted() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("A"));

        assertEquals(1, assembler.size(), "one partial message must be tracked while it waits");
    }

    @Test
    void expiredAssemblyRestartsTheMessage() {
        AtomicLong now = new AtomicLong(0);
        ChunkAssembler assembler = new ChunkAssembler(now::get);
        assembler.accept(KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("A"));
        now.addAndGet(ChunkAssembler.TTL_NANOS + 1);

        assembler.accept(KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("B"));

        assertArrayEquals(NetworkTestFixture.bytesOf("BC"),
            assembler.accept(KEY, CHANNEL, 1, 2, NetworkTestFixture.bytesOf("C")),
            "an expired partial must be dropped so the new message reassembles cleanly");
    }

    @Test
    void expiredAssemblyDoesNotCompleteWithStaleFrame() {
        AtomicLong now = new AtomicLong(0);
        ChunkAssembler assembler = new ChunkAssembler(now::get);
        assembler.accept(KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("A"));
        now.addAndGet(ChunkAssembler.TTL_NANOS + 1);

        assertNull(assembler.accept(KEY, CHANNEL, 1, 2, NetworkTestFixture.bytesOf("B")),
            "a frame arriving after the TTL must not complete the stale message");
    }

    @Test
    void clearingOneChannelKeepsTheOthers() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, KEEP_CHANNEL, 0, 2, NetworkTestFixture.bytesOf("k"));
        assembler.accept(KEY, DROP_CHANNEL, 0, 2, NetworkTestFixture.bytesOf("d"));

        assembler.clear(KEY, DROP_CHANNEL);

        assertEquals("k!",
            new String(assembler.accept(KEY, KEEP_CHANNEL, 1, 2, NetworkTestFixture.bytesOf("!"))),
            "clearing one channel must leave the other channel's partial intact");
    }

    @Test
    void clearingOneChannelDropsOnlyThatPartial() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, KEEP_CHANNEL, 0, 2, NetworkTestFixture.bytesOf("k"));
        assembler.accept(KEY, DROP_CHANNEL, 0, 2, NetworkTestFixture.bytesOf("d"));

        assembler.clear(KEY, DROP_CHANNEL);

        assertEquals(1, assembler.size(), "clearing one channel must drop exactly one partial");
    }

    @Test
    void clearingConnectionDropsOnlyItsPartials() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("a"));
        assembler.accept(OTHER_KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("b"));

        assembler.clear(KEY);

        assertEquals(1, assembler.size(), "clearing one connection must drop only its partials");
    }

    @Test
    void clearingConnectionKeepsTheOtherPartial() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("a"));
        assembler.accept(OTHER_KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("b"));

        assembler.clear(KEY);

        assertNotNull(assembler.accept(OTHER_KEY, CHANNEL, 1, 2, NetworkTestFixture.bytesOf("!")),
            "another connection's partial must still complete after a clear");
    }

    @Test
    void clearAllDropsEverything() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("a"));

        assembler.clearAll();

        assertEquals(0, assembler.size(), "clearAll must drop every partial message");
    }
}
