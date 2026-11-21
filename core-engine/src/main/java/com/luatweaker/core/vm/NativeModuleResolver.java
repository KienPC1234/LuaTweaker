package com.luatweaker.core.vm;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.luava.binding.LuaDataConverter;
import org.luava.runtime.LuaNil;
import org.luava.runtime.LuaValue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@code require("LuaTweaker.X")} module names to the global value that backs them.
 * Data-driven: every module alias maps to an ordered list of global keys to try
 * (first non-nil wins). Extracted from the engine so it stays focused on VM lifecycle.
 */
public final class NativeModuleResolver {

    /** Prefix for fully-qualified module aliases. */
    private static final String LUA_TWEAKER_PREFIX = "LuaTweaker.";

    private static final Map<String, String[]> MODULE_GLOBALS = buildModuleGlobals();
    private static final Map<String, String[]> DYNAMIC_ALIASES = new ConcurrentHashMap<>();

    private NativeModuleResolver() {}

    /**
     * Registers an extra {@code require()} alias contributed by a Java plugin.
     * Plugin aliases never override built-in keys; when the name already exists
     * the built-in mapping wins so core behaviour stays stable.
     */
    public static void registerAlias(String modName, String... globalKeys) {
        if (modName == null || modName.isBlank() || globalKeys == null || globalKeys.length == 0) {
            return;
        }
        DYNAMIC_ALIASES.putIfAbsent(modName, globalKeys.clone());
    }

    /**
     * Standardized registration for built-in or plugin services.
     * Maps primaryName, "LuaTweaker." + primaryName, and all aliases to the global lookup keys.
     */
    public static void registerModule(String primaryName, String... aliases) {
        if (primaryName == null || primaryName.isBlank()) {
            return;
        }
        String[] keyArray = dedupeKeys(primaryName, aliases);
        registerAlias(primaryName, keyArray);
        registerAlias(LUA_TWEAKER_PREFIX + primaryName, keyArray);
        if (aliases == null) {
            return;
        }
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
                registerAlias(alias, keyArray);
                registerAlias(LUA_TWEAKER_PREFIX + alias, keyArray);
            }
        }
    }

    private static String[] dedupeKeys(String primaryName, String... aliases) {
        List<String> keys = new ArrayList<>();
        keys.add(primaryName);
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank() && !keys.contains(alias)) {
                    keys.add(alias);
                }
            }
        }
        return keys.toArray(new String[0]);
    }

    /** Test-only hook to isolate unit tests sharing this static map. */
    public static void clearDynamicAliasesForTests() {
        DYNAMIC_ALIASES.clear();
    }

    /** {aliases..., keys...} rows: every alias resolves through the same global keys. */
    private static void service(Map<String, String[]> target, String[] aliases, String... keys) {
        for (String alias : aliases) {
            target.put(alias, keys);
        }
    }

    private static Map<String, String[]> buildModuleGlobals() {
        Map<String, String[]> globals =
                Collections.synchronizedMap(new LinkedHashMap<>());
        service(globals, new String[]{"Content", "LuaTweaker.Content", "startup"},
                "Content", "startup");
        // The content builder is also registered as the "Startup" service, so scripts may
        // require it under that name. Keep require() in sync with the service registry.
        service(globals, new String[]{"LuaTweaker.Startup", "Startup"}, "Content", "startup");
        service(globals, new String[]{"Recipe", "LuaTweaker.Recipe", "Recipes", "recipes"},
                "Recipe", "recipes");
        service(globals, new String[]{"Events", "LuaTweaker.Events", "events"}, "Events", "events");
        service(globals, new String[]{"World", "LuaTweaker.World", "Workspace", "workspace"},
                "World", "Workspace");
        service(globals, new String[]{"Task", "LuaTweaker.Task", "task"}, "Task", "task");
        service(globals, new String[]{"Entities", "LuaTweaker.Entities", "EntityService"},
                "Entities", "EntityService");
        service(globals, new String[]{"Players", "LuaTweaker.Players"}, "Players");
        service(globals, new String[]{"AIGoals", "LuaTweaker.AIGoals"}, "AIGoals");
        service(globals, new String[]{"Loot", "LuaTweaker.Loot"}, "Loot");
        service(globals, new String[]{"Worldgen", "LuaTweaker.Worldgen"}, "Worldgen");
        service(globals, new String[]{"Noise", "LuaTweaker.Noise"}, "Noise");
        service(globals, new String[]{"Dimensions", "LuaTweaker.Dimensions"}, "Dimensions");
        service(globals, new String[]{"SpawnRules", "LuaTweaker.SpawnRules"}, "SpawnRules");
        service(globals, new String[]{"Structures", "LuaTweaker.Structures"}, "Structures");
        service(globals, new String[]{"Biomes", "LuaTweaker.Biomes"}, "Biomes");
        service(globals, new String[]{"Storage", "LuaTweaker.Storage", "storage"}, "Storage", "storage");
        service(globals, new String[]{"WorldStorage", "LuaTweaker.WorldStorage"}, "WorldStorage");
        service(globals, new String[]{"PlayerStorage", "LuaTweaker.PlayerStorage"}, "PlayerStorage");
        service(globals, new String[]{"SessionStorage", "LuaTweaker.SessionStorage"}, "SessionStorage");
        service(globals, new String[]{"Datapack", "LuaTweaker.Datapack", "datapack"}, "Datapack");
        service(globals, new String[]{"Network", "LuaTweaker.Network", "NetworkService"}, "NetworkService");
        service(globals, new String[]{"Interception", "LuaTweaker.Interception", "InterceptionService"},
                "Interception", "InterceptionService");
        service(globals, new String[]{"Commands", "LuaTweaker.Commands"}, "Commands");
        service(globals, new String[]{"Json", "LuaTweaker.Json", "json"}, "Json", "json");
        service(globals, new String[]{"Update", "LuaTweaker.Update"}, "Update");
        service(globals, new String[]{"Net", "LuaTweaker.Net"}, "Net");
        service(globals, new String[]{"WebService", "LuaTweaker.WebService"}, "Net");
        service(globals, new String[]{"Camera", "LuaTweaker.Camera"}, "Camera");
        service(globals, new String[]{"Client", "LuaTweaker.Client", "ClientService",
                "LuaTweaker.ClientService"}, "Client", "ClientService");
        service(globals, new String[]{"ClientEffects", "LuaTweaker.ClientEffects"}, "ClientEffects");
        service(globals, new String[]{"GuiService", "LuaTweaker.GuiService"}, "GuiService");
        service(globals, new String[]{"RenderService", "LuaTweaker.RenderService"}, "RenderService");
        service(globals, new String[]{"RunService", "LuaTweaker.RunService"}, "RunService");
        service(globals, new String[]{"KeyBindService", "LuaTweaker.KeyBindService"}, "KeyBindService");
        service(globals, new String[]{"Shaders", "LuaTweaker.Shaders"}, "Shaders");
        service(globals, new String[]{"ImGui", "LuaTweaker.ImGui"}, "ImGui");
        service(globals, new String[]{"Signal", "LuaTweaker.Signal"}, "Signal");
        service(globals, new String[]{"Brain", "LuaTweaker.Brain"}, "Brain");
        service(globals, new String[]{"Utils", "LuaTweaker.Utils"}, "Utils");
        service(globals, new String[]{"TweenService", "LuaTweaker.TweenService"}, "TweenService");
        service(globals, new String[]{"Vector3", "LuaTweaker.Math.Vector3"}, "Vector3");
        service(globals, new String[]{"Vector2", "LuaTweaker.Math.Vector2"}, "Vector2");
        service(globals, new String[]{"Color3", "LuaTweaker.Math.Color3"}, "Color3");
        return Collections.unmodifiableMap(globals);
    }

    /**
     * Returns the first non-nil global for the requested module alias.
     *
     * @param globals SPI wrapper around the luava globals table
     * @param modName requested module name
     * @return the resolved global, or {@code LuaNil.NIL} when the alias is unknown or unset
     */
    public static LuaValue resolveLuava(ILuaTable globals, String modName) {
        if (globals == null || modName == null || modName.isBlank()) {
            return LuaNil.NIL;
        }
        for (String key : lookupKeys(modName)) {
            ILuaValue val = globals.rawget(key);
            if (val == null || val.isNil()) {
                continue;
            }
            LuaValue converted = toLuavaValue(val);
            if (converted != null && !converted.isNil()) {
                return converted;
            }
        }
        return LuaNil.NIL;
    }

    private static String[] lookupKeys(String modName) {
        String[] keys = MODULE_GLOBALS.get(modName);
        if (keys == null) {
            keys = DYNAMIC_ALIASES.get(modName);
        }
        if (keys != null) {
            return keys;
        }
        if (modName.startsWith(LUA_TWEAKER_PREFIX)) {
            String shortName = modName.substring(LUA_TWEAKER_PREFIX.length());
            keys = MODULE_GLOBALS.get(shortName);
            if (keys == null) {
                keys = DYNAMIC_ALIASES.get(shortName);
            }
            return keys != null ? keys : new String[]{shortName};
        }
        return new String[]{modName};
    }

    /**
     * Unwraps an SPI value into its backing luava value. A dedicated luava SPI wrapper exposes
     * a zero-argument accessor whose return type is a luava LuaValue; discovery by return type
     * keeps this resolver independent of the wrapper class name.
     */
    private static LuaValue toLuavaValue(ILuaValue value) {
        for (Method method : value.getClass().getMethods()) {
            if (method.getParameterCount() != 0
                    || !LuaValue.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                Object raw = method.invoke(value);
                if (raw instanceof LuaValue luavaValue) {
                    return luavaValue;
                }
            } catch (ReflectiveOperationException e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "NativeModuleResolver: failed to unwrap Lua value via " + method.getName()
                                + ": " + e.getMessage());
            }
        }
        Object javaObject = value.toJavaObject();
        if (javaObject instanceof LuaValue luavaValue) {
            return luavaValue;
        }
        return javaObject == null ? LuaNil.NIL : LuaDataConverter.toLua(javaObject);
    }
}
