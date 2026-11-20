package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Chunking is the fix for "oversized payload tears the connection down". */
class ChunkAssemblerTest {

    private static byte[] bytesOf(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] complete(ChunkAssembler assembler, String key, String channel, byte[] message) {
        List<byte[]> chunks = ChunkAssembler.split(message);
        byte[] result = null;
        for (int index = 0; index < chunks.size(); index++) {
            result = assembler.accept(key, channel, index, chunks.size(), chunks.get(index));
        }
        return result;
    }

    @Test
    void smallMessageIsOneFrame() {
        List<byte[]> chunks = ChunkAssembler.split(bytesOf("hello"));

        assertEquals(1, chunks.size());
        assertArrayEquals(bytesOf("hello"), chunks.get(0));
    }

    @Test
    void emptyMessageIsOneEmptyFrame() {
        List<byte[]> chunks = ChunkAssembler.split(new byte[0]);

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).length);
    }

    @Test
    void largeMessageSplitsIntoBoundedFramesAndReassembles() {
        byte[] message = new byte[ChunkAssembler.CHUNK_BYTES * 2 + 5];
        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) (i * 31);
        }

        List<byte[]> chunks = ChunkAssembler.split(message);
        assertEquals(3, chunks.size());
        for (byte[] chunk : chunks) {
            assertTrue(chunk.length <= ChunkAssembler.CHUNK_BYTES);
        }

        ChunkAssembler assembler = new ChunkAssembler();
        assertArrayEquals(message, complete(assembler, "S:player", "chan", message));
        assertEquals(0, assembler.size(), "a completed message must be released");
    }

    @Test
    void oversizedMessageIsRejectedBeforeSending() {
        byte[] message = new byte[ChunkAssembler.MAX_MESSAGE_BYTES + 1];

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ChunkAssembler.split(message));
        assertTrue(error.getMessage().contains("above"), error.getMessage());
    }

    @Test
    void singleFrameIsReturnedDirectly() {
        ChunkAssembler assembler = new ChunkAssembler();

        byte[] result = assembler.accept("S:player", "chan", 0, 1, bytesOf("whole"));

        assertArrayEquals(bytesOf("whole"), result);
        assertEquals(0, assembler.size());
    }

    @Test
    void outOfOrderFramesStillReassemble() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertNull(assembler.accept("S:player", "chan", 2, 3, bytesOf("cc")));
        assertNull(assembler.accept("S:player", "chan", 0, 3, bytesOf("aa")));
        byte[] done = assembler.accept("S:player", "chan", 1, 3, bytesOf("bb"));

        assertArrayEquals(bytesOf("aabbcc"), done);
    }

    @Test
    void duplicateFrameIsIgnored() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertNull(assembler.accept("S:player", "chan", 0, 2, bytesOf("A")));
        assertNull(assembler.accept("S:player", "chan", 0, 2, bytesOf("B")));
        byte[] done = assembler.accept("S:player", "chan", 1, 2, bytesOf("C"));

        assertArrayEquals(bytesOf("AC"), done, "a duplicate must not overwrite the original frame");
    }

    @Test
    void changingChunkCountMidMessageIsRejected() {
        ChunkAssembler assembler = new ChunkAssembler();
        assertNull(assembler.accept("S:player", "chan", 0, 3, bytesOf("a")));

        assertThrows(IllegalArgumentException.class,
                () -> assembler.accept("S:player", "chan", 1, 2, bytesOf("b")));
    }

    @Test
    void invalidChunkCountAndIndexAreRejected() {
        ChunkAssembler assembler = new ChunkAssembler();

        assertThrows(IllegalArgumentException.class,
                () -> assembler.accept("S:player", "chan", 0, 0, bytesOf("a")));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.accept("S:player", "chan", 0, ChunkAssembler.MAX_CHUNKS + 1, bytesOf("a")));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.accept("S:player", "chan", -1, 2, bytesOf("a")));
        assertThrows(IllegalArgumentException.class,
                () -> assembler.accept("S:player", "chan", 2, 2, bytesOf("a")));
    }

    @Test
    void oversizedFrameIsRejected() {
        ChunkAssembler assembler = new ChunkAssembler();
        byte[] tooBig = new byte[ChunkAssembler.CHUNK_BYTES + 1];

        assertThrows(IllegalArgumentException.class,
                () -> assembler.accept("S:player", "chan", 0, 2, tooBig));
    }

    @Test
    void perConnectionAssemblyCapIsEnforced() {
        ChunkAssembler assembler = new ChunkAssembler();
        assertNull(assembler.accept("S:player", "a", 0, 2, bytesOf("a")));
        assertNull(assembler.accept("S:player", "b", 0, 2, bytesOf("b")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> assembler.accept("S:player", "c", 0, 2, bytesOf("c")));
        assertTrue(error.getMessage().contains("connection"), error.getMessage());
    }

    @Test
    void globalAssemblyCapIsEnforced() {
        ChunkAssembler assembler = new ChunkAssembler();
        for (int i = 0; i < ChunkAssembler.MAX_ASSEMBLIES; i++) {
            assertNull(assembler.accept("S:" + i, "chan", 0, 2, bytesOf("a")));
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> assembler.accept("S:overflow", "chan", 0, 2, bytesOf("a")));
        assertTrue(error.getMessage().contains("concurrent"), error.getMessage());
    }

    @Test
    void expiredAssemblyIsDropped() {
        AtomicLong now = new AtomicLong(0);
        ChunkAssembler assembler = new ChunkAssembler(now::get);

        assertNull(assembler.accept("S:player", "chan", 0, 2, bytesOf("A")));
        assertEquals(1, assembler.size());

        now.addAndGet(ChunkAssembler.TTL_NANOS + 1);

        // The stale assembly is gone, so this frame starts a fresh message.
        assertNull(assembler.accept("S:player", "chan", 0, 2, bytesOf("B")));
        byte[] done = assembler.accept("S:player", "chan", 1, 2, bytesOf("C"));

        assertArrayEquals(bytesOf("BC"), done);
    }

    @Test
    void clearingOneChannelKeepsTheOthers() {
        ChunkAssembler assembler = new ChunkAssembler();
        assertNull(assembler.accept("S:player", "keep", 0, 2, bytesOf("k")));
        assertNull(assembler.accept("S:player", "drop", 0, 2, bytesOf("d")));

        assembler.clear("S:player", "drop");
        assertEquals(1, assembler.size());
        assertEquals("k!", new String(
                assembler.accept("S:player", "keep", 1, 2, bytesOf("!"))));
    }

    @Test
    void clearingAConnectionDropsItsPartialsOnly() {
        ChunkAssembler assembler = new ChunkAssembler();
        assertNull(assembler.accept("S:one", "chan", 0, 2, bytesOf("a")));
        assertNull(assembler.accept("S:two", "chan", 0, 2, bytesOf("b")));

        assembler.clear("S:one");

        assertEquals(1, assembler.size());
        assertNotNull(assembler.accept("S:two", "chan", 1, 2, bytesOf("!")));
    }

    @Test
    void clearAllDropsEverything() {
        ChunkAssembler assembler = new ChunkAssembler();
        assertNull(assembler.accept("S:one", "chan", 0, 2, bytesOf("a")));
        assembler.clearAll();

        assertEquals(0, assembler.size());
    }

    @Test
    void reassembledBytesAreIdentical() {
        byte[] message = new byte[ChunkAssembler.CHUNK_BYTES + 17];
        java.util.Random random = new java.util.Random(42);
        random.nextBytes(message);

        assertArrayEquals(message, complete(new ChunkAssembler(), "S:player", "chan", message));
    }
}
