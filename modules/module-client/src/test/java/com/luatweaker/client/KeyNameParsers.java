package com.luatweaker.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Helpers for the key-name table tests: parse a batch of names into a comparable map, or collect
 * the rejection message of every name that must fail.
 */
final class KeyNameParsers {

    private KeyNameParsers() {
    }

    static Map<String, Object> parsed(List<String> inputs, Function<String, Object> parser) {
        Map<String, Object> codes = new ConcurrentHashMap<>();
        for (String input : inputs) {
            codes.put(input, parser.apply(input));
        }
        return codes;
    }

    static List<String> rejectionMessages(List<String> inputs, Function<String, Object> parser) {
        List<String> messages = new ArrayList<>();
        for (String input : inputs) {
            messages.add(assertThrows(IllegalArgumentException.class, () -> parser.apply(input),
                    "'" + input + "' must be rejected instead of silently misbinding").getMessage());
        }
        return messages;
    }
}
