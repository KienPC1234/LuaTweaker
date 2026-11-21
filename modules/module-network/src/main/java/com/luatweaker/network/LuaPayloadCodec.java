package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Binary wire format for every Lua value that crosses the network.
 *
 * <p>Replaces the old "Lua table -> Gson JSON string -> UTF-8 bytes -> JSON parse -> Lua
 * table" path with one compact tagged stream. JSON repeated every key on every message and
 * allocated a {@code String} plus a full {@code JsonElement} tree per packet; this codec
 * writes the values directly and decodes straight into Lua tables.</p>
 *
 * <p>Round-trip guarantees:</p>
 * <ul>
 *   <li>dense tables ({@code 1..n}) stay Lua arrays ({@code #t} and {@code ipairs} work),</li>
 *   <li>string-keyed tables stay maps,</li>
 *   <li>integers keep their integer tag, so large ids do not drift,</li>
 *   <li>keys are stringified exactly like the previous JSON format did.</li>
 * </ul>
 *
 * <p>Everything that cannot cross the wire (functions, userdata, threads) fails loudly.
 * Encoding lives in {@link LuaPayloadEncoder}, decoding in {@link LuaPayloadDecoder}.</p>
 */
public final class LuaPayloadCodec {

    /** Bumped whenever the byte layout changes; a mismatched version is rejected, never guessed. */
    public static final byte VERSION = 1;

    /** Deepest table/array nesting accepted in either direction. */
    public static final int MAX_NESTING = 32;
    /** Largest number of arguments or collection entries in one message. */
    public static final int MAX_ELEMENTS = 65_536;
    /** Largest single string, in UTF-8 bytes. */
    public static final int MAX_STRING_BYTES = 1_048_576;
    /** Largest decoded message. Chunking splits anything above one frame. */
    public static final int MAX_MESSAGE_BYTES = 4_194_304;

    static final byte TAG_NIL = 0;
    static final byte TAG_FALSE = 1;
    static final byte TAG_TRUE = 2;
    static final byte TAG_INT = 3;
    static final byte TAG_DOUBLE = 4;
    static final byte TAG_STRING = 5;
    static final byte TAG_ARRAY = 6;
    static final byte TAG_MAP = 7;

    private LuaPayloadCodec() {
    }

    /**
     * Serializes a Lua argument list.
     *
     * @throws IllegalArgumentException when a value cannot cross the wire, the payload is
     *                                  nested too deeply, or it exceeds {@link #MAX_MESSAGE_BYTES}
     */
    public static byte @NotNull [] encodeArgs(ILuaValue @Nullable ... args) {
        ILuaValue[] values = args == null ? new ILuaValue[0] : args;
        BinaryWriter out = new BinaryWriter();
        out.writeByte(VERSION);
        out.writeVarInt(values.length);
        LuaPayloadEncoder.writeAll(out, values);
        return out.toByteArray();
    }

    /**
     * Decodes a payload produced by {@link #encodeArgs(ILuaValue[])}.
     *
     * @throws IllegalArgumentException on truncation, trailing bytes, an unknown version or
     *                                  a malformed length
     */
    public static ILuaValue @NotNull [] decodeArgs(@NotNull ILuaEngine engine, byte @NotNull [] data) {
        BinaryReader in = new BinaryReader(data, 0, data.length);
        int version = in.readByte();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported network payload version " + version);
        }
        int count = in.readPositiveVarInt("argument count");
        if (count > MAX_ELEMENTS) {
            throw new IllegalArgumentException("Argument count " + count + " exceeds " + MAX_ELEMENTS);
        }
        if (count > in.remaining()) {
            throw new IllegalArgumentException("Argument count " + count + " exceeds the payload size");
        }
        ILuaValue[] values = new ILuaValue[count];
        LuaPayloadDecoder.readAll(engine, in, values, 0);
        in.requireFinished();
        return values;
    }

    /**
     * Decodes a payload, treating {@code null} and empty input as "no arguments".
     * Empty payloads are the wire form of calls without arguments.
     */
    public static ILuaValue @NotNull [] decodeArgsOrEmpty(@NotNull ILuaEngine engine,
                                                          byte @Nullable [] data) {
        if (data == null || data.length == 0) {
            return new ILuaValue[0];
        }
        return decodeArgs(engine, data);
    }
}
