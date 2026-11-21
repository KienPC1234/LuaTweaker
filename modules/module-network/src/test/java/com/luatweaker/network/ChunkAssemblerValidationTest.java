package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import static com.luatweaker.network.NetworkTestFixture.bytesOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Rejection of impossible or hostile chunk headers. */
class ChunkAssemblerValidationTest {

    private static final String KEY = "S:player";
    private static final String CHANNEL = "chan";

    @Test
    void changingChunkCountMidMessageIsRejected() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, CHANNEL, 0, 3, bytesOf("a"));

        assertThrows(IllegalArgumentException.class,
            () -> assembler.accept(KEY, CHANNEL, 1, 2, bytesOf("b")),
            "a chunk count that changes mid-message must be rejected");
    }

    @Test
    void zeroChunkCountIsRejected() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertThrows(IllegalArgumentException.class,
            () -> assembler.accept(KEY, CHANNEL, 0, 0, bytesOf("a")),
            "a message declaring zero chunks must be rejected");
    }

    @Test
    void chunkCountAboveMaxIsRejected() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertThrows(IllegalArgumentException.class,
            () -> assembler.accept(KEY, CHANNEL, 0, ChunkAssembler.MAX_CHUNKS + 1, bytesOf("a")),
            "a chunk count above the protocol maximum must be rejected");
    }

    @Test
    void negativeChunkIndexIsRejected() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertThrows(IllegalArgumentException.class,
            () -> assembler.accept(KEY, CHANNEL, -1, 2, bytesOf("a")),
            "a negative chunk index must be rejected");
    }

    @Test
    void chunkIndexAtOrBeyondCountIsRejected() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertThrows(IllegalArgumentException.class,
            () -> assembler.accept(KEY, CHANNEL, 2, 2, bytesOf("a")),
            "a chunk index equal to the chunk count must be rejected");
    }

    @Test
    void oversizedFrameIsRejected() {
        ChunkAssembler assembler = new ChunkAssembler();
        byte[] tooBig = new byte[ChunkAssembler.MAX_FRAME_BYTES + 1];

        assertThrows(IllegalArgumentException.class,
            () -> assembler.accept(KEY, CHANNEL, 0, 2, tooBig),
            "a frame larger than the receive cap must be rejected");
    }

    @Test
    void legacyLargeFrameIsAccepted() {
        ChunkAssembler assembler = new ChunkAssembler();
        byte[] legacy = new byte[ChunkAssembler.CHUNK_BYTES * 2];

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () -> assembler.accept(KEY, CHANNEL, 0, 2, legacy),
            "a frame above the send split size but below the receive cap (older peers) must pass bounds");
    }
}
