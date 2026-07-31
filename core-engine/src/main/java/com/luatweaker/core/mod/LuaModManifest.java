package com.luatweaker.core.mod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Record representing the metadata contained in a LuaMod's manifest.json file.
 */
public record LuaModManifest(
        @NotNull String id,
        @NotNull String name,
        @NotNull String author,
        @NotNull String version,
        @NotNull String main,
        @NotNull List<String> dependencies,
        @NotNull List<String> permissions
) {
    private static final Gson GSON = new Gson();

    public LuaModManifest {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("LuaMod manifest requires non-empty 'id'");
        if (name == null || name.isBlank()) name = id;
        if (author == null) author = "Unknown";
        if (version == null) version = "1.0.0";
        if (main == null || main.isBlank()) main = "main.lua";
        if (dependencies == null) dependencies = Collections.emptyList();
        if (permissions == null) permissions = Collections.emptyList();
    }

    public static @Nullable LuaModManifest parseJson(@NotNull String jsonString) {
        try {
            JsonObject json = GSON.fromJson(jsonString, JsonObject.class);
            if (json == null || !json.has("id")) return null;

            String id = json.get("id").getAsString();
            String name = json.has("name") ? json.get("name").getAsString() : id;
            String author = json.has("author") ? json.get("author").getAsString() : "Unknown";
            String version = json.has("version") ? json.get("version").getAsString() : "1.0.0";
            String main = json.has("main") ? json.get("main").getAsString() : "main.lua";

            List<String> deps = new ArrayList<>();
            if (json.has("dependencies") && json.get("dependencies").isJsonArray()) {
                json.getAsJsonArray("dependencies").forEach(elem -> deps.add(elem.getAsString()));
            }

            List<String> perms = new ArrayList<>();
            if (json.has("permissions") && json.get("permissions").isJsonArray()) {
                json.getAsJsonArray("permissions").forEach(elem -> perms.add(elem.getAsString()));
            }

            return new LuaModManifest(id, name, author, version, main, deps, perms);
        } catch (Exception e) {
            return null;
        }
    }
}
