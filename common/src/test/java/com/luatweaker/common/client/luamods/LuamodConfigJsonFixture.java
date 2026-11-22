package com.luatweaker.common.client.luamods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Shared JSON fixture for the config editor tests. */
final class LuamodConfigJsonFixture {

    static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    static final String SAMPLE_JSON = """
            {
              "enabled": true,
              "detail_scale": 0.045,
              "threshold": 42,
              "tag": "custom",
              "nested": {
                "active": false,
                "factor": 1.5
              },
              "items": ["apple", "bread"]
            }
            """;

    private LuamodConfigJsonFixture() {
    }

    static JsonObject sample() {
        return JsonParser.parseString(SAMPLE_JSON).getAsJsonObject();
    }
}
