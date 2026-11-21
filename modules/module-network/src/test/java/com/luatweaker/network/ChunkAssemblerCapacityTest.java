package com.luatweaker.network;

import org.junit.jupiter.api.Test;

import static com.luatweaker.network.NetworkTestFixture.bytesOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-connection and global caps that stop a peer from exhausting the assembler. */
class ChunkAssemblerCapacityTest {

    private static final String KEY = "S:player";
    private static final String CHANNEL = "chan";

    @Test
    void perConnectionCapIsEnforced() {
        ChunkAssembler assembler = new ChunkAssembler();
        for (int slot = 0; slot < ChunkAssembler.MAX_ASSEMBLIES_PER_KEY; slot++) {
            assembler.accept(KEY, "chan-" + slot, 0, 2, bytesOf("a"));
        }

        assertThrows(IllegalArgumentException.class,
            () -> assembler.accept(KEY, "chan-overflow", 0, 2, bytesOf("c")),
            "one connection must not hold more partial messages than the per-key cap");
    }

    @Test
    void perConnectionCapErrorNamesTheConnection() {
        ChunkAssembler assembler = new ChunkAssembler();
        for (int slot = 0; slot < ChunkAssembler.MAX_ASSEMBLIES_PER_KEY; slot++) {
            assembler.accept(KEY, "chan-" + slot, 0, 2, bytesOf("a"));
        }

        String message = NetworkTestFixture.errorMessage(
            () -> assembler.accept(KEY, "chan-overflow", 0, 2, bytesOf("c")));

        assertTrue(message.contains("connection"),
            "the per-connection rejection must name the connection limit, got: " + message);
    }

    @Test
    void globalCapIsEnforced() {
        ChunkAssembler assembler = new ChunkAssembler();
        for (int connection = 0; connection < ChunkAssembler.MAX_ASSEMBLIES; connection++) {
            assembler.accept("S:" + connection, CHANNEL, 0, 2, bytesOf("a"));
        }

        assertThrows(IllegalArgumentException.class,
            () -> assembler.accept("S:overflow", CHANNEL, 0, 2, bytesOf("a")),
            "the global concurrent-assembly cap must be enforced");
    }

    @Test
    void globalCapErrorNamesConcurrency() {
        ChunkAssembler assembler = new ChunkAssembler();
        for (int connection = 0; connection < ChunkAssembler.MAX_ASSEMBLIES; connection++) {
            assembler.accept("S:" + connection, CHANNEL, 0, 2, bytesOf("a"));
        }

        String message = NetworkTestFixture.errorMessage(
            () -> assembler.accept("S:overflow", CHANNEL, 0, 2, bytesOf("a")));

        assertTrue(message.contains("concurrent"),
            "the global rejection must name the concurrency limit, got: " + message);
    }
}
