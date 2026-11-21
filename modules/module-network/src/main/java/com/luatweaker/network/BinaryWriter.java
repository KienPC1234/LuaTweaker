package com.luatweaker.network;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Growable big-endian byte writer shared by the Lua wire codec and the RPC envelope.
 *
 * <p>Varints are unsigned 32-bit LEB128. A negative {@code int} is written as its unsigned
 * bit pattern, which is what {@link #writeZigZagInt(int)} relies on.</p>
 */
final class BinaryWriter {

    private byte[] buffer = new byte[256];
    private int count;

    void writeByte(int value) {
        ensure(1);
        buffer[count++] = (byte) value;
    }

    void writeVarInt(int value) {
        long remaining = value & 0xFFFF_FFFFL;
        while (remaining >= 0x80L) {
            ensure(1);
            buffer[count++] = (byte) ((remaining & 0x7FL) | 0x80L);
            remaining >>>= 7;
        }
        ensure(1);
        buffer[count++] = (byte) remaining;
    }

    void writeZigZagInt(int value) {
        writeVarInt((value << 1) ^ (value >> 31));
    }

    void writeDouble(double value) {
        long bits = Double.doubleToLongBits(value);
        ensure(8);
        for (int shift = 56; shift >= 0; shift -= 8) {
            buffer[count++] = (byte) (bits >>> shift);
        }
    }

    void writeBytes(byte @NotNull [] data) {
        ensure(data.length);
        System.arraycopy(data, 0, buffer, count, data.length);
        count += data.length;
    }

    void writeUtf8(@NotNull String value) {
        writeUtf8(value, Integer.MAX_VALUE);
    }

    /**
     * Writes a length-prefixed UTF-8 string.
     *
     * @throws IllegalArgumentException when the encoding is longer than {@code maxBytes}
     */
    void writeUtf8(@NotNull String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException(
                    "String is " + bytes.length + " bytes, above the " + maxBytes + " byte limit");
        }
        writeVarInt(bytes.length);
        writeBytes(bytes);
    }

    int size() {
        return count;
    }

    byte @NotNull [] toByteArray() {
        return Arrays.copyOf(buffer, count);
    }

    private void ensure(int extra) {
        int required = count + extra;
        if (required <= buffer.length) {
            return;
        }
        int capacity = buffer.length;
        while (capacity < required) {
            int next = capacity <= (Integer.MAX_VALUE / 2) ? capacity * 2 : Integer.MAX_VALUE;
            if (next == capacity) {
                throw new IllegalStateException("Network payload exceeded the maximum array size");
            }
            capacity = next;
        }
        buffer = Arrays.copyOf(buffer, capacity);
    }
}
