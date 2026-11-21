package com.luatweaker.api.json;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

/**
 * Low-level JSON service for encoding and decoding JSON payloads in Lua scripts.
 */
@LuaDoc(description = "Low-level JSON encoding and decoding service.")
public interface IJsonService {

    @LuaDoc(
            description = "Decodes a JSON string into Lua values (tables, primitives, lists). Throws an error on malformed JSON.",
            params = {"json: string"},
            returnType = "any"
    )
    ILuaValue decode(String json);

    @LuaDoc(
            description = "Encodes a Lua value or table into a JSON string. Set pretty to true for 2-space indented output.",
            params = {"value: any", "pretty: boolean"},
            returnType = "string"
    )
    String encode(ILuaValue value, @LuaDefault("false") boolean pretty);

    @LuaDoc(
            description = "Returns true when the string parses as JSON (object, array, string, number, boolean or null). "
                    + "Never throws: null, blank and malformed inputs yield false. "
                    + "A cheap pre-check only — still use pcall(Decode) when you need the value (TOCTOU).",
            params = {"json: string"},
            returnType = "boolean"
    )
    boolean isValid(@Nullable String json);

    @LuaDoc(
            description = "Alias for decode(json).",
            params = {"json: string"},
            returnType = "any"
    )
    default ILuaValue parse(String json) {
        return decode(json);
    }

    @LuaDoc(
            description = "Alias for encode(value, pretty).",
            params = {"value: any", "pretty: boolean"},
            returnType = "string"
    )
    default String stringify(ILuaValue value, @LuaDefault("false") boolean pretty) {
        return encode(value, pretty);
    }
}
