package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Reassembly of split frames, including ordering, duplicates and release of finished messages. */
class ChunkAssemblerAssemblyTest {

    private static final String KEY = "S:player";
    private static final String CHANNEL = "chan";
    private static final String WHOLE = "whole";

    @Test
    void singleFrameIsReturnedDirectly() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertArrayEquals(NetworkTestFixture.bytesOf(WHOLE),
            assembler.accept(KEY, CHANNEL, 0, 1, NetworkTestFixture.bytesOf(WHOLE)),
            "a complete one-frame message must be returned straight away");
    }

    @Test
    void singleFrameReleasesTheAssembly() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, CHANNEL, 0, 1, NetworkTestFixture.bytesOf(WHOLE));

        assertEquals(0, assembler.size(), "a delivered one-frame message must leave no partial state");
    }

    @Test
    void outOfOrderFramesStillReassemble() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, CHANNEL, 2, 3, NetworkTestFixture.bytesOf("cc"));
        assembler.accept(KEY, CHANNEL, 0, 3, NetworkTestFixture.bytesOf("aa"));

        assertArrayEquals(NetworkTestFixture.bytesOf("aabbcc"),
            assembler.accept(KEY, CHANNEL, 1, 3, NetworkTestFixture.bytesOf("bb")),
            "frames arriving out of order must still reassemble in index order");
    }

    @Test
    void incompleteMessageReturnsNoResult() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertEquals(List.of(true, true),
            List.of(
                assembler.accept(KEY, CHANNEL, 0, 3, NetworkTestFixture.bytesOf("aa")) == null,
                assembler.accept(KEY, CHANNEL, 2, 3, NetworkTestFixture.bytesOf("cc")) == null),
            "partial messages must not be delivered to the caller");
    }

    @Test
    void duplicateFrameDoesNotOverwriteTheOriginal() {
        ChunkAssembler assembler = new ChunkAssembler();
        assembler.accept(KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("A"));
        assembler.accept(KEY, CHANNEL, 0, 2, NetworkTestFixture.bytesOf("B"));

        assertArrayEquals(NetworkTestFixture.bytesOf("AC"),
            assembler.accept(KEY, CHANNEL, 1, 2, NetworkTestFixture.bytesOf("C")),
            "a duplicate index must be ignored instead of overwriting the first frame");
    }

    @Test
    void reassembledBytesAreIdentical() {
        byte[] message = NetworkTestFixture.largeChunkedMessage();

        assertArrayEquals(message, NetworkTestFixture.complete(new ChunkAssembler(), KEY, CHANNEL, message),
            "reassembling all frames must reproduce the original bytes exactly");
    }

    @Test
    void completedMessageIsReleased() {
        ChunkAssembler assembler = new ChunkAssembler();
        NetworkTestFixture.complete(assembler, KEY, CHANNEL, NetworkTestFixture.largeChunkedMessage());

        assertEquals(0, assembler.size(), "a fully delivered message must be released from memory");
    }

    @Test
    void pendingMessageIsNotDeliveredEarly() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertNull(assembler.accept(KEY, CHANNEL, 1, 2, NetworkTestFixture.bytesOf("tail")),
            "the last frame alone must not complete a message whose first frame is missing");
    }
}
