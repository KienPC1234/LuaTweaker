package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves {@code require("LuaTweaker.X")} module names to the global value that backs them.
 * Data-driven: every module alias maps to an ordered list of global keys to try
 * (first non-nil wins). Extracted from the engine so it stays focused on VM lifecycle.
 */
public final class NativeModuleResolver {

    private static final Map<String, String[]> MODULE_GLOBALS = buildModuleGlobals();

    private NativeModuleResolver() {}

    private static Map<String, String[]> buildModuleGlobals() {
        Map<String, String[]> m = new HashMap<>();
        m.put("LuaTweaker.Content", new String[]{"Content", "startup"});
        m.put("Content", new String[]{"Content", "startup"});
        m.put("startup", new String[]{"Content", "startup"});
        m.put("LuaTweaker.Recipe", new String[]{"Recipe", "recipes"});
        m.put("Recipe", new String[]{"Recipe", "recipes"});
        m.put("Recipes", new String[]{"Recipe", "recipes"});
        m.put("recipes", new String[]{"Recipe", "recipes"});
        m.put("LuaTweaker.Events", new String[]{"Events", "events"});
        m.put("Events", new String[]{"Events", "events"});
        m.put("events", new String[]{"Events", "events"});
        m.put("LuaTweaker.World", new String[]{"World", "Workspace"});
        m.put("World", new String[]{"World", "Workspace"});
        m.put("Workspace", new String[]{"World", "Workspace"});
        m.put("workspace", new String[]{"World", "Workspace"});
        m.put("LuaTweaker.Task", new String[]{"Task", "task"});
        m.put("Task", new String[]{"Task", "task"});
        m.put("task", new String[]{"Task", "task"});
        m.put("LuaTweaker.Entities", new String[]{"Entities", "EntityService"});
        m.put("Entities", new String[]{"Entities", "EntityService"});
        m.put("EntityService", new String[]{"Entities", "EntityService"});
        m.put("LuaTweaker.Players", new String[]{"Players"});
        m.put("Players", new String[]{"Players"});
        m.put("LuaTweaker.AIGoals", new String[]{"AIGoals"});
        m.put("AIGoals", new String[]{"AIGoals"});
        m.put("LuaTweaker.Loot", new String[]{"Loot"});
        m.put("Loot", new String[]{"Loot"});
        m.put("LuaTweaker.Worldgen", new String[]{"Worldgen"});
        m.put("Worldgen", new String[]{"Worldgen"});
        m.put("LuaTweaker.Noise", new String[]{"Noise"});
        m.put("Noise", new String[]{"Noise"});
        m.put("LuaTweaker.Dimensions", new String[]{"Dimensions"});
        m.put("Dimensions", new String[]{"Dimensions"});
        m.put("LuaTweaker.SpawnRules", new String[]{"SpawnRules"});
        m.put("SpawnRules", new String[]{"SpawnRules"});
        m.put("LuaTweaker.Structures", new String[]{"Structures"});
        m.put("Structures", new String[]{"Structures"});
        m.put("LuaTweaker.Biomes", new String[]{"Biomes"});
        m.put("Biomes", new String[]{"Biomes"});
        m.put("LuaTweaker.Storage", new String[]{"Storage", "storage"});
        m.put("Storage", new String[]{"Storage", "storage"});
        m.put("storage", new String[]{"Storage", "storage"});
        m.put("LuaTweaker.WorldStorage", new String[]{"WorldStorage"});
        m.put("WorldStorage", new String[]{"WorldStorage"});
        m.put("LuaTweaker.PlayerStorage", new String[]{"PlayerStorage"});
        m.put("PlayerStorage", new String[]{"PlayerStorage"});
        m.put("LuaTweaker.SessionStorage", new String[]{"SessionStorage"});
        m.put("SessionStorage", new String[]{"SessionStorage"});
        m.put("LuaTweaker.Datapack", new String[]{"Datapack"});
        m.put("Datapack", new String[]{"Datapack"});
        m.put("datapack", new String[]{"Datapack"});
        m.put("LuaTweaker.Network", new String[]{"NetworkService"});
        m.put("Network", new String[]{"NetworkService"});
        m.put("NetworkService", new String[]{"NetworkService"});
        m.put("LuaTweaker.Interception", new String[]{"Interception", "InterceptionService"});
        m.put("Interception", new String[]{"Interception", "InterceptionService"});
        m.put("InterceptionService", new String[]{"Interception", "InterceptionService"});
        m.put("LuaTweaker.Commands", new String[]{"Commands"});
        m.put("Commands", new String[]{"Commands"});
        m.put("LuaTweaker.Update", new String[]{"Update"});
        m.put("Update", new String[]{"Update"});
        m.put("LuaTweaker.Net", new String[]{"Net"});
        m.put("Net", new String[]{"Net"});
        m.put("LuaTweaker.WebService", new String[]{"Net"});
        m.put("WebService", new String[]{"Net"});
        m.put("LuaTweaker.Camera", new String[]{"Camera"});
        m.put("Camera", new String[]{"Camera"});
        m.put("LuaTweaker.Client", new String[]{"Client", "ClientService"});
        m.put("LuaTweaker.ClientService", new String[]{"Client", "ClientService"});
        m.put("Client", new String[]{"Client", "ClientService"});
        m.put("ClientService", new String[]{"Client", "ClientService"});
        m.put("LuaTweaker.ClientEffects", new String[]{"ClientEffects"});
        m.put("ClientEffects", new String[]{"ClientEffects"});
        m.put("LuaTweaker.GuiService", new String[]{"GuiService"});
        m.put("GuiService", new String[]{"GuiService"});
        m.put("LuaTweaker.RenderService", new String[]{"RenderService"});
        m.put("RenderService", new String[]{"RenderService"});
        m.put("LuaTweaker.RunService", new String[]{"RunService"});
        m.put("RunService", new String[]{"RunService"});
        m.put("LuaTweaker.KeyBindService", new String[]{"KeyBindService"});
        m.put("KeyBindService", new String[]{"KeyBindService"});
        m.put("LuaTweaker.Signal", new String[]{"Signal"});
        m.put("Signal", new String[]{"Signal"});
        m.put("LuaTweaker.Utils", new String[]{"Utils"});
        m.put("Utils", new String[]{"Utils"});
        m.put("LuaTweaker.TweenService", new String[]{"TweenService"});
        m.put("TweenService", new String[]{"TweenService"});
        m.put("LuaTweaker.Math.Vector3", new String[]{"Vector3"});
        m.put("Vector3", new String[]{"Vector3"});
        m.put("LuaTweaker.Math.Vector2", new String[]{"Vector2"});
        m.put("Vector2", new String[]{"Vector2"});
        m.put("LuaTweaker.Math.Color3", new String[]{"Color3"});
        m.put("Color3", new String[]{"Color3"});
        return Collections.unmodifiableMap(m);
    }

    /**
     * Returns the first non-nil global for the requested module alias.
     *
     * @param globals SPI wrapper around the luava globals table
     * @param modName requested module name
     * @return the resolved global, or {@code LuaNil.NIL} when the alias is unknown or unset
     */
    public static org.luava.runtime.LuaValue resolveLuava(ILuaTable globals, String modName) {
        String[] keys = MODULE_GLOBALS.get(modName);
        if (keys == null || globals == null) return org.luava.runtime.LuaNil.NIL;
        for (String key : keys) {
            ILuaValue val = globals.rawget(key);
            if (val == null || val.isNil()) continue;
            org.luava.runtime.LuaValue converted = toLuavaValue(val);
            if (converted != null && !converted.isNil()) return converted;
        }
        return org.luava.runtime.LuaNil.NIL;
    }

    /**
     * Unwraps an SPI value into its backing luava value. A dedicated luava SPI wrapper exposes
     * a zero-argument accessor whose return type is a luava LuaValue; discovery by return type
     * keeps this resolver independent of the wrapper class name.
     */
    private static org.luava.runtime.LuaValue toLuavaValue(ILuaValue value) {
        for (Method method : value.getClass().getMethods()) {
            if (method.getParameterCount() != 0
                    || !org.luava.runtime.LuaValue.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                Object raw = method.invoke(value);
                if (raw instanceof org.luava.runtime.LuaValue luavaValue) return luavaValue;
            } catch (ReflectiveOperationException e) {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "NativeModuleResolver: failed to unwrap Lua value via " + method.getName()
                                + ": " + e.getMessage());
            }
        }
        Object javaObject = value.toJavaObject();
        if (javaObject instanceof org.luava.runtime.LuaValue luavaValue) return luavaValue;
        return javaObject == null
                ? org.luava.runtime.LuaNil.NIL
                : org.luava.binding.LuaDataConverter.toLua(javaObject);
    }
}
