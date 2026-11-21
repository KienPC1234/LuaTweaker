package com.luatweaker.command;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.luatweaker.command.CommandDefinitionParser.REGISTER_PREFIX;
import static com.luatweaker.command.CommandDefinitionParser.REJECTED_BY_MOD_INFIX;

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

    private record Owner(ILuaEngine engine, ILuaValue table, String modId) {}

    /**
     * Owner of each registered path. On the same engine, ownership is checked by table identity,
     * so a script that overwrites the {@code mod} global with {@code {ID="victim"}} cannot take
     * over another mod's command. A new engine (reload) is matched by mod id instead, which keeps
     * replace-on-reload working.
     */
    private static final Map<String, Owner> OWNERS = new ConcurrentHashMap<>();

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
        Map<String, CommandDefinition> byName = new ConcurrentHashMap<>();
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
        OWNERS.clear();
    }

    /** The live {@code mod}/{@code Mod} table object, used for non-spoofable ownership checks. */
    private @Nullable ILuaValue currentModTable() {
        ILuaValue value = engine.getGlobalEnvironment().rawget("mod");
        if (value == null || value.isNil()) {
            value = engine.getGlobalEnvironment().rawget("Mod");
        }
        return value != null && !value.isNil() && value.isTable() ? value : null;
    }

    private boolean sameOwner(@NotNull String path, @Nullable ILuaValue callerTable,
                              @Nullable String callerModId) {
        if (callerTable == null) {
            return false;
        }
        Owner owner = OWNERS.get(path);
        if (owner == null) {
            return false;
        }
        if (owner.engine().equals(engine)) {
            // Same VM: only the exact table object that registered may replace/unregister.
            // No ILuaEngine implementation overrides equals, so this is reference identity.
            return owner.table().equals(callerTable);
        }
        // Different VM: this is a reload; match the declarative mod id.
        return callerModId != null && callerModId.equals(owner.modId());
    }

    @Override
    public boolean register(@NotNull String name, @NotNull ILuaTable definition) {
        String modId = resolveCurrentModId();
        ILuaValue ownerTable = currentModTable();
        if (rejectInvalidContext(name, modId, ownerTable)) {
            return false;
        }

        if (!CommandPathValidator.isValidPath(name)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + REJECTED_BY_MOD_INFIX + modId + "': invalid path (segments of lowercase letters, digits, underscores or colons, max "
                            + CommandPathValidator.NAME_MAX_LENGTH + " chars each, max "
                            + CommandPathValidator.PATH_MAX_DEPTH + " levels, max "
                            + CommandPathValidator.PATH_MAX_LENGTH + " chars total).");
            return false;
        }

        if (CommandPathValidator.isReservedPath(name)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + REJECTED_BY_MOD_INFIX + modId
                            + "': name is reserved (built-in /lt tree or vanilla command).");
            return false;
        }

        CommandDefinition parsed = CommandDefinitionParser.parseDefinition(engine, modId, name, definition);
        if (parsed == null) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + REJECTED_BY_MOD_INFIX + modId + "': invalid definition table.");
            return false;
        }

        // Check every claimed path (primary + aliases) against foreign owners. Ownership is
        // decided by the mod table object, not by the spoofable ID string.
        if (hasForeignOwner(parsed, ownerTable, modId, name)) {
            return false;
        }

        Owner owner = new Owner(engine, ownerTable, modId);
        for (String path : CommandPathValidator.allPaths(parsed)) {
            COMMANDS.put(path, parsed);
            OWNERS.put(path, owner);
        }
        LuaTweakerLog.get().info(LogStage.COMMAND,
                "Commands:Register '/" + name + "' by mod '" + modId + "' (op:" + parsed.permissionLevel()
                        + ", console:" + parsed.consoleAllowed()
                        + (parsed.aliases().isEmpty() ? "" : ", aliases: " + parsed.aliases())
                        + (parsed.suggestionProvider() == null ? "" : ", suggestions: yes")
                        + ")");
        return true;
    }

    private static boolean rejectInvalidContext(@NotNull String name, @Nullable String modId,
                                                @Nullable ILuaValue ownerTable) {
        if (modId == null || ownerTable == null) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name
                            + "') rejected: no owning mod context (mod global missing). Register commands from main.lua or mod.OnEnable().");
            return true;
        }
        return false;
    }

    private boolean hasForeignOwner(@NotNull CommandDefinition parsed, @Nullable ILuaValue ownerTable,
                                    @Nullable String modId, @NotNull String name) {
        for (String path : CommandPathValidator.allPaths(parsed)) {
            CommandDefinition existing = COMMANDS.get(path);
            if (existing != null && !sameOwner(path, ownerTable, modId)) {
                LuaTweakerLog.get().error(LogStage.COMMAND,
                        REGISTER_PREFIX + name + REJECTED_BY_MOD_INFIX + modId + "': path '/" + path
                                + "' is already registered by mod '" + existing.modId()
                                + "'. Use a mod-specific name to avoid conflicts.");
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean unregister(@NotNull String name) {
        String modId = resolveCurrentModId();
        ILuaValue ownerTable = currentModTable();
        CommandDefinition existing = COMMANDS.get(name);
        if (existing == null) {
            LuaTweakerLog.get().warn(LogStage.COMMAND,
                    "Commands:Unregister('" + name + "') ignored: not registered.");
            return false;
        }
        if (!sameOwner(name, ownerTable, modId)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    "Commands:Unregister('" + name + "') rejected: owned by mod '" + existing.modId()
                            + "', caller '" + (modId == null ? "unknown" : modId) + "'.");
            return false;
        }
        // Resolve aliases to the primary name first: the definition is stored
        // under its primary, so removing by an alias name would match nothing
        // and falsely report success.
        String primary = existing.name();
        COMMANDS.entrySet().removeIf(entry -> entry.getValue().name().equals(primary));
        OWNERS.keySet().removeIf(path -> !COMMANDS.containsKey(path));
        LuaTweakerLog.get().info(LogStage.COMMAND,
                "Commands:Unregister '" + primary + "' by mod '" + modId + "'."
                        + (primary.equals(name) ? "" : " (via alias '" + name + "')"));
        return true;
    }

    /**
     * Resolves the id of the Lua mod currently being loaded from the engine's
     * global {@code mod} table. Only valid while a mod's main.lua / OnEnable
     * runs; commands must be registered during loading.
     */
    private @Nullable String resolveCurrentModId() {
        return com.luatweaker.core.mod.LuaModContext.resolveCurrentModId(engine);
    }
}
