package com.luatweaker.core.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.api.json.IJsonService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * High-performance, sandboxed JSON encoder and decoder for LuaTweaker.
 *
 * <p>The two conversion directions live in {@link JsonToLuaDecoder} and
 * {@link LuaToJsonEncoder}; this facade owns the Gson formatters and the public
 * {@link IJsonService} contract.</p>
 */
public final class JsonServiceImpl implements IJsonService {

    private static final Gson COMPACT_GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ILuaEngine engine;

    public JsonServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public @NotNull ILuaValue decode(String json) {
        return decodeJson(this.engine, json);
    }

    @Override
    public boolean isValid(@Nullable String json) {
        return isValidJson(json);
    }

    @Override
    public @NotNull String encode(ILuaValue value, boolean pretty) {
        return encodeJson(value, pretty);
    }

    /**
     * Decodes a raw JSON string into an {@link ILuaValue} hierarchy.
     *
     * @param engine target Lua engine for allocating tables and primitives
     * @param json   raw JSON string
     * @return parsed Lua value (table, string, number, boolean, or nil)
     * @throws IllegalArgumentException if the input is empty or contains syntax errors
     */
    public static @NotNull ILuaValue decodeJson(@NotNull ILuaEngine engine, @Nullable String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Json.Decode: JSON input must not be null or empty");
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return JsonToLuaDecoder.jsonToLua(engine, element);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Json.Decode failed: " + e.getMessage(), e);
        }
    }

    /**
     * Predicate version of {@link #decodeJson}: true when the string parses as JSON.
     *
     * @param json raw JSON string
     * @return true for any valid JSON document, false for null, blank or malformed input
     */
    public static boolean isValidJson(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonParser.parseString(json);
            return true;
        } catch (JsonSyntaxException | IllegalStateException e) {
            return false;
        }
    }

    /**
     * Encodes an {@link ILuaValue} into a JSON string.
     *
     * @param value  root Lua value
     * @param pretty whether to format with 2-space indentation
     * @return JSON string
     * @throws IllegalArgumentException if a circular table reference is detected
     */
    public static @NotNull String encodeJson(@Nullable ILuaValue value, boolean pretty) {
        Gson gson = pretty ? PRETTY_GSON : COMPACT_GSON;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        JsonElement element = LuaToJsonEncoder.luaToJson(value, visited);
        return gson.toJson(element);
    }
}
