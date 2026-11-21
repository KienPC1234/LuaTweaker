package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Splitting is the fix for "oversized payload tears the connection down". */
class ChunkAssemblerTest {

    private static final String HELLO = "hello";

    @Test
    void smallMessageSplitsIntoOneFrame() {
        assertEquals(1, ChunkAssembler.split(NetworkTestFixture.bytesOf(HELLO)).size(),
            "a message below the chunk size must split into a single frame");
    }

    @Test
    void smallMessageFrameCarriesTheWholeMessage() {
        assertArrayEquals(NetworkTestFixture.bytesOf(HELLO),
            ChunkAssembler.split(NetworkTestFixture.bytesOf(HELLO)).get(0),
            "the single frame must carry the whole message unchanged");
    }

    @Test
    void emptyMessageSplitsIntoOneFrame() {
        assertEquals(1, ChunkAssembler.split(new byte[0]).size(),
            "an empty message must still produce exactly one frame");
    }

    @Test
    void emptyMessageFrameIsEmpty() {
        assertEquals(0, ChunkAssembler.split(new byte[0]).get(0).length,
            "the frame for an empty message must carry zero bytes");
    }

    @Test
    void largeMessageSplitsIntoThreeFrames() {
        assertEquals(3, ChunkAssembler.split(NetworkTestFixture.largeChunkedMessage()).size(),
            "two full chunks plus a five-byte tail must be three frames");
    }

    @Test
    void everyFrameFitsTheChunkSize() {
        assertTrue(ChunkAssembler.split(NetworkTestFixture.largeChunkedMessage()).stream()
                .allMatch(chunk -> chunk.length <= ChunkAssembler.CHUNK_BYTES),
            "no split frame may exceed the configured chunk size");
    }

    @Test
    void oversizedMessageIsRejected() {
        byte[] message = new byte[ChunkAssembler.MAX_MESSAGE_BYTES + 1];

        assertThrows(IllegalArgumentException.class, () -> ChunkAssembler.split(message),
            "a message above the size cap must be rejected before sending");
    }

    @Test
    void oversizedMessageErrorNamesTheLimit() {
        String message = NetworkTestFixture.errorMessage(
            () -> ChunkAssembler.split(new byte[ChunkAssembler.MAX_MESSAGE_BYTES + 1]));

        assertTrue(message.contains("above"),
            "the rejection must explain the size limit, got: " + message);
    }
}
