package com.luatweaker.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * JSON wire format shared by RemoteEvents, RemoteFunctions and their responses.
 *
 * <p>Lua arrays stay JSON arrays so {@code #t} and {@code ipairs} keep working on the
 * receiver; string-keyed tables become JSON objects. Values that cannot cross the wire
 * (functions, userdata, threads) are rejected loudly instead of being sent as
 * {@code "table: 0x..."}.</p>
 */
public final class NetworkPayloadCodec {

    /** Matches the event payload codec bound: {@code ByteBufCodecs.STRING_UTF8}. */
    public static final int MAX_PAYLOAD_CHARS = 32_767;
    /** UTF-8 can use up to three bytes per char in the codec's bound. */
    public static final int MAX_PAYLOAD_BYTES = MAX_PAYLOAD_CHARS * 3;
    /** Deepest table/array nesting accepted in either direction. */
    public static final int MAX_NESTING = 32;

    private static final Gson GSON = new Gson();

    private NetworkPayloadCodec() {
    }

    /**
     * Serializes a Lua vararg list into a JSON array.
     *
     * @throws IllegalArgumentException when a value cannot round-trip or the payload is oversized
     */
    public static @NotNull String encodeArgs(@NotNull ILuaValue[] args) {
        JsonArray array = new JsonArray();
        for (ILuaValue value : args) {
            array.add(encode(value, 0));
        }
        String json = GSON.toJson(array);
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        // The packet codec rejects oversized strings on the Netty thread, which tears the
        // connection down instead of raising a Lua error. Enforce the bound before queuing.
        if (json.length() > MAX_PAYLOAD_CHARS || bytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Network payload is too large: " + json.length()
                    + " chars / " + bytes + " bytes (limit " + MAX_PAYLOAD_CHARS + " chars, "
                    + MAX_PAYLOAD_BYTES + " bytes)");
        }
        return json;
    }

    /**
     * Decodes a JSON argument array into Lua values.
     *
     * @throws IllegalArgumentException on malformed JSON, excessive nesting or an empty payload
     */
    public static ILuaValue[] decodeArgs(@NotNull ILuaEngine engine, @NotNull String json) {
        if (json.isBlank()) {
            return new ILuaValue[0];
        }
        assertBracketDepth(json);
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Malformed network payload: " + e.getMessage(), e);
        }
        if (!root.isJsonArray()) {
            throw new IllegalArgumentException("Network payload must be a JSON array, found: " + json);
        }
        JsonArray array = root.getAsJsonArray();
        ILuaValue[] result = new ILuaValue[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = decode(engine, array.get(i), 0);
        }
        return result;
    }

    private static JsonElement encode(ILuaValue value, int depth) {
        if (value == null || value.isNil()) {
            return JsonNull.INSTANCE;
        }
        if (depth > MAX_NESTING) {
            throw new IllegalArgumentException(
                    "Network payload nesting is too deep (max " + MAX_NESTING + " levels)");
        }
        if (value.isTable()) {
            ILuaTable table = value.asTable();
            int length = table.length();
            Map<ILuaValue, ILuaValue> entries = table.asMap();
            boolean isArray = length > 0 && entries.size() == length;
            if (isArray) {
                for (ILuaValue key : entries.keySet()) {
                    if (!key.isNumber() || key.asInt() < 1 || key.asInt() > length) {
                        isArray = false;
                        break;
                    }
                }
            }
            if (isArray) {
                JsonArray array = new JsonArray();
                for (int i = 1; i <= length; i++) {
                    array.add(encode(table.rawget(i), depth + 1));
                }
                return array;
            }
            JsonObject object = new JsonObject();
            for (Map.Entry<ILuaValue, ILuaValue> entry : entries.entrySet()) {
                object.add(entry.getKey().asString(), encode(entry.getValue(), depth + 1));
            }
            return object;
        }
        Object raw = value.toJavaObject();
        if (raw instanceof String s) {
            return new JsonPrimitive(s);
        }
        if (raw instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (raw instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        throw new IllegalArgumentException(
                "Network payloads only support booleans, numbers, strings and tables of those; got "
                        + value.getClass().getSimpleName());
    }

    private static ILuaValue decode(ILuaEngine engine, JsonElement element, int depth) {
        if (element == null || element.isJsonNull()) {
            return engine.nilValue();
        }
        if (depth > MAX_NESTING) {
            throw new IllegalArgumentException(
                    "Network payload nesting is too deep (max " + MAX_NESTING + " levels)");
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return engine.wrapBoolean(primitive.getAsBoolean());
            }
            if (primitive.isNumber()) {
                return engine.wrapNumber(primitive.getAsDouble());
            }
            return engine.wrapString(primitive.getAsString());
        }
        if (element.isJsonObject()) {
            ILuaTable table = engine.createTable();
            JsonObject object = element.getAsJsonObject();
            for (String key : object.keySet()) {
                table.rawset(key, decode(engine, object.get(key), depth + 1));
            }
            return table;
        }
        JsonArray array = element.getAsJsonArray();
        ILuaTable table = engine.createTable();
        for (int i = 0; i < array.size(); i++) {
            table.rawset(i + 1, decode(engine, array.get(i), depth + 1));
        }
        return table;
    }

    /**
     * Gson parses recursively, so a hostile 32k payload of {@code [[[[...} would blow the
     * stack before {@link #decode} can reject it. Reject it from the raw text first.
     */
    private static void assertBracketDepth(String json) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                depth++;
                if (depth > MAX_NESTING) {
                    throw new IllegalArgumentException(
                            "Network payload nesting is too deep (max " + MAX_NESTING + " levels)");
                }
            } else if (c == ']' || c == '}') {
                depth--;
            }
        }
    }
}
