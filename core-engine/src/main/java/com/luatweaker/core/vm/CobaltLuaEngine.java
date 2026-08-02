package com.luatweaker.core.vm;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.*;
import com.luatweaker.core.engine.LuaEngine;
import com.luatweaker.core.linter.LuaLinter;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LuaClosure;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.VarArgFunction;

import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CobaltLuaEngine implements ILuaEngine {
    private final LuaState state;
    private final LuaEngine rawEngine;
    private boolean debugMode = false;
    private final java.util.Map<String, LuaValue> moduleCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final ThreadLocal<java.util.Deque<String>> activeModuleStack = ThreadLocal.withInitial(java.util.ArrayDeque::new);

    public CobaltLuaEngine() {
        this(false);
    }

    public CobaltLuaEngine(boolean debugMode) {
        this.debugMode = debugMode;
        this.rawEngine = new LuaEngine();
        this.state = rawEngine.getState();
        setupGlobalBindings();
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
        state.globals().rawset("DEBUG", ValueFactory.valueOf(debugMode));
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    private void setupGlobalBindings() {
        LuaTable globals = state.globals();
        globals.rawset("_G", globals);

        // 1. Expose DEBUG global boolean
        globals.rawset("DEBUG", ValueFactory.valueOf(debugMode));

        // 2. Redirect standard Lua print(...) to AsyncFileLogger & per-mod log
        globals.rawset("print", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                StringBuilder sb = new StringBuilder();
                int count = args.count();
                for (int i = 1; i <= count; i++) {
                    sb.append(args.arg(i).toString());
                    if (i < count) sb.append("\t");
                }
                String msg = sb.toString();
                AsyncFileLogger.get().info("PRINT", msg, state);
                String modId = getActiveModId();
                if (modId != null) AsyncFileLogger.get().logMod(modId, "INFO", msg);
                return Constants.NIL;
            }
        });

        // 3. Expose structured log table (log.info, log.warn, log.error, log.debug)
        LuaTable logTable = new LuaTable();
        logTable.rawset("info", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String msg = args.arg(1).toString();
                AsyncFileLogger.get().info("SCRIPT", msg, state);
                String modId = getActiveModId();
                if (modId != null) AsyncFileLogger.get().logMod(modId, "INFO", msg);
                return Constants.NIL;
            }
        });
        logTable.rawset("warn", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String msg = args.arg(1).toString();
                AsyncFileLogger.get().warn("SCRIPT", msg, state);
                String modId = getActiveModId();
                if (modId != null) AsyncFileLogger.get().logMod(modId, "WARN", msg);
                return Constants.NIL;
            }
        });
        logTable.rawset("error", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String msg = args.arg(1).toString();
                AsyncFileLogger.get().error("SCRIPT", msg, state);
                String modId = getActiveModId();
                if (modId != null) AsyncFileLogger.get().logMod(modId, "ERROR", msg);
                return Constants.NIL;
            }
        });
        logTable.rawset("debug", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                if (debugMode) {
                    String msg = args.arg(1).toString();
                    AsyncFileLogger.get().info("DEBUG", msg, state);
                    String modId = getActiveModId();
                    if (modId != null) AsyncFileLogger.get().logMod(modId, "DEBUG", msg);
                }
                return Constants.NIL;
            }
        });
        globals.rawset("log", logTable);

        // 3b. Standard OS Library (os.clock, os.time)
        LuaTable osTable = new LuaTable();
        osTable.rawset("clock", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) {
                return ValueFactory.valueOf(System.currentTimeMillis() / 1000.0);
            }
        });
        osTable.rawset("time", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) {
                return ValueFactory.valueOf(System.currentTimeMillis() / 1000);
            }
        });
        globals.rawset("os", osTable);

        // 4. Mod / game GetService service bindings
        LuaTable modTable = new LuaTable();
        VarArgFunction getServiceFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String name = args.arg(2).checkLuaString().toString();
                Object service = LuaServiceRegistry.get(name);
                if (service == null) {
                    throw new LuaError("Service '" + name + "' not found");
                }
                if (service instanceof ILuaTable customBinding) {
                    return ((CobaltLuaTable) customBinding).getCobaltValue();
                }
                return new LuaUserdata(service);
            }
        };
        modTable.rawset("GetService", getServiceFunc);
        globals.rawset("Mod", modTable);
        globals.rawset("game", modTable);

        // 5. item(id, count, [nbt/options]) function (KubeJS style)
        VarArgFunction itemFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaValue first = args.arg(1);
                if (first instanceof LuaTable tbl) {
                    LuaValue idVal = tbl.rawget(ValueFactory.valueOf("id"));
                    if (idVal.isNil()) idVal = tbl.rawget(ValueFactory.valueOf("item"));
                    String id = idVal.checkLuaString().toString();
                    LuaValue cVal = tbl.rawget(ValueFactory.valueOf("count"));
                    int count = (cVal instanceof LuaInteger || cVal instanceof LuaDouble) ? cVal.toInteger() : 1;

                    ItemCount ic = new ItemCount(id, count);
                    LuaValue dmgVal = tbl.rawget(ValueFactory.valueOf("damage"));
                    if (dmgVal instanceof LuaInteger || dmgVal instanceof LuaDouble) ic = ic.withDamage(dmgVal.toInteger());
                    LuaValue nameVal = tbl.rawget(ValueFactory.valueOf("name"));
                    if (nameVal instanceof LuaString) ic = ic.withName(nameVal.toString());
                    LuaValue nbtVal = tbl.rawget(ValueFactory.valueOf("nbt"));
                    if (!nbtVal.isNil()) ic = ic.withNbt(nbtVal.toString());
                    return createItemUserdata(ic);
                }

                String itemId = first.checkLuaString().toString();
                if (itemId.startsWith("#") || itemId.startsWith("tag:") || itemId.startsWith("oredict:")) {
                    return createIngredientUserdata(new IngredientWrapper(itemId));
                }

                int count = args.arg(2).isNil() ? 1 : args.arg(2).checkInteger();
                ItemCount ic = new ItemCount(itemId, count);

                LuaValue third = args.arg(3);
                if (!third.isNil()) {
                    if (third instanceof LuaTable opts) {
                        LuaValue dmg = opts.rawget(ValueFactory.valueOf("damage"));
                        if (dmg instanceof LuaInteger || dmg instanceof LuaDouble) ic = ic.withDamage(dmg.toInteger());
                        LuaValue name = opts.rawget(ValueFactory.valueOf("name"));
                        if (name instanceof LuaString) ic = ic.withName(name.toString());
                        LuaValue nbt = opts.rawget(ValueFactory.valueOf("nbt"));
                        if (!nbt.isNil()) ic = ic.withNbt(nbt.toString());
                    } else {
                        ic = ic.withNbt(third.toString());
                    }
                }
                return createItemUserdata(ic);
            }
        };

        // 6. ingredient(descriptor), tag(name), oredict(name)
        VarArgFunction ingFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String desc = args.arg(1).checkLuaString().toString();
                return createIngredientUserdata(new IngredientWrapper(desc));
            }
        };
        VarArgFunction tagFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                String desc = args.arg(1).checkLuaString().toString();
                if (!desc.startsWith("#")) desc = "#" + desc;
                return createIngredientUserdata(new IngredientWrapper(desc));
            }
        };

        globals.rawset("item", itemFunc);
        globals.rawset("ingredient", ingFunc);
        globals.rawset("tag", tagFunc);
        globals.rawset("oredict", tagFunc);

        // 7. Bootstrap Roblox task and Signal libraries from Resource File
        loadBootstrapResource("/lua/luatweaker_bootstrap.lua");

        // 7b. Deferred task runner: executes a Lua function inside a proper Cobalt
        // coroutine loop so yields (task.wait) and resumes work without leaking
        // UnwindThrowable control-flow exceptions into Java callers.
        LuaValue taskVal = globals.rawget("task");
        if (taskVal instanceof LuaTable taskTbl) {
            taskTbl.rawset("_run_deferred", new VarArgFunction() {
                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    LuaValue fnVal = args.arg(1);
                    if (!(fnVal instanceof LuaFunction fn)) {
                        return ValueFactory.varargsOf(Constants.NIL, Constants.NIL);
                    }
                    List<LuaValue> callArgs = new ArrayList<>();
                    LuaValue argsTblVal = args.arg(2);
                    if (argsTblVal instanceof LuaTable argsTbl) {
                        int len = argsTbl.length();
                        for (int i = 1; i <= len; i++) {
                            callArgs.add(argsTbl.rawget(i));
                        }
                    }
                    LuaThread thread = new LuaThread(state, fn);
                    Varargs result;
                    try {
                        result = LuaThread.run(thread, ValueFactory.varargsOf(callArgs.toArray(new LuaValue[0])));
                    } finally {
                        restoreCurrentThread(state);
                    }
                    return ValueFactory.varargsOf(thread, result == null ? Constants.NIL : result);
                }
            });
        }

        setupNativeRequire(globals);
    }

    // Cobalt leaves LuaState.currentThread pointing at the last executed coroutine
    // after LuaThread.run returns. The field is package-private, so restore it via
    // reflection to keep subsequent interpreter calls attached to the main thread.
    private static final java.lang.reflect.Field CURRENT_THREAD_FIELD;

    static {
        java.lang.reflect.Field field;
        try {
            field = LuaState.class.getDeclaredField("currentThread");
            field.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError("Cannot access LuaState.currentThread: " + e);
        }
        CURRENT_THREAD_FIELD = field;
    }

    private static void restoreCurrentThread(LuaState state) {
        try {
            CURRENT_THREAD_FIELD.set(state, state.getMainThread());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to restore LuaState.currentThread", e);
        }
    }

    private void loadBootstrapResource(String resourcePath) {
        try (InputStream stream = CobaltLuaEngine.class.getResourceAsStream(resourcePath)) {
            if (stream != null) {
                LuaClosure closure = LoadState.load(state, stream, resourcePath, state.globals());
                org.squiddev.cobalt.function.Dispatch.call(state, closure);
                AsyncFileLogger.get().info(com.luatweaker.api.log.LogStage.SYSTEM, "Successfully executed bootstrap resource: " + resourcePath);
            } else {
                AsyncFileLogger.get().error(com.luatweaker.api.log.LogStage.SYSTEM, "Bootstrap resource file not found: " + resourcePath, state);
            }
        } catch (Throwable e) {
            AsyncFileLogger.get().error(com.luatweaker.api.log.LogStage.SYSTEM, "Failed to load system bootstrap script '" + resourcePath + "': " + e.getMessage(), state);
        }
    }

    private String getActiveModId() {
        String currentModule = activeModuleStack.get().peek();
        if (currentModule == null || currentModule.isBlank()) return null;
        int slash = currentModule.indexOf('/');
        if (slash > 0) return currentModule.substring(0, slash);
        int dot = currentModule.indexOf('.');
        return dot > 0 ? currentModule.substring(0, dot) : currentModule;
    }

    private File luaDirectory;

    @Override
    public void setLuaDirectory(File luaDirectory) {
        this.luaDirectory = luaDirectory;
    }

    private void setupNativeRequire(LuaTable globals) {
        LuaValue oldRequire = globals.rawget("require");
        if (oldRequire != null && !oldRequire.isNil()) {
            globals.rawset("_oldRequire", oldRequire);
        }
        globals.rawset("require", new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError, org.squiddev.cobalt.UnwindThrowable {
                if (args.count() < 1) throw new LuaError("require requires 1 argument (moduleName)");
                String modName = args.arg(1).toString();

                if (modName.startsWith(".")) {
                    if (modName.startsWith("..")) {
                        throw new LuaError("Parent directory traversal ('..') is forbidden for Sibling Require security.");
                    }
                    java.util.Deque<String> stack = activeModuleStack.get();
                    String currentModule = stack.peek();
                    if (currentModule != null && !currentModule.isBlank()) {
                        if (currentModule.endsWith(".lua")) {
                            currentModule = currentModule.substring(0, currentModule.length() - 4);
                        }
                        currentModule = currentModule.replace('/', '.');
                        int lastDot = currentModule.lastIndexOf('.');
                        if (lastDot > 0) {
                            String parentPackage = currentModule.substring(0, lastDot);
                            modName = parentPackage + "." + modName.substring(1);
                        } else {
                            modName = currentModule + "." + modName.substring(1);
                        }
                    } else {
                        modName = modName.substring(1);
                    }
                }

                LuaValue result = moduleCache.get(modName);
                if (result == null) {
                    result = resolveNativeModule(globals, modName);
                    if (result == null || result.isNil()) {
                        result = resolveFileModule(modName);
                    }
                    if (result != null && !result.isNil()) {
                        moduleCache.put(modName, result);
                    }
                }
                if (result != null && !result.isNil()) {
                    return result;
                }
                LuaValue oldReq = globals.rawget("_oldRequire");
                if (oldReq instanceof org.squiddev.cobalt.function.LuaFunction) {
                    return org.squiddev.cobalt.function.Dispatch.invoke(state, oldReq, args);
                }
                throw new LuaError("Module not found: " + modName);
            }
        });
    }

    private LuaValue resolveFileModule(String modName) {
        File dir = luaDirectory;
        if (dir == null || !dir.exists()) {
            dir = new File("luamods");
        }
        if (!dir.exists()) {
            dir = new File("lua");
        }
        if (!dir.exists()) return Constants.NIL;

        String relPath = modName.replace('.', '/');
        List<String> candidates = List.of(
                relPath + ".lua",
                relPath + "/init.lua",
                "lib/" + relPath + ".lua",
                "lib/" + relPath + "/init.lua"
        );

        for (String cand : candidates) {
            File file = new File(dir, cand);
            if (file.exists() && file.isFile()) {
                activeModuleStack.get().push(modName);
                try (InputStream stream = Files.newInputStream(file.toPath())) {
                    LuaClosure closure = LoadState.load(state, stream, file.getName(), state.globals());
                    Varargs res = org.squiddev.cobalt.function.Dispatch.call(state, closure);
                    LuaValue ret = res.arg(1);
                    return (ret != null && !ret.isNil()) ? ret : ValueFactory.valueOf(true);
                } catch (Throwable e) {
                    if (e.getClass().getName().contains("UnwindThrowable")) {
                        return ValueFactory.valueOf(true);
                    }
                    e.printStackTrace();
                    String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
                    AsyncFileLogger.get().error("REQUIRE", "Failed to load Lua module file '" + cand + "': " + msg, state);
                    return Constants.NIL;
                } finally {
                    activeModuleStack.get().pop();
                }
            }
        }

        // Search sub-directories of luamods/ (e.g. luamods/my_custom_mod/src/server/boss_ai.lua)
        File[] subMods = dir.listFiles();
        if (subMods != null) {
            for (File modFolder : subMods) {
                if (modFolder.isDirectory()) {
                    File targetFile = new File(modFolder, relPath + ".lua");
                    if (!targetFile.exists()) targetFile = new File(modFolder, relPath + "/init.lua");
                    if (targetFile.exists() && targetFile.isFile()) {
                        activeModuleStack.get().push(modName);
                        try (InputStream stream = Files.newInputStream(targetFile.toPath())) {
                            LuaClosure closure = LoadState.load(state, stream, targetFile.getName(), state.globals());
                            Varargs res = org.squiddev.cobalt.function.Dispatch.call(state, closure);
                            LuaValue ret = res.arg(1);
                            return (ret != null && !ret.isNil()) ? ret : ValueFactory.valueOf(true);
                        } catch (Throwable e) {
                            if (e.getClass().getName().contains("UnwindThrowable")) {
                                return ValueFactory.valueOf(true);
                            }
                            String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
                            AsyncFileLogger.get().error("REQUIRE", "Failed to load Lua module file '" + targetFile.getPath() + "': " + msg, state);
                        } finally {
                            activeModuleStack.get().pop();
                        }
                    }
                }
            }
        }

        return Constants.NIL;
    }

    private LuaValue resolveNativeModule(LuaTable globals, String modName) {
        return switch (modName) {
            case "LuaTweaker.Content", "Content", "startup" -> {
                LuaValue val = globals.rawget("Content");
                yield (val != null && !val.isNil()) ? val : globals.rawget("startup");
            }
            case "LuaTweaker.Recipe", "Recipe", "Recipes", "recipes" -> {
                LuaValue val = globals.rawget("Recipe");
                yield (val != null && !val.isNil()) ? val : globals.rawget("recipes");
            }
            case "LuaTweaker.Events", "Events", "events" -> {
                LuaValue val = globals.rawget("Events");
                yield (val != null && !val.isNil()) ? val : globals.rawget("events");
            }
            case "LuaTweaker.World", "World", "Workspace", "workspace" -> {
                LuaValue val = globals.rawget("World");
                yield (val != null && !val.isNil()) ? val : globals.rawget("Workspace");
            }
            case "LuaTweaker.Task", "Task", "task" -> {
                LuaValue val = globals.rawget("Task");
                yield (val != null && !val.isNil()) ? val : globals.rawget("task");
            }
            case "LuaTweaker.Entities", "Entities", "EntityService" -> {
                LuaValue val = globals.rawget("Entities");
                yield (val != null && !val.isNil()) ? val : globals.rawget("EntityService");
            }
            case "LuaTweaker.Players", "Players" -> globals.rawget("Players");
            case "LuaTweaker.AIGoals", "AIGoals" -> globals.rawget("AIGoals");
            case "LuaTweaker.Loot", "Loot" -> globals.rawget("Loot");
            case "LuaTweaker.Storage", "Storage", "storage" -> {
                LuaValue val = globals.rawget("Storage");
                if (val == null || val.isNil()) {
                    val = globals.rawget("storage");
                }
                yield val;
            }
            case "LuaTweaker.WorldStorage", "WorldStorage" -> globals.rawget("WorldStorage");
            case "LuaTweaker.PlayerStorage" -> globals.rawget("PlayerStorage");
            case "LuaTweaker.SessionStorage" -> globals.rawget("SessionStorage");
            case "LuaTweaker.Datapack", "Datapack", "datapack" -> globals.rawget("Datapack");
            case "LuaTweaker.Network", "Network", "NetworkService" -> globals.rawget("NetworkService");
            case "LuaTweaker.Interception", "Interception", "InterceptionService" -> {
                LuaValue val = globals.rawget("Interception");
                yield (val != null && !val.isNil()) ? val : globals.rawget("InterceptionService");
            }
            case "LuaTweaker.Camera", "Camera" -> globals.rawget("Camera");
            case "LuaTweaker.Client", "LuaTweaker.ClientService", "Client", "ClientService" -> {
                LuaValue val = globals.rawget("Client");
                yield (val != null && !val.isNil()) ? val : globals.rawget("ClientService");
            }
            case "LuaTweaker.ClientEffects", "ClientEffects" -> globals.rawget("ClientEffects");
            case "LuaTweaker.GuiService", "GuiService" -> globals.rawget("GuiService");
            case "LuaTweaker.RunService", "RunService" -> globals.rawget("RunService");
            case "LuaTweaker.KeyBindService", "KeyBindService" -> globals.rawget("KeyBindService");
            case "LuaTweaker.Signal", "Signal" -> globals.rawget("Signal");
            case "LuaTweaker.Utils", "Utils" -> globals.rawget("Utils");
            case "LuaTweaker.TweenService", "TweenService" -> globals.rawget("TweenService");
            case "LuaTweaker.Math.Vector3", "Vector3" -> globals.rawget("Vector3");
            case "LuaTweaker.Math.Vector2", "Vector2" -> globals.rawget("Vector2");
            case "LuaTweaker.Math.Color3", "Color3" -> globals.rawget("Color3");
            default -> Constants.NIL;
        };
    }

    @Override
    public synchronized void executeString(String code, String name) {
        activeModuleStack.get().push(name);
        try (InputStream stream = new java.io.ByteArrayInputStream(code.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            LuaClosure closure = LoadState.load(state, stream, name, state.globals());
            org.squiddev.cobalt.function.Dispatch.call(state, closure);
        } catch (Throwable e) {
            if (!e.getClass().getName().contains("UnwindThrowable")) {
                AsyncFileLogger.get().error("BOOTSTRAP", "Error running bootstrap script " + name + ": " + e.getMessage(), state);
            }
        } finally {
            activeModuleStack.get().pop();
        }
    }

    private LuaUserdata createItemUserdata(ItemCount ic) {
        LuaTable meta = new LuaTable();

        VarArgFunction withCount = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                int count = args.arg(2).checkInteger();
                return createItemUserdata(current.withCount(count));
            }
        };
        VarArgFunction withDamage = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                int dmg = args.arg(2).checkInteger();
                return createItemUserdata(current.withDamage(dmg));
            }
        };
        VarArgFunction withName = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                String name = args.arg(2).checkLuaString().toString();
                return createItemUserdata(current.withName(name));
            }
        };
        VarArgFunction withLore = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                List<String> loreList = new ArrayList<>();
                if (args.arg(2) instanceof LuaTable tbl) {
                    int len = tbl.length();
                    for (int i = 1; i <= len; i++) {
                        loreList.add(tbl.rawget(i).toString());
                    }
                } else {
                    loreList.add(args.arg(2).toString());
                }
                return createItemUserdata(current.withLore(loreList));
            }
        };
        VarArgFunction withEnchantment = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                String ench = args.arg(2).checkLuaString().toString();
                int level = args.arg(3).isNil() ? 1 : args.arg(3).checkInteger();
                return createItemUserdata(current.withEnchantment(ench, level));
            }
        };
        VarArgFunction withNbt = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                ItemCount current = (ItemCount) ud.instance;
                String nbt = args.arg(2).toString();
                return createItemUserdata(current.withNbt(nbt));
            }
        };

        try {
            meta.rawset("withCount", withCount);       meta.rawset("count", withCount);
            meta.rawset("withDamage", withDamage);     meta.rawset("damage", withDamage);
            meta.rawset("withName", withName);         meta.rawset("name", withName);
            meta.rawset("withLore", withLore);         meta.rawset("lore", withLore);
            meta.rawset("withEnchantment", withEnchantment); meta.rawset("enchant", withEnchantment);
            meta.rawset("withNbt", withNbt);           meta.rawset("nbt", withNbt);

            LuaTable indexTable = new LuaTable();
            indexTable.rawset("id", ValueFactory.valueOf(ic.itemId()));
            indexTable.rawset("count", ValueFactory.valueOf(ic.count()));
            indexTable.rawset("damage", ValueFactory.valueOf(ic.damage()));

            meta.rawset(Constants.INDEX, new VarArgFunction() {
                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    LuaValue key = args.arg(2);
                    LuaValue val = meta.rawget(key);
                    if (!val.isNil()) return val;
                    return indexTable.rawget(key);
                }
            });
        } catch (LuaError ignored) {}

        LuaUserdata ud = new LuaUserdata(ic);
        ud.setMetatable(state, meta);
        return ud;
    }

    private LuaUserdata createIngredientUserdata(IngredientWrapper ing) {
        LuaTable meta = new LuaTable();
        VarArgFunction orFunc = new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                LuaUserdata ud = (LuaUserdata) args.arg(1);
                IngredientWrapper current = (IngredientWrapper) ud.instance;
                LuaValue otherArg = args.arg(2);
                if (otherArg instanceof LuaUserdata otherUd && otherUd.instance instanceof IngredientWrapper otherIng) {
                    return createIngredientUserdata(current.or(otherIng));
                }
                return createIngredientUserdata(current.or(otherArg.toString()));
            }
        };

        try {
            meta.rawset("orIngredient", orFunc);
            meta.rawset("alt", orFunc);
            meta.rawset("otherwise", orFunc);
            meta.rawset("or", orFunc);
            meta.rawset(Constants.INDEX, meta);
        } catch (LuaError ignored) {}

        LuaUserdata ud = new LuaUserdata(ing);
        ud.setMetatable(state, meta);
        return ud;
    }

    @Override
    public void registerService(String name, ILuaTable service) {
        LuaServiceRegistry.register(name, service);
    }

    @Override
    public void registerService(String name, Object service) {
        LuaServiceRegistry.register(name, service);
    }

    @Override
    public ILuaTable getGlobalEnvironment() {
        return new CobaltLuaTable(state.globals());
    }

    @Override
    public ILuaValue toLuaValue(Object obj) {
        if (obj == null) return nilValue();
        if (obj instanceof ILuaValue lv) return lv;
        if (obj instanceof String s) return wrapString(s);
        if (obj instanceof Number n) return wrapNumber(n.doubleValue());
        if (obj instanceof Boolean b) return wrapBoolean(b);
        if (obj instanceof ILuaTable t) return t;

        if (obj instanceof java.util.Map<?, ?> map) {
            ILuaTable table = createTable();
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                table.rawset(String.valueOf(entry.getKey()), toLuaValue(entry.getValue()));
            }
            return table;
        }

        if (obj instanceof java.util.List<?> list) {
            ILuaTable table = createTable();
            int i = 1;
            for (Object item : list) {
                table.rawset(i++, toLuaValue(item));
            }
            return table;
        }

        return wrapUserdata(obj);
    }

    private LuaValue toCobaltValue(ILuaValue val) {
        if (val == null || val.isNil()) return Constants.NIL;
        if (val instanceof CobaltLuaValue clv) return clv.getCobaltValue();
        Object raw = val.toJavaObject();
        if (raw instanceof String s) return ValueFactory.valueOf(s);
        if (raw instanceof Number n) return ValueFactory.valueOf(n.doubleValue());
        if (raw instanceof Boolean b) return ValueFactory.valueOf(b);
        return ValueFactory.userdataOf(raw);
    }

    @Override
    public synchronized ILuaValue callFunction(ILuaValue function, ILuaValue... args) {
        if (function != null && !function.isNil()) {
            try {
                LuaValue cobaltFunc = toCobaltValue(function);
                LuaValue[] cobaltArgs = new LuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    cobaltArgs[i] = toCobaltValue(args[i]);
                }
                Varargs result = org.squiddev.cobalt.function.Dispatch.invoke(state, cobaltFunc, ValueFactory.varargsOf(cobaltArgs));
                return new CobaltLuaValue(result.arg(1));
            } catch (org.squiddev.cobalt.UnwindThrowable e) {
                // Control-flow exception used internally by the Cobalt VM for coroutine yields
                // and thread resumptions. It is NOT a script error and carries no message.
            } catch (Throwable e) {
                String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
                AsyncFileLogger.get().error("FUNCTION_CALL", "Lua error executing callback: " + msg, state);
            }
        }
        return nilValue();
    }

    @Override
    public void registerGlobal(String name, ILuaValue value) {
        state.globals().rawset(name, toCobaltValue(value));
    }

    @Override
    public void registerGlobal(String name, ILuaFunction function) {
        state.globals().rawset(name, new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                int count = args.count();
                ILuaValue[] wrappedArgs = new ILuaValue[count];
                for (int i = 0; i < count; i++) {
                    wrappedArgs[i] = new CobaltLuaValue(args.arg(i + 1));
                }
                try {
                    ILuaValue result = function.invoke(wrappedArgs);
                    return result == null ? Constants.NIL : toCobaltValue(result);
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError(e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        });
    }

    @Override
    public synchronized void executeScript(File file, String context) {
        if (!file.exists()) {
            AsyncFileLogger.get().warn(context, "Script file does not exist: " + file.getAbsolutePath(), state);
            return;
        }

        try (InputStream stream = Files.newInputStream(file.toPath())) {
            LuaClosure closure = LoadState.load(state, stream, file.getName(), state.globals());
            org.squiddev.cobalt.function.Dispatch.call(state, closure);
            AsyncFileLogger.get().info(context, "Successfully executed script: " + file.getName(), state);
        } catch (CompileException e) {
            LuaLinter.logFancyCompileError(context, file.getName(), file, e);
        } catch (LuaError e) {
            e.fillTraceback(state);
            LuaLinter.logFancyRuntimeError(context, file.getName(), file, e);
        } catch (Throwable e) {
            AsyncFileLogger.get().error(context, "Unexpected error running script " + file.getName() + ": " + e.getMessage(), state);
        }
    }

    @Override
    public ILuaTable createTable() {
        return new CobaltLuaTable(new LuaTable());
    }

    @Override
    public ILuaValue wrapUserdata(Object userdata) {
        return new CobaltLuaValue(new LuaUserdata(userdata));
    }

    @Override
    public ILuaValue wrapString(String value) {
        return new CobaltLuaValue(ValueFactory.valueOf(value));
    }

    @Override
    public ILuaValue wrapNumber(double value) {
        return new CobaltLuaValue(ValueFactory.valueOf(value));
    }

    @Override
    public ILuaValue wrapBoolean(boolean value) {
        return new CobaltLuaValue(ValueFactory.valueOf(value));
    }

    @Override
    public ILuaValue nilValue() {
        return new CobaltLuaValue(Constants.NIL);
    }
}
