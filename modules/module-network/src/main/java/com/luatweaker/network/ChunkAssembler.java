package com.luatweaker.network;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Splits oversized messages into frames and reassembles them on the other side.
 *
 * <p>Minecraft frames have a hard size limit, and an oversized payload used to tear the
 * connection down instead of failing the call. Anything above {@link #CHUNK_BYTES} is now
 * sent as {@link #MAX_CHUNKS} bounded frames and rebuilt here before it reaches the Lua
 * layer.</p>
 *
 * <p>The receiver treats all input as hostile: chunk count, index, per-chunk size, total
 * size, concurrent messages per connection and message lifetime are all capped, so a client
 * cannot exhaust server memory by opening assemblies.</p>
 */
public final class ChunkAssembler {

    /**
     * Bytes per outbound frame. Conservative on purpose: small frames stay clear of
     * the custom-payload ceilings enforced by vanilla, NeoForge and third-party
     * Netty pipeline mods on every supported version, so a frame can never tear
     * the connection down before {@link #accept} sees it.
     */
    public static final int CHUNK_BYTES = 32 * 1024;
    /** Largest single frame the receiver accepts (older 256 KiB senders stay compatible). */
    public static final int MAX_FRAME_BYTES = 256 * 1024;
    /** A message is at most this many frames (32 KiB * 128 = 4 MiB, unchanged). */
    public static final int MAX_CHUNKS = 128;
    /** Largest message that can be reassembled. */
    public static final int MAX_MESSAGE_BYTES = CHUNK_BYTES * MAX_CHUNKS;
    /**
     * Concurrent in-flight messages across every connection. Sized so a full
     * server (100+ players each bursting their per-connection budget) never
     * starves: 2048 slots of at most one 32 KiB parked chunk bound the
     * slot-squatting cost while honest bursts always fit.
     */
    public static final int MAX_ASSEMBLIES = 2_048;
    /** Concurrent in-flight messages per connection. */
    public static final int MAX_ASSEMBLIES_PER_KEY = 4;
    /**
     * An assembly nobody finishes is dropped after this long. Chunks of one
     * message travel back-to-back, so 5 s is plenty for honest lag and short
     * enough that parked single-chunk assemblies cannot hold slots hostage.
     */
    public static final long TTL_NANOS = TimeUnit.SECONDS.toNanos(5);
    /** A message delivered in a single frame skips assembly entirely. */
    private static final int SINGLE_FRAME_CHUNKS = 1;

    private final LongSupplier clock;
    private final Map<AssemblyKey, Assembly> assemblies = new ConcurrentHashMap<>();

    public ChunkAssembler() {
        this(System::nanoTime);
    }

    /** Injectable clock so the expiry path can be tested without sleeping. */
    public ChunkAssembler(@NotNull LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Splits one message into frames.
     *
     * @throws IllegalArgumentException when the message exceeds {@link #MAX_MESSAGE_BYTES}
     */
    public static @NotNull List<byte @NotNull []> split(byte @NotNull [] data) {
        Objects.requireNonNull(data, "data");
        if (data.length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Network message is " + data.length
                    + " bytes, above the " + MAX_MESSAGE_BYTES + " byte maximum");
        }
        if (data.length == 0) {
            return List.of(new byte[0]);
        }
        int count = (data.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        List<byte[]> chunks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int from = index * CHUNK_BYTES;
            int to = Math.min(from + CHUNK_BYTES, data.length);
            chunks.add(Arrays.copyOfRange(data, from, to));
        }
        return chunks;
    }

    /**
     * Adds one frame.
     *
     * @param key     connection identity, e.g. {@code "S:<uuid>"}; never a Lua-controlled value
     * @param channel wire channel the frame belongs to
     * @return the complete message once the last frame arrives, otherwise {@code null}
     * @throws IllegalArgumentException when the frame is malformed, oversized or over a cap
     */
    public byte @Nullable [] accept(@NotNull String key, @NotNull String channel,
                                    int index, int total, byte @NotNull [] data) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(data, "data");
        validateFrameBounds(index, total, data);
        if (total == SINGLE_FRAME_CHUNKS) {
            return data;
        }

        long now = clock.getAsLong();
        assemblies.entrySet().removeIf(entry -> now - entry.getValue().createdAtNanos > TTL_NANOS);

        AssemblyKey assemblyKey = new AssemblyKey(key, channel);
        if (!assemblies.containsKey(assemblyKey)) {
            rejectOverCapacity(key);
        }

        byte[][] completed = new byte[1][];
        assemblies.compute(assemblyKey, (ignored, existing) ->
                accumulateChunk(existing, total, index, data, now, completed));
        return completed[0];
    }

    private static void validateFrameBounds(int index, int total, byte[] data) {
        if (total < 1 || total > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid chunk count " + total + " (max " + MAX_CHUNKS + ")");
        }
        if (index < 0 || index >= total) {
            throw new IllegalArgumentException("Invalid chunk index " + index + " of " + total);
        }
        if (data.length > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException(
                    "Chunk is " + data.length + " bytes (max " + MAX_FRAME_BYTES + ")");
        }
    }

    private void rejectOverCapacity(@NotNull String key) {
        if (assemblies.size() >= MAX_ASSEMBLIES) {
            throw new IllegalArgumentException(
                    "Too many concurrent chunked messages (" + MAX_ASSEMBLIES + ")");
        }
        int forKey = 0;
        for (AssemblyKey assemblyKey : assemblies.keySet()) {
            if (assemblyKey.key().equals(key)) {
                forKey++;
            }
        }
        if (forKey >= MAX_ASSEMBLIES_PER_KEY) {
            throw new IllegalArgumentException("Too many concurrent chunked messages for this connection");
        }
    }

    private static Assembly accumulateChunk(Assembly existing, int total, int index,
                                            byte[] data, long now, byte[][] completed) {
        Assembly assembly = existing;
        if (assembly == null) {
            assembly = new Assembly(total, now);
        } else if (assembly.total != total) {
            throw new IllegalArgumentException(
                    "Chunk count changed from " + assembly.total + " to " + total);
        }
        assembly.add(index, data);
        if (assembly.bytes > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException(
                    "Reassembled message is " + assembly.bytes + " bytes (max " + MAX_MESSAGE_BYTES + ")");
        }
        if (assembly.received == total) {
            completed[0] = assembly.concatenate();
            return null;
        }
        return assembly;
    }

    /** Drops every partial message of one connection (logout, dimension change, reload). */
    public void clear(@NotNull String key) {
        assemblies.keySet().removeIf(assemblyKey -> assemblyKey.key().equals(key));
    }

    /** Drops one channel's partial message, leaving the connection's other channels intact. */
    public void clear(@NotNull String key, @NotNull String channel) {
        assemblies.keySet().removeIf(
                assemblyKey -> assemblyKey.key().equals(key) && assemblyKey.channel().equals(channel));
    }

    public void clearAll() {
        assemblies.clear();
    }

    /** Number of in-flight messages, for diagnostics and tests. */
    public int size() {
        return assemblies.size();
    }

    private record AssemblyKey(String key, String channel) {
    }

    private static final class Assembly {
        private final int total;
        private final byte[][] chunks;
        private final long createdAtNanos;
        private int received;
        private int bytes;

        private Assembly(int total, long createdAtNanos) {
            this.total = total;
            this.chunks = new byte[total][];
            this.createdAtNanos = createdAtNanos;
        }

        private void add(int index, byte[] data) {
            if (chunks[index] != null) {
                // Duplicate frame: ignore it so a retransmit cannot corrupt the message.
                return;
            }
            chunks[index] = data;
            received++;
            bytes += data.length;
        }

        private byte[] concatenate() {
            byte[] merged = new byte[bytes];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, merged, offset, chunk.length);
                offset += chunk.length;
            }
            return merged;
        }
    }
}
