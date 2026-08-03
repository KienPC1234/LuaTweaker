package com.luatweaker.command;

import com.luatweaker.api.command.CoreCommandNames;
import com.luatweaker.api.command.ICommandService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side command registry shared across ALL Lua engines (startup, reloads).
 *
 * <p>Like the event bus, the registry map is static and survives engine reloads:
 * {@code /lt reload} builds a new engine and re-runs {@code main.lua}, which
 * re-registers every command with the new engine's handler. The shared map is
 * cleared at the start of every load cycle (see {@link #clear()} call sites in
 * the platform bootstrap) so commands of disabled/removed mods do not linger.</p>
 *
 * <p>Conflict policy (clear registration, no silent shadowing):</p>
 * <ul>
 *   <li>Reserved names (core {@code /lt} tree + vanilla commands) are rejected.</li>
 *   <li>A mod re-registering its own command REPLACES the previous definition (reload-safe).</li>
 *   <li>A different mod claiming an existing name, path or alias is rejected loudly.</li>
 *   <li>Collisions with vanilla commands are caught at Brigadier build time.</li>
 * </ul>
 */
public class CommandServiceImpl implements ICommandService {

    /**
     * Every registered path (primary names AND aliases) maps to its definition.
     * Aliases resolve like primary names, so a foreign mod can never claim a
     * path that is already an alias of another mod.
     */
    private static final Map<String, CommandDefinition> COMMANDS = new ConcurrentHashMap<>();

    private static final int NAME_MAX_LENGTH = 32;
    private static final int PATH_MAX_DEPTH = 4;
    private static final int PATH_MAX_LENGTH = 64;
    private static final int PERMISSION_MIN = 0;
    private static final int PERMISSION_MAX = 4;

    private final ILuaEngine engine;

    public CommandServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    /** @return the current definition for a command path (primary name or alias), or null. */
    public static @Nullable CommandDefinition get(@NotNull String path) {
        return COMMANDS.get(path);
    }

    /** @return all registered Lua commands (primary names only) sorted by path. */
    public static @NotNull List<CommandDefinition> getSnapshot() {
        Map<String, CommandDefinition> byName = new java.util.LinkedHashMap<>();
        for (CommandDefinition def : COMMANDS.values()) {
            byName.putIfAbsent(def.name(), def);
        }
        List<CommandDefinition> list = new ArrayList<>(byName.values());
        list.sort(Comparator.comparing(CommandDefinition::name));
        return List.copyOf(list);
    }

    /** Removes every Lua command registration (called at the start of each load cycle). */
    public static void clear() {
        COMMANDS.clear();
    }

    @Override
    public boolean register(@NotNull String name, @NotNull ILuaTable definition) {
        String modId = resolveCurrentModId();
        if (modId == null) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Commands:Register('" + name + "') rejected: no owning mod context (mod global missing). Register commands from main.lua or mod.OnEnable().");
            return false;
        }

        if (!isValidPath(name)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Commands:Register('" + name + "') rejected by mod '" + modId + "': invalid path (segments of lowercase letters, digits, underscores or colons, max "
                            + NAME_MAX_LENGTH + " chars each, max " + PATH_MAX_DEPTH + " levels, max " + PATH_MAX_LENGTH + " chars total).");
            return false;
        }

        if (isReservedPath(name)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Commands:Register('" + name + "') rejected by mod '" + modId + "': name is reserved (built-in /lt tree or vanilla command).");
            return false;
        }

        CommandDefinition parsed = parseDefinition(engine, modId, name, definition);
        if (parsed == null) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Commands:Register('" + name + "') rejected by mod '" + modId + "': invalid definition table.");
            return false;
        }

        // Check every claimed path (primary + aliases) against foreign owners.
        for (String path : allPaths(parsed)) {
            CommandDefinition existing = COMMANDS.get(path);
            if (existing != null && !existing.modId().equals(modId)) {
                LuaTweakerLog.get().error(LogStage.COMMAND,
                        "Commands:Register('" + name + "') rejected by mod '" + modId + "': path '/" + path
                                + "' is already registered by mod '" + existing.modId()
                                + "'. Use a mod-specific name to avoid conflicts.");
                return false;
            }
        }

        for (String path : allPaths(parsed)) {
            COMMANDS.put(path, parsed);
        }
        LuaTweakerLog.get().info(LogStage.COMMAND,
                "Commands:Register '/" + name + "' by mod '" + modId + "' (op:" + parsed.permissionLevel()
                        + ", console:" + parsed.consoleAllowed()
                        + (parsed.aliases().isEmpty() ? "" : ", aliases: " + parsed.aliases())
                        + (parsed.suggestionProvider() == null ? "" : ", suggestions: yes")
                        + ")");
        return true;
    }

    @Override
    public boolean unregister(@NotNull String name) {
        String modId = resolveCurrentModId();
        CommandDefinition existing = COMMANDS.get(name);
        if (existing == null) {
            LuaTweakerLog.get().warn(LogStage.COMMAND,
                    "Commands:Unregister('" + name + "') ignored: not registered.");
            return false;
        }
        if (modId == null || !existing.modId().equals(modId)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Commands:Unregister('" + name + "') rejected: owned by mod '" + existing.modId()
                            + "', caller '" + (modId == null ? "unknown" : modId) + "'.");
            return false;
        }
        // Remove the primary name and every alias of the definition.
        COMMANDS.entrySet().removeIf(entry -> entry.getValue().name().equals(name));
        LuaTweakerLog.get().info(LogStage.COMMAND,
                "Commands:Unregister '" + name + "' by mod '" + modId + "'.");
        return true;
    }

    /** All paths (primary + aliases) a definition claims, in a stable order. */
    private static List<String> allPaths(@NotNull CommandDefinition def) {
        List<String> paths = new ArrayList<>();
        paths.add(def.name());
        paths.addAll(def.aliases());
        return paths;
    }

    /**
     * Resolves the id of the Lua mod currently being loaded from the engine's
     * global {@code mod} table. Only valid while a mod's main.lua / OnEnable
     * runs; commands must be registered during loading.
     */
    private @Nullable String resolveCurrentModId() {
        try {
            ILuaValue modVal = engine.getGlobalEnvironment().rawget("mod");
            if (modVal == null || modVal.isNil()) {
                modVal = engine.getGlobalEnvironment().rawget("Mod");
            }
            if (modVal != null && !modVal.isNil() && modVal.isTable()) {
                ILuaValue idVal = modVal.asTable().rawget("ID");
                if (idVal != null && !idVal.isNil()) {
                    return idVal.asString();
                }
            }
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Commands:Register failed while resolving owning mod id: " + e.getMessage());
        }
        return null;
    }

    /** Validates a slash-separated command path: {@code [a-z0-9_:]+} segments, max depth 4. */
    private static boolean isValidPath(@NotNull String path) {
        if (path.isEmpty() || path.length() > PATH_MAX_LENGTH) return false;
        // Java's String.split drops trailing empty segments, so reject them explicitly.
        if (path.startsWith("/") || path.endsWith("/") || path.contains("//")) return false;
        String[] segments = path.split("/");
        if (segments.length == 0 || segments.length > PATH_MAX_DEPTH) return false;
        for (String segment : segments) {
            if (segment.isEmpty() || segment.length() > NAME_MAX_LENGTH) return false;
            for (int i = 0; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != ':') {
                    return false;
                }
            }
        }
        return true;
    }

    /** Reserved: core /lt roots as first segment, or the whole path matching a reserved name. */
    private static boolean isReservedPath(@NotNull String path) {
        String firstSegment = path.split("/")[0];
        if ("lt".equals(firstSegment) || "luatweaker".equals(firstSegment)) return true;
        return CoreCommandNames.RESERVED.contains(path);
    }

    /** Reads a definition field accepting PascalCase and camelCase aliases. */
    private static @Nullable Object readField(@NotNull ILuaTable definition, @NotNull String pascalKey, @NotNull String camelKey) {
        ILuaValue value = definition.rawget(pascalKey);
        if (value == null || value.isNil()) {
            value = definition.rawget(camelKey);
        }
        if (value == null || value.isNil()) return null;
        return value.toJavaObject();
    }

    private static @Nullable CommandDefinition parseDefinition(
            @NotNull ILuaEngine engine,
            @NotNull String modId,
            @NotNull String name,
            @NotNull ILuaTable definition
    ) {
        try {
            ILuaValue handlerVal = definition.rawget("Handler");
            if (handlerVal == null || handlerVal.isNil()) {
                handlerVal = definition.rawget("handler");
            }
            if (handlerVal == null || !handlerVal.isFunction()) {
                LuaTweakerLog.get().error(LogStage.COMMAND,
                        "Commands:Register('" + name + "') by mod '" + modId + "': missing required 'Handler' function.");
                return null;
            }

            String description = name;
            Object descObj = readField(definition, "Description", "description");
            if (descObj != null) {
                if (!(descObj instanceof String desc)) {
                    LuaTweakerLog.get().error(LogStage.COMMAND,
                            "Commands:Register('" + name + "') by mod '" + modId + "': 'Description' must be a string.");
                    return null;
                }
                description = desc;
            }

            int permissionLevel = 2;
            Object permObj = readField(definition, "PermissionLevel", "permissionLevel");
            if (permObj != null) {
                if (!(permObj instanceof Number permNum)) {
                    LuaTweakerLog.get().error(LogStage.COMMAND,
                            "Commands:Register('" + name + "') by mod '" + modId + "': 'PermissionLevel' must be a number.");
                    return null;
                }
                permissionLevel = permNum.intValue();
                if (permissionLevel < PERMISSION_MIN || permissionLevel > PERMISSION_MAX) {
                    LuaTweakerLog.get().error(LogStage.COMMAND,
                            "Commands:Register('" + name + "') by mod '" + modId + "': 'PermissionLevel' must be between " + PERMISSION_MIN + " and " + PERMISSION_MAX + ".");
                    return null;
                }
            }

            boolean consoleAllowed = true;
            Object consoleObj = readField(definition, "ConsoleAllowed", "consoleAllowed");
            if (consoleObj != null) {
                if (!(consoleObj instanceof Boolean console)) {
                    LuaTweakerLog.get().error(LogStage.COMMAND,
                            "Commands:Register('" + name + "') by mod '" + modId + "': 'ConsoleAllowed' must be a boolean.");
                    return null;
                }
                consoleAllowed = console;
            }

            String usage = "";
            Object usageObj = readField(definition, "Usage", "usage");
            if (usageObj != null) {
                if (!(usageObj instanceof String usageStr)) {
                    LuaTweakerLog.get().error(LogStage.COMMAND,
                            "Commands:Register('" + name + "') by mod '" + modId + "': 'Usage' must be a string.");
                    return null;
                }
                usage = usageStr;
            }

            List<String> aliases = parseAliases(modId, name, definition);
            if (aliases == null) return null;

            ILuaValue suggestionProvider = parseSuggestions(definition);

            return new CommandDefinition(modId, name, description, permissionLevel, consoleAllowed, usage,
                    aliases, suggestionProvider, engine, handlerVal);
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Commands:Register('" + name + "') by mod '" + modId + "' failed to parse definition: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses the optional {@code Aliases} table: list of valid paths, deduplicated,
     * never equal to the primary name. Returns null (rejects) on malformed input.
     */
    private static @Nullable List<String> parseAliases(
            @NotNull String modId,
            @NotNull String name,
            @NotNull ILuaTable definition
    ) {
        ILuaValue aliasesVal = definition.rawget("Aliases");
        if (aliasesVal == null || aliasesVal.isNil()) {
            aliasesVal = definition.rawget("aliases");
        }
        if (aliasesVal == null || aliasesVal.isNil()) return List.of();
        if (!aliasesVal.isTable()) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Commands:Register('" + name + "') by mod '" + modId + "': 'Aliases' must be a table of strings.");
            return null;
        }

        Set<String> aliases = new LinkedHashSet<>();
        ILuaTable aliasesTable = aliasesVal.asTable();
        int count = aliasesTable.length();
        if (count == 0) return List.of();
        for (int i = 1; i <= count; i++) {
            Object entry = aliasesTable.rawget(i).toJavaObject();
            if (!(entry instanceof String aliasPath) || !isValidPath(aliasPath)) {
                LuaTweakerLog.get().error(LogStage.COMMAND,
                        "Commands:Register('" + name + "') by mod '" + modId + "': alias #" + i + " is not a valid command path.");
                return null;
            }
            if (isReservedPath(aliasPath)) {
                LuaTweakerLog.get().error(LogStage.COMMAND,
                        "Commands:Register('" + name + "') by mod '" + modId + "': alias '" + aliasPath + "' is reserved.");
                return null;
            }
            if (!aliasPath.equals(name)) {
                aliases.add(aliasPath);
            }
        }
        return List.copyOf(aliases);
    }

    /**
     * Parses the optional {@code Suggestions} field: a Lua function
     * {@code (sender, args) -> table of strings}, or a static table of strings.
     * Returns null when absent; throws {@link IllegalArgumentException} with a
     * descriptive message when the field is present but invalid (the caller's
     * outer catch turns that into a rejected registration).
     */
    private static @Nullable ILuaValue parseSuggestions(@NotNull ILuaTable definition) {
        ILuaValue suggestionsVal = definition.rawget("Suggestions");
        if (suggestionsVal == null || suggestionsVal.isNil()) {
            suggestionsVal = definition.rawget("suggestions");
        }
        if (suggestionsVal == null || suggestionsVal.isNil()) return null;
        if (suggestionsVal.isFunction()) return suggestionsVal;
        if (suggestionsVal.isTable()) {
            ILuaTable suggestionsTable = suggestionsVal.asTable();
            int count = suggestionsTable.length();
            for (int i = 1; i <= count; i++) {
                Object entry = suggestionsTable.rawget(i).toJavaObject();
                if (!(entry instanceof String)) {
                    throw new IllegalArgumentException("'Suggestions' table entries must be strings.");
                }
            }
            return suggestionsVal;
        }
        throw new IllegalArgumentException("'Suggestions' must be a function or a table of strings.");
    }
}
