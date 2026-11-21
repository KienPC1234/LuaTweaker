package com.luatweaker.core.mod;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Record representing the metadata contained in a LuaMod's manifest.json file.
 *
 * <p>Dependency fields accept both the legacy plain-id form ({@code "other_mod"})
 * and rich objects ({@code {"id": "lib", "min_version": "1.0.0",
 * "max_version": "2.0.0", "optional": true}}). Platform condition blocks are
 * objects with optional {@code min_version}/{@code max_version} bounds:
 * <pre>{@code
 * "minecraft": {"min_version": "1.21", "max_version": "1.21.1"},
 * "luatweaker": {"min_version": "1.0.0"},
 * "mods": [{"id": "jei", "min_version": "19.0.0"}],
 * "integrations": {"mekanism": {"min_version": "10.7.0", "entry": "integrations/mekanism.lua"}}
 * }</pre>
 */
public record LuaModManifest(
        @NotNull String id,
        @NotNull String name,
        @NotNull String author,
        @NotNull String version,
        @NotNull String main,
        @NotNull String environment,
        @NotNull List<ModDependency> dependencies,
        @NotNull List<ModDependency> optionalDependencies,
        @NotNull List<ModDependency> modDependencies,
        @NotNull Map<String, ModIntegration> integrations,
        @Nullable VersionConstraint luatweaker,
        @Nullable VersionConstraint minecraft,
        @NotNull List<String> permissions,
        @Nullable String updateUrl
) {
    private static final Gson GSON = new Gson();

    public LuaModManifest {
        id = LuaModPathSecurity.validateModId(id);
        if (name == null || name.isBlank()) { name = id; }
        if (author == null) { author = "Unknown"; }
        if (version == null) { version = "1.0.0"; }
        main = LuaModPathSecurity.validateEntryPoint(main == null || main.isBlank() ? "main.lua" : main);
        if (environment == null || environment.isBlank()) { throw new IllegalArgumentException("LuaMod manifest requires non-empty 'environment' (must be 'client', 'server', or 'universal')"); }
        if (!"client".equals(environment) && !"server".equals(environment) && !"universal".equals(environment)) {
            throw new IllegalArgumentException("LuaMod manifest 'environment' must be 'client', 'server', or 'universal', found: " + environment);
        }
        if (dependencies == null) { dependencies = Collections.emptyList(); }
        if (optionalDependencies == null) { optionalDependencies = Collections.emptyList(); }
        if (modDependencies == null) { modDependencies = Collections.emptyList(); }
        if (integrations == null) { integrations = Collections.emptyMap(); }
        if (permissions == null) { permissions = Collections.emptyList(); }
        updateUrl = blankToNull(updateUrl);
    }

    /** @return the value, or {@code null} for blank input. */
    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Legacy constructor: plain-id dependency lists, no platform constraints or integrations. */
    public LuaModManifest(@NotNull String id,
                          @NotNull String name,
                          @NotNull String author,
                          @NotNull String version,
                          @NotNull String main,
                          @NotNull String environment,
                          @NotNull List<String> dependencies,
                          @NotNull List<String> optionalDependencies,
                          @NotNull List<String> permissions,
                          @Nullable String updateUrl) {
        this(id, name, author, version, main, environment,
                toRequired(dependencies), toOptional(optionalDependencies),
                Collections.emptyList(), Collections.emptyMap(), null, null, permissions, updateUrl);
    }

    private static @NotNull List<ModDependency> toRequired(@Nullable List<String> ids) {
        if (ids == null) { return Collections.emptyList(); }
        List<ModDependency> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (id != null && !id.isBlank()) { out.add(new ModDependency(id.trim(), null, null, false)); }
        }
        return out;
    }

    private static @NotNull List<ModDependency> toOptional(@Nullable List<String> ids) {
        if (ids == null) { return Collections.emptyList(); }
        List<ModDependency> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (id != null && !id.isBlank()) { out.add(new ModDependency(id.trim(), null, null, true)); }
        }
        return out;
    }

    public static @Nullable LuaModManifest parseJson(@NotNull String jsonString) {
        JsonObject json = parseObject(jsonString);
        if (json == null || !json.has("id")) {
            return null;
        }
        try {
            return parseManifest(json);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return null;
        }
    }

    private static @NotNull LuaModManifest parseManifest(@NotNull JsonObject json) {
        String id = stringOr(json, "id", null);
        if (id == null) {
            throw new IllegalArgumentException("LuaMod manifest requires an 'id'");
        }
        String name = stringOr(json, "name", id);
        String author = stringOr(json, "author", "Unknown");
        String version = stringOr(json, "version", "1.0.0");
        String main = stringOr(json, "main", "main.lua");

        List<ModDependency> deps = parseDependencyList(json, "dependencies", false);
        List<String> perms = parseStringList(json, "permissions");
        List<ModDependency> optionalDeps = parseDependencyList(json, "optional_dependencies", true);
        List<ModDependency> modDeps = parseDependencyList(json, "mods", false);

        VersionConstraint engineConstraint = parseConstraint(json, "luatweaker");
        VersionConstraint minecraftConstraint = parseConstraint(json, "minecraft");
        Map<String, ModIntegration> integrations = parseIntegrations(json);
        String updateUrl = blankToNull(stringOr(json, "update_url", null));

        String environment = stringOr(json, "environment", null);
        if (environment == null) {
            throw new IllegalArgumentException("LuaMod manifest requires an 'environment'");
        }
        return new LuaModManifest(id, name, author, version, main, environment,
                deps, optionalDeps, modDeps, integrations,
                engineConstraint, minecraftConstraint, perms, updateUrl);
    }

    /** @return the parsed object, or {@code null} for malformed JSON. */
    private static @Nullable JsonObject parseObject(@NotNull String jsonString) {
        try {
            return GSON.fromJson(jsonString, JsonObject.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    /** Reads a string member, falling back when absent or not a primitive. */
    private static @Nullable String stringOr(@NotNull JsonObject json, @NotNull String key,
            @Nullable String fallback) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return json.get(key).getAsString();
    }

    private static @NotNull List<ModDependency> parseDependencyList(@NotNull JsonObject json,
            @NotNull String key, boolean defaultOptional) {
        List<ModDependency> out = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            json.getAsJsonArray(key).forEach(elem -> addDep(out, ModDependency.parse(elem, defaultOptional)));
        }
        return out;
    }

    private static @NotNull List<String> parseStringList(@NotNull JsonObject json, @NotNull String key) {
        List<String> out = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            json.getAsJsonArray(key).forEach(elem -> {
                if (elem.isJsonPrimitive()) {
                    out.add(elem.getAsString());
                }
            });
        }
        return out;
    }

    private static @NotNull Map<String, ModIntegration> parseIntegrations(@NotNull JsonObject json) {
        Map<String, ModIntegration> integrations = Collections.synchronizedMap(new LinkedHashMap<>());
        if (json.has("integrations") && json.get("integrations").isJsonObject()) {
            for (Map.Entry<String, JsonElement> declared
                    : json.getAsJsonObject("integrations").entrySet()) {
                String targetId = declared.getKey() == null ? "" : declared.getKey().trim();
                if (targetId.isEmpty()) {
                    continue;
                }
                ModIntegration integration = ModIntegration.parse(declared.getValue());
                if (integration != null) {
                    integrations.put(targetId, integration);
                }
            }
        }
        return integrations;
    }

    private static void addDep(@NotNull List<ModDependency> out, @Nullable ModDependency dep) {
        if (dep != null) { out.add(dep); }
    }

    /**
     * Parses a platform condition block ({@code "luatweaker"} / {@code "minecraft"}).
     * Accepts either an object with {@code min_version}/{@code max_version} or a
     * single exact-version string. Returns {@code null} when absent.
     */
    private static @Nullable VersionConstraint parseConstraint(@NotNull JsonObject json, @NotNull String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) { return null; }
        JsonElement element = json.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String exact = element.getAsString().trim();
            if (exact.isEmpty()) { return null; }
            return new VersionConstraint(exact, exact);
        }
        if (!element.isJsonObject()) { return null; }
        JsonObject obj = element.getAsJsonObject();
        String min = boundOrNull(obj, "min_version");
        String max = boundOrNull(obj, "max_version");
        if (min == null && max == null) { return null; }
        return new VersionConstraint(min, max);
    }

    /** Reads an optional version bound, normalizing blanks to {@code null}. */
    private static @Nullable String boundOrNull(@NotNull JsonObject obj, @NotNull String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) { return null; }
        String value = obj.get(key).getAsString().trim();
        if (value.isEmpty()) { return null; }
        return value;
    }
}
