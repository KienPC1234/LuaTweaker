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
        @NotNull String environment,
        @NotNull List<String> dependencies,
        @NotNull List<String> permissions,
        @Nullable String updateUrl
) {
    private static final Gson GSON = new Gson();

    public LuaModManifest {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("LuaMod manifest requires non-empty 'id'");
        if (name == null || name.isBlank()) name = id;
        if (author == null) author = "Unknown";
        if (version == null) version = "1.0.0";
        if (main == null || main.isBlank()) main = "main.lua";
        if (environment == null || environment.isBlank()) throw new IllegalArgumentException("LuaMod manifest requires non-empty 'environment' (must be 'client', 'server', or 'universal')");
        if (!environment.equals("client") && !environment.equals("server") && !environment.equals("universal")) {
            throw new IllegalArgumentException("LuaMod manifest 'environment' must be 'client', 'server', or 'universal', found: " + environment);
        }
        if (dependencies == null) dependencies = Collections.emptyList();
        if (permissions == null) permissions = Collections.emptyList();
        if (updateUrl != null && updateUrl.isBlank()) updateUrl = null;
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

            String updateUrl = null;
            if (json.has("update_url") && json.get("update_url").isJsonPrimitive()) {
                updateUrl = json.get("update_url").getAsString().trim();
                if (updateUrl.isEmpty()) updateUrl = null;
            }

            if (!json.has("environment")) {
                throw new IllegalArgumentException("Missing 'environment' field");
            }
            String environment = json.get("environment").getAsString();

            return new LuaModManifest(id, name, author, version, main, environment, deps, perms, updateUrl);
        } catch (Exception e) {
            return null;
        }
    }
}
