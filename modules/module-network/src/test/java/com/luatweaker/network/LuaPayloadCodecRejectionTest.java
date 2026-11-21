package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Encoding and decoding limits: unsupported values, nesting depth, string size, element count. */
class LuaPayloadCodecRejectionTest {

    private static final String FUNCTION_GLOBAL = "_payloadFn";

    LuavaEngine engine;

    @BeforeEach
    void setup() {
        engine = new LuavaEngine(false);
    }

    @Test
    void functionsAreRejected() {
        engine.executeString("_payloadFn = function() end", "codec_fn");
        ILuaValue function = engine.getGlobalEnvironment().rawget(FUNCTION_GLOBAL);

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.encodeArgs(function),
            "a function argument must be rejected instead of silently dropped");
    }

    @Test
    void functionErrorListsAllowedTypes() {
        engine.executeString("_payloadFn = function() end", "codec_fn");
        ILuaValue function = engine.getGlobalEnvironment().rawget(FUNCTION_GLOBAL);

        String message = NetworkTestFixture.errorMessage(() -> LuaPayloadCodec.encodeArgs(function));

        assertTrue(message.contains("only support"),
            "the rejection must explain the allowed types, got: " + message);
    }

    @Test
    void overlyDeepEncodingIsRejected() {
        ILuaTable root = engine.createTable();
        ILuaTable cursor = root;
        for (int level = 0; level < LuaPayloadCodec.MAX_NESTING + 5; level++) {
            ILuaTable next = engine.createTable();
            cursor.rawset(1, next);
            cursor = next;
        }

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.encodeArgs(root),
            "encoding past the nesting limit must be rejected");
    }

    @Test
    void overlyDeepDecodingIsRejected() {
        byte[] payload = LuaPayloadFixtures.nestingPayload();

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.decodeArgs(engine, payload),
            "decoding past the nesting limit must fail before the stack overflows");
    }

    @Test
    void nestingErrorNamesDepth() {
        byte[] payload = LuaPayloadFixtures.nestingPayload();

        String message = NetworkTestFixture.errorMessage(() -> LuaPayloadCodec.decodeArgs(engine, payload));

        assertTrue(message.contains("nesting"),
            "the nesting rejection must name the nesting limit, got: " + message);
    }

    @Test
    void oversizedStringIsRejected() {
        String big = "x".repeat(LuaPayloadCodec.MAX_STRING_BYTES + 1);

        assertThrows(IllegalArgumentException.class,
            () -> LuaPayloadCodec.encodeArgs(engine.wrapString(big)),
            "a string above the byte cap must be rejected");
    }

    @Test
    void oversizedStringErrorNamesBytes() {
        String big = "x".repeat(LuaPayloadCodec.MAX_STRING_BYTES + 1);

        String message = NetworkTestFixture.errorMessage(() -> LuaPayloadCodec.encodeArgs(engine.wrapString(big)));

        assertTrue(message.contains("bytes"),
            "the string rejection must state the byte limit, got: " + message);
    }

    @Test
    void absentElementCountIsRejected() {
        byte[] payload = LuaPayloadFixtures.absurdCountPayload();

        assertThrows(IllegalArgumentException.class, () -> LuaPayloadCodec.decodeArgs(engine, payload),
            "an absurd declared element count must be rejected");
    }

    @Test
    void absurdElementCountErrorNamesLimit() {
        byte[] payload = LuaPayloadFixtures.absurdCountPayload();

        String message = NetworkTestFixture.errorMessage(() -> LuaPayloadCodec.decodeArgs(engine, payload));

        assertTrue(message.contains("exceeds"),
            "the element-count rejection must state that the limit is exceeded, got: " + message);
    }
}
