package com.luatweaker.command;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Parses the definition table passed to {@code Commands:Register} into an
 * immutable {@link CommandDefinition}. Malformed input is logged and rejected
 * (null) instead of throwing, so one bad mod cannot abort the load cycle.
 */
final class CommandDefinitionParser {

    static final String REGISTER_PREFIX = "Commands:Register('";
    static final String REJECTED_BY_MOD_INFIX = "') rejected by mod '";
    static final String BY_MOD_INFIX = "') by mod '";

    private static final int PERMISSION_MIN = 0;
    private static final int PERMISSION_MAX = 4;
    private static final int PERMISSION_DEFAULT = 2;

    private CommandDefinitionParser() {}

    static @Nullable CommandDefinition parseDefinition(
            @NotNull ILuaEngine engine,
            @NotNull String modId,
            @NotNull String name,
            @NotNull ILuaTable definition
    ) {
        try {
            ILuaValue handler = parseHandler(modId, name, definition);
            if (handler == null) {
                return null;
            }
            String description = parseOptionalString(definition, "Description", "description", name, modId, name);
            if (description == null) {
                return null;
            }
            Integer permissionLevel = parsePermissionLevel(definition, modId, name);
            if (permissionLevel == null) {
                return null;
            }
            Boolean consoleAllowed = parseConsoleAllowed(definition, modId, name);
            if (consoleAllowed == null) {
                return null;
            }
            String usage = parseOptionalString(definition, "Usage", "usage", "", modId, name);
            if (usage == null) {
                return null;
            }
            List<String> aliases = parseAliases(modId, name, definition).orElse(null);
            if (aliases == null) {
                return null;
            }
            ILuaValue suggestionProvider = parseSuggestions(definition);
            return new CommandDefinition(modId, name, description, permissionLevel, consoleAllowed, usage,
                    aliases, suggestionProvider, engine, handler);
        } catch (IllegalArgumentException | LuaException e) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "' failed to parse definition: " + e.getMessage());
            return null;
        }
    }

    private static @Nullable ILuaValue parseHandler(@NotNull String modId, @NotNull String name,
                                                     @NotNull ILuaTable definition) {
        ILuaValue handlerVal = definition.rawget("Handler");
        if (handlerVal == null || handlerVal.isNil()) {
            handlerVal = definition.rawget("handler");
        }
        if (handlerVal == null || !handlerVal.isFunction()) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "': missing required 'Handler' function.");
            return null;
        }
        return handlerVal;
    }

    /**
     * Optional string field: missing means {@code fallback}, present-but-not-a-string
     * rejects the registration.
     */
    private static @Nullable String parseOptionalString(@NotNull ILuaTable definition,
                                                        @NotNull String pascalKey, @NotNull String camelKey,
                                                        @NotNull String fallback, @NotNull String modId,
                                                        @NotNull String name) {
        Object value = CommandPathValidator.readField(definition, pascalKey, camelKey);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String text)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "': '" + pascalKey + "' must be a string.");
            return null;
        }
        return text;
    }

    /**
     * Optional integer permission level: missing means {@link #PERMISSION_DEFAULT},
     * non-integers and out-of-range values reject the registration.
     */
    private static @Nullable Integer parsePermissionLevel(@NotNull ILuaTable definition,
                                                           @NotNull String modId, @NotNull String name) {
        Object permObj = CommandPathValidator.readField(definition, "PermissionLevel", "permissionLevel");
        if (permObj == null) {
            return PERMISSION_DEFAULT;
        }
        if (!(permObj instanceof Number permNum)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "': 'PermissionLevel' must be a number.");
            return null;
        }
        int permissionLevel = permNum.intValue();
        if (permNum.doubleValue() != permissionLevel) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "': 'PermissionLevel' must be an integer.");
            return null;
        }
        if (permissionLevel < PERMISSION_MIN || permissionLevel > PERMISSION_MAX) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "': 'PermissionLevel' must be between "
                            + PERMISSION_MIN + " and " + PERMISSION_MAX + ".");
            return null;
        }
        return permissionLevel;
    }

    private static @Nullable Boolean parseConsoleAllowed(@NotNull ILuaTable definition,
                                                         @NotNull String modId, @NotNull String name) {
        Object consoleObj = CommandPathValidator.readField(definition, "ConsoleAllowed", "consoleAllowed");
        if (consoleObj == null) {
            return true;
        }
        if (!(consoleObj instanceof Boolean console)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "': 'ConsoleAllowed' must be a boolean.");
            return null;
        }
        return console;
    }

    /**
     * Parses the optional {@code Aliases} table: list of valid paths, deduplicated,
     * never equal to the primary name. Empty means the registration is rejected
     * (malformed aliases); an absent field yields an empty list.
     */
    static @NotNull Optional<List<String>> parseAliases(
            @NotNull String modId,
            @NotNull String name,
            @NotNull ILuaTable definition
    ) {
        ILuaValue aliasesVal = definition.rawget("Aliases");
        if (aliasesVal == null || aliasesVal.isNil()) {
            aliasesVal = definition.rawget("aliases");
        }
        if (aliasesVal == null || aliasesVal.isNil()) {
            return Optional.of(List.of());
        }
        if (!aliasesVal.isTable()) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "': 'Aliases' must be a table of strings.");
            return Optional.empty();
        }

        Set<String> aliases = new LinkedHashSet<>();
        ILuaTable aliasesTable = aliasesVal.asTable();
        int count = aliasesTable.length();
        if (count == 0) {
            return Optional.of(List.of());
        }
        for (int i = 1; i <= count; i++) {
            if (!validateAliasEntry(aliasesTable, modId, name, i, aliases)) {
                return Optional.empty();
            }
        }
        return Optional.of(List.copyOf(aliases));
    }

    private static boolean validateAliasEntry(@NotNull ILuaTable aliasesTable, @NotNull String modId,
                                              @NotNull String name, int index, Set<String> aliases) {
        ILuaValue entryVal = aliasesTable.rawget(index);
        Object entry = entryVal == null ? null : entryVal.toJavaObject();
        if (!(entry instanceof String aliasPath) || !CommandPathValidator.isValidPath(aliasPath)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "': alias #" + index
                            + " is not a valid command path.");
            return false;
        }
        if (CommandPathValidator.isReservedPath(aliasPath)) {
            LuaTweakerLog.get().error(LogStage.COMMAND,
                    REGISTER_PREFIX + name + BY_MOD_INFIX + modId + "': alias '" + aliasPath + "' is reserved.");
            return false;
        }
        if (!aliasPath.equals(name)) {
            aliases.add(aliasPath);
        }
        return true;
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
        if (suggestionsVal == null || suggestionsVal.isNil()) {
            return null;
        }
        if (suggestionsVal.isFunction()) {
            return suggestionsVal;
        }
        if (suggestionsVal.isTable()) {
            validateSuggestionEntries(suggestionsVal.asTable());
            return suggestionsVal;
        }
        throw new IllegalArgumentException("'Suggestions' must be a function or a table of strings.");
    }

    private static void validateSuggestionEntries(@NotNull ILuaTable suggestionsTable) {
        int count = suggestionsTable.length();
        for (int i = 1; i <= count; i++) {
            ILuaValue entryVal = suggestionsTable.rawget(i);
            Object entry = entryVal == null ? null : entryVal.toJavaObject();
            if (!(entry instanceof String)) {
                throw new IllegalArgumentException("'Suggestions' table entries must be strings.");
            }
        }
    }
}
