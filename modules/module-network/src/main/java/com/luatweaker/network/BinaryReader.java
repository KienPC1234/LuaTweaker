package com.luatweaker.network;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Bounds-checked reader for {@link BinaryWriter} output.
 *
 * <p>Every failure is an {@link IllegalArgumentException}: the caller logs it and drops the
 * packet. A malformed payload must never surface as {@code IndexOutOfBoundsException} or a
 * huge allocation, because those escape the network handler and can tear the connection.</p>
 */
final class BinaryReader {

    private final byte[] buffer;
    private final int end;
    private int position;

    BinaryReader(byte @NotNull [] buffer, int offset, int length) {
        if (offset < 0 || length < 0 || offset > buffer.length - length) {
            throw new IllegalArgumentException("BinaryReader window is outside the buffer");
        }
        this.buffer = buffer;
        this.position = offset;
        this.end = offset + length;
    }

    int remaining() {
        return end - position;
    }

    int readByte() {
        require(1);
        return buffer[position++] & 0xFF;
    }

    int readVarInt() {
        int result = 0;
        int shift = 0;
        while (true) {
            if (shift > 28) {
                throw new IllegalArgumentException("Malformed varint: more than 5 bytes");
            }
            int current = readByte();
            result |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
    }

    int readPositiveVarInt(@NotNull String what) {
        int value = readVarInt();
        if (value < 0) {
            throw new IllegalArgumentException(what + " must not be negative");
        }
        return value;
    }

    int readZigZagInt() {
        int raw = readVarInt();
        return (raw >>> 1) ^ -(raw & 1);
    }

    double readDouble() {
        require(8);
        long bits = 0;
        for (int i = 0; i < 8; i++) {
            bits = (bits << 8) | (buffer[position++] & 0xFFL);
        }
        return Double.longBitsToDouble(bits);
    }

    byte @NotNull [] readBytes(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative byte length: " + length);
        }
        require(length);
        byte[] copy = new byte[length];
        System.arraycopy(buffer, position, copy, 0, length);
        position += length;
        return copy;
    }

    @NotNull String readUtf8(int maxBytes) {
        int length = readPositiveVarInt("string length");
        if (length > maxBytes) {
            throw new IllegalArgumentException(
                    "String is " + length + " bytes, above the " + maxBytes + " byte limit");
        }
        return new String(readBytes(length), StandardCharsets.UTF_8);
    }

    void requireFinished() {
        if (position != end) {
            throw new IllegalArgumentException((end - position) + " trailing byte(s) in payload");
        }
    }

    private void require(int count) {
        if (count > end - position) {
            throw new IllegalArgumentException(
                    "Payload truncated: needed " + count + " byte(s), " + (end - position) + " left");
        }
    }
}
