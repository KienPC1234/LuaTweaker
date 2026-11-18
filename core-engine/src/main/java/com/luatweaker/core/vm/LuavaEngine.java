package com.luatweaker.core.vm;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.bind.DynamicJavaProxy;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.mod.LuaModPathSecurity;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.binding.JavaAccessPolicy;
import org.luava.runtime.LuaException;
import org.luava.runtime.LuaFunction;
import org.luava.runtime.LuaNil;
import org.luava.runtime.LuaState;
import org.luava.runtime.LuaTable;
import org.luava.runtime.LuaUserdata;
import org.luava.runtime.LuaValue;
import org.luava.runtime.bytecode.BytecodeVM;
import org.luava.runtime.bytecode.LuaClosure;
import org.luava.runtime.concurrency.LuaCoroutine;
import org.luava.runtime.eval.CallStack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * luava-backed implementation of {@link ILuaEngine}.
 *
 * <p>Provides the full engine contract: globals, print/log routing, recipe DSL userdata, native
 * require, deferred task runner and error tracking, all on the luava 0.2.5 runtime. Every VM
 * entry point is synchronized because the server thread and the render thread share one state.</p>
 */
public class LuavaEngine implements ILuaEngine {

    private static final String DEFAULT_LUAMODS_DIR = "luamods";
    private static final String DEFAULT_LUA_DIR = "lua";
    private static final String BOOTSTRAP_RESOURCE = "/lua/luatweaker_bootstrap.lua";
    private static final int MILLIS_PER_SECOND = 1000;

    private static final Pattern MOD_ID_PATTERN =
            Pattern.compile("([A-Za-z0-9_]+)[/\\\\][^/\\\\]+\\.lua");
    private static final Pattern LUAMODS_SOURCE_PATTERN = Pattern.compile("luamods[/\\\\](.+)");

    private final LuaState state;
    private final Map<String, LuaValue> moduleCache = new ConcurrentHashMap<>();
    private final ThreadLocal<Deque<String>> activeModuleStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Serializes Lua execution between external threads (server tick, render thread, reload).
     * Coroutine threads started by {@code task._run_deferred} belong to the external operation
     * that resumed them and therefore must NOT take this gate: the resuming thread is parked
     * waiting for the coroutine, so it cannot be executing Lua at the same time. Nested external
     * calls are tracked by {@link #externalDepth} so the gate is released only once.
     */
    private final Semaphore executionGate = new Semaphore(1, true);
    private final ThreadLocal<Integer> externalDepth = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> COROUTINE_THREAD = new ThreadLocal<>();

    /** Suspended/dead coroutines created by deferred tasks, used to stop them on reload. */
    private final Set<LuaCoroutine> liveCoroutines = ConcurrentHashMap.newKeySet();

    private volatile String lastExecutionError;
    private volatile boolean closed;
    private File luaDirectory;
    private boolean debugMode;

    /** Creates an engine with debug logging disabled. */
    public LuavaEngine() {
        this(false);
    }

    /**
     * Creates an engine.
     *
     * @param debugMode whether the {@code log.debug} Lua function emits output
     */
    public LuavaEngine(boolean debugMode) {
        this.debugMode = debugMode;
        this.state = new LuaState();
        configureJavaAccessPolicy();
        setupGlobalBindings();
    }

    /**
     * Returns and clears the error recorded by the most recent execution, if any.
     *
     * @return the recorded error message, or {@code null} when the last execution succeeded
     */
    /**
     * Enters an operation that may execute Lua. External threads take the gate; coroutine threads
     * belong to the operation that resumed them and therefore run without it.
     */
    /**
     * True when the caller is running inside any luava coroutine, including ones created by
     * Lua's own {@code coroutine.create}/{@code wrap}, not only {@code task._run_deferred}.
     */
    private static boolean isCoroutineThread() {
        if (Boolean.TRUE.equals(COROUTINE_THREAD.get())) {
            return true;
        }
        LuaCoroutine running = LuaCoroutine.running();
        return running != null && !running.isMainThread();
    }

    private void enterExecution() {
        if (isCoroutineThread()) {
            return;
        }
        Integer depth = externalDepth.get();
        if (depth == null) {
            executionGate.acquireUninterruptibly();
            externalDepth.set(1);
        } else {
            externalDepth.set(depth + 1);
        }
    }

    /** Leaves the operation, releasing the gate when the outermost call returns. */
    private void exitExecution() {
        if (isCoroutineThread()) {
            return;
        }
        Integer depth = externalDepth.get();
        if (depth == null) {
            return;
        }
        if (depth <= 1) {
            externalDepth.remove();
            executionGate.release();
        } else {
            externalDepth.set(depth - 1);
        }
    }

    private void checkRunning() {
        if (closed) {
            throw new IllegalStateException("Lua engine is shut down");
        }
    }

    @Override
    public @Nullable String getAndClearLastExecutionError() {
        String error = lastExecutionError;
        lastExecutionError = null;
        return error;
    }

    @Override
    public void setLuaDirectory(@NotNull File luaDirectory) {
        this.luaDirectory = luaDirectory;
    }

    // ---- VM setup -------------------------------------------------------

    /**
     * Keeps luava's default Java access policy: dangerous reflective entry points are blocked,
     * while cross-mod Java interop (the reason the dynamic bridge exists) stays available.
     */
    private void configureJavaAccessPolicy() {
        state.javaPolicy(JavaAccessPolicy.DEFAULT);
    }

    private void setupGlobalBindings() {
        LuaTable globals = state.getGlobals();
        globals.rawset(key("_G"), globals);
        globals.rawset(key("DEBUG"), LuaValue.valueOf(debugMode));

        globals.rawset(key("print"), LuaFunction.of(args -> {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    builder.append('\t');
                }
                builder.append(args[i] == null ? LuaNil.NIL.toLuaString() : args[i].toLuaString());
            }
            String message = builder.toString();
            AsyncFileLogger.get().info(LogStage.SCRIPT_LOAD, message);
            String modId = resolveModId();
            if (modId != null) {
                AsyncFileLogger.get().logMod(modId, "INFO", message);
            }
            return LuaNil.NIL;
        }));

        LuaTable logTable = new LuaTable();
        logTable.rawset(key("info"), LuaFunction.of(args -> {
            String message = firstArg(args);
            AsyncFileLogger.get().info(LogStage.SCRIPT_LOAD, message);
            writeModLog("INFO", message);
            return LuaNil.NIL;
        }));
        logTable.rawset(key("warn"), LuaFunction.of(args -> {
            String message = firstArg(args);
            AsyncFileLogger.get().warn(LogStage.SCRIPT_LOAD, message);
            writeModLog("WARN", message);
            return LuaNil.NIL;
        }));
        logTable.rawset(key("error"), LuaFunction.of(args -> {
            String message = firstArg(args);
            AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR, message);
            writeModLog("ERROR", message);
            return LuaNil.NIL;
        }));
        logTable.rawset(key("debug"), LuaFunction.of(args -> {
            if (debugMode) {
                String message = firstArg(args);
                AsyncFileLogger.get().info(LogStage.SCRIPT_LOAD, "[DEBUG] " + message);
                writeModLog("DEBUG", message);
            }
            return LuaNil.NIL;
        }));
        globals.rawset(key("log"), logTable);

        LuaTable osTable = new LuaTable();
        osTable.rawset(key("clock"), LuaFunction.of(args ->
                LuaValue.valueOf(System.currentTimeMillis() / (double) MILLIS_PER_SECOND)));
        osTable.rawset(key("time"), LuaFunction.of(args ->
                LuaValue.valueOf(System.currentTimeMillis() / MILLIS_PER_SECOND)));
        globals.rawset(key("os"), osTable);

        LuaTable modTable = new LuaTable();
        modTable.rawset(key("GetService"), LuaFunction.of(args -> {
            if (args.length < 2 || argAt(args, 1).isNil()) {
                throw LuaValue.argError(2, "string", "got nil");
            }
            String name = toJavaString(args[1]);
            Object service = LuaServiceRegistry.get(name);
            if (service == null) {
                throw new LuaException("Service '" + name + "' not found");
            }
            if (service instanceof LuavaLuaTable luavaTable) {
                return luavaTable.getLuavaTable();
            }
            return toLuavaValue(wrapDynamicJavaObject(service));
        }));
        globals.rawset(key("Mod"), modTable);
        globals.rawset(key("game"), modTable);

        globals.rawset(key("item"), LuaFunction.of(this::createItemFromArgs));
        globals.rawset(key("ingredient"), LuaFunction.of(this::createIngredientFromArgs));
        globals.rawset(key("tag"), LuaFunction.of(this::createTagFromArgs));
        globals.rawset(key("oredict"), LuaFunction.of(this::createTagFromArgs));

        loadBootstrapResource(BOOTSTRAP_RESOURCE);
        installDeferredRunner(globals);
        setupNativeRequire(globals);

        state.resourceLoader(this::loadResource);
    }

    // ---- Recipe DSL userdata --------------------------------------------

    private LuaValue createItemFromArgs(LuaValue... args) {
        LuaValue first = args.length > 0 && args[0] != null ? args[0] : LuaNil.NIL;
        if (first instanceof LuaTable tbl) {
            LuaValue idVal = tbl.rawget(key("id"));
            if (idVal.isNil()) {
                idVal = tbl.rawget(key("item"));
            }
            if (idVal.isNil()) {
                throw LuaValue.argError(1, "table with 'id' or 'item'", "got nil");
            }
            int count = readInt(tbl.rawget(key("count")), 1);
            ItemCount item = new ItemCount(toJavaString(idVal), count);
            LuaValue damage = tbl.rawget(key("damage"));
            if (damage.isNumber()) {
                item = item.withDamage((int) damage.toLong());
            }
            LuaValue name = tbl.rawget(key("name"));
            if (name.isString()) {
                item = item.withName(toJavaString(name));
            }
            LuaValue nbt = tbl.rawget(key("nbt"));
            if (!nbt.isNil()) {
                item = item.withNbt(toJavaString(nbt));
            }
            return ItemUserdataFactory.createItemUserdataLuava(state, item);
        }

        if (first.isNil()) {
            throw LuaValue.argError(1, "string", "got nil");
        }
        String itemId = toJavaString(first);
        if (itemId.startsWith("#") || itemId.startsWith("tag:") || itemId.startsWith("oredict:")) {
            return ItemUserdataFactory.createIngredientUserdataLuava(state, new IngredientWrapper(itemId));
        }

        int count = args.length < 2 || args[1].isNil() ? 1 : readInt(args[1], 1);
        ItemCount item = new ItemCount(itemId, count);
        if (args.length >= 3 && !args[2].isNil()) {
            LuaValue third = args[2];
            if (third instanceof LuaTable opts) {
                LuaValue damage = opts.rawget(key("damage"));
                if (damage.isNumber()) {
                    item = item.withDamage((int) damage.toLong());
                }
                LuaValue name = opts.rawget(key("name"));
                if (name.isString()) {
                    item = item.withName(toJavaString(name));
                }
                LuaValue nbt = opts.rawget(key("nbt"));
                if (!nbt.isNil()) {
                    item = item.withNbt(toJavaString(nbt));
                }
            } else {
                item = item.withNbt(toJavaString(third));
            }
        }
        return ItemUserdataFactory.createItemUserdataLuava(state, item);
    }

    private LuaValue createIngredientFromArgs(LuaValue... args) {
        if (args.length < 1 || args[0].isNil()) {
            throw LuaValue.argError(1, "string", "got nil");
        }
        return ItemUserdataFactory.createIngredientUserdataLuava(
                state, new IngredientWrapper(toJavaString(args[0])));
    }

    private LuaValue createTagFromArgs(LuaValue... args) {
        if (args.length < 1 || args[0].isNil()) {
            throw LuaValue.argError(1, "string", "got nil");
        }
        String descriptor = toJavaString(args[0]);
        if (!descriptor.startsWith("#")) {
            descriptor = "#" + descriptor;
        }
        return ItemUserdataFactory.createIngredientUserdataLuava(
                state, new IngredientWrapper(descriptor));
    }

    // ---- Bootstrap and deferred tasks -----------------------------------

    private void loadBootstrapResource(@NotNull String resourcePath) {
        try (InputStream stream = LuavaEngine.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                AsyncFileLogger.get().error(LogStage.SYSTEM,
                        "Bootstrap resource file not found: " + resourcePath);
                return;
            }
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            state.eval(source, resourcePath);
            AsyncFileLogger.get().info(LogStage.SYSTEM,
                    "Successfully executed bootstrap resource: " + resourcePath);
        } catch (LuaException e) {
            AsyncFileLogger.get().error(LogStage.SYSTEM,
                    "Failed to load system bootstrap script '" + resourcePath + "': "
                            + describeLuaException(e));
        } catch (IOException e) {
            AsyncFileLogger.get().error(LogStage.SYSTEM,
                    "Failed to read system bootstrap script '" + resourcePath + "': " + e.getMessage());
        }
    }

    /**
     * Java binding for {@code task._run_deferred}.
     *
     * <p>luava runs a coroutine on its own virtual thread and {@code resume()} parks the caller
     * until the coroutine yields or dies. The coroutine is marked as belonging to the current
     * operation so that Java bindings it calls (for example {@code task.getTimeClock}) do not try
     * to take {@link #executionGate} while the tick thread is parked waiting for it.</p>
     *
     * <p>This is synchronous with respect to {@code task._tick}: the call returns only when the
     * task yields at {@code Task.wait} or finishes. A script that never yields still blocks the
     * tick, exactly like a blocking {@code Task.wait} loop always did; the timeout guards for
     * runaway scripts belong to the script author (or a future luava instruction guard).</p>
     */
    private void installDeferredRunner(@NotNull LuaTable globals) {
        LuaValue taskValue = globals.rawget(key("task"));
        if (!(taskValue instanceof LuaTable taskTable)) {
            AsyncFileLogger.get().error(LogStage.SYSTEM,
                    "Bootstrap did not define the 'task' table; deferred tasks are unavailable");
            return;
        }
        taskTable.rawset(key("_run_deferred"), LuaFunction.of(args -> {
            LuaValue functionValue = argAt(args, 0);
            if (!(functionValue instanceof LuaFunction function)) {
                return LuaNil.NIL;
            }
            List<LuaValue> callArgs = new ArrayList<>();
            LuaValue argsValue = argAt(args, 1);
            if (argsValue instanceof LuaTable argsTable) {
                int length = argsTable.rawlen();
                for (int i = 1; i <= length; i++) {
                    callArgs.add(argsTable.rawgetInt(i));
                }
            }

            String moduleHint = sourceModuleHint(function);
            LuaFunction marked = LuaFunction.of(coroutineArgs -> {
                COROUTINE_THREAD.set(Boolean.TRUE);
                if (moduleHint != null) {
                    activeModuleStack.get().push(moduleHint);
                }
                try {
                    return function.call(coroutineArgs);
                } finally {
                    if (moduleHint != null) {
                        activeModuleStack.get().pop();
                    }
                    COROUTINE_THREAD.remove();
                }
            });
            LuaCoroutine coroutine = new LuaCoroutine(marked);
            liveCoroutines.add(coroutine);
            try {
                coroutine.resume(callArgs.toArray(new LuaValue[0]));
            } catch (LuaException e) {
                AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                        "Deferred task failed: " + describeLuaException(e));
            } finally {
                if (coroutine.getStatus() == LuaCoroutine.Status.DEAD) {
                    liveCoroutines.remove(coroutine);
                }
            }
            return coroutine;
        }));
    }

    // ---- require --------------------------------------------------------

    private void setupNativeRequire(@NotNull LuaTable globals) {
        LuaValue oldRequire = globals.rawget(key("require"));
        if (oldRequire != null && !oldRequire.isNil()) {
            globals.rawset(key("_oldRequire"), oldRequire);
        }
        globals.rawset(key("require"), LuaFunction.of(args -> {
            if (args.length < 1 || args[0] == null || args[0].isNil()) {
                throw new LuaException("require requires 1 argument (moduleName)");
            }
            String modName = toJavaString(args[0]);

            if (modName.startsWith(".")) {
                if (modName.startsWith("..")) {
                    throw new LuaException(
                            "Parent directory traversal ('..') is forbidden for Sibling Require security.");
                }
                String currentModule = activeModuleStack.get().peek();
                if (currentModule != null && !currentModule.isBlank()) {
                    if (currentModule.endsWith(".lua")) {
                        currentModule = currentModule.substring(0, currentModule.length() - 4);
                    }
                    currentModule = currentModule.replace('/', '.').replace('\\', '.');
                    int firstDot = currentModule.indexOf('.');
                    String modRoot = firstDot > 0 ? currentModule.substring(0, firstDot) : currentModule;
                    modName = modRoot + "." + modName.substring(1);
                } else {
                    modName = modName.substring(1);
                }
            }

            LuaValue result = moduleCache.get(modName);
            if (result == null) {
                result = NativeModuleResolver.resolveLuava(getGlobalEnvironment(), modName);
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

            LuaValue oldRequireFunction = globals.rawget(key("_oldRequire"));
            if (oldRequireFunction instanceof LuaFunction function) {
                return function.call(args);
            }
            throw new LuaException("Module not found: " + modName);
        }));
    }

    private @NotNull LuaValue resolveFileModule(@NotNull String modName) {
        File root = resolveLuaRoot();
        if (root == null) {
            return LuaNil.NIL;
        }
        String relativeName = modName.replace('.', '/');
        List<String> candidates = List.of(
                relativeName + ".lua",
                relativeName + "/init.lua",
                "lib/" + relativeName + ".lua",
                "lib/" + relativeName + "/init.lua"
        );

        for (String candidate : candidates) {
            LuaValue loaded = tryLoadModuleFile(root, candidate, modName);
            if (loaded != null && !loaded.isNil()) {
                return loaded;
            }
        }

        File[] subModules = root.listFiles();
        if (subModules != null) {
            for (File modFolder : subModules) {
                if (!modFolder.isDirectory()) {
                    continue;
                }
                String folderPrefix = modFolder.getName() + "/";
                LuaValue loaded = tryLoadModuleFile(root, folderPrefix + relativeName + ".lua", modName);
                if (loaded == null || loaded.isNil()) {
                    loaded = tryLoadModuleFile(root, folderPrefix + relativeName + "/init.lua", modName);
                }
                if (loaded != null && !loaded.isNil()) {
                    return loaded;
                }
            }
        }
        return LuaNil.NIL;
    }

    private @Nullable LuaValue tryLoadModuleFile(@NotNull File root, @NotNull String relativePath,
                                                 @NotNull String modName) {
        Path path;
        try {
            path = LuaModPathSecurity.resolveContained(root.toPath(), relativePath);
        } catch (IllegalArgumentException e) {
            AsyncFileLogger.get().warn(LogStage.SYSTEM,
                    "Rejected require path '" + relativePath + "': " + e.getMessage());
            return null;
        }
        if (!Files.isRegularFile(path)) {
            return null;
        }
        activeModuleStack.get().push(modName);
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            LuaValue returned = state.eval(source, path.getFileName().toString());
            return returned != null && !returned.isNil() ? returned : LuaValue.valueOf(true);
        } catch (LuaException e) {
            AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                    "Failed to load Lua module file '" + relativePath + "': " + describeLuaException(e));
            return LuaNil.NIL;
        } catch (IOException e) {
            AsyncFileLogger.get().error(LogStage.SCRIPT_LOAD,
                    "Failed to read Lua module file '" + relativePath + "': " + e.getMessage());
            return LuaNil.NIL;
        } catch (Throwable e) {
            AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                    "Unexpected error loading Lua module file '" + relativePath + "': " + e);
            return LuaNil.NIL;
        } finally {
            activeModuleStack.get().pop();
        }
    }

    /** Returns the configured lua root, falling back to luamods/ then lua/. */
    private @Nullable File resolveLuaRoot() {
        if (luaDirectory != null && luaDirectory.exists()) {
            return luaDirectory;
        }
        File fallback = new File(DEFAULT_LUAMODS_DIR);
        if (fallback.exists()) {
            return fallback;
        }
        fallback = new File(DEFAULT_LUA_DIR);
        return fallback.exists() ? fallback : null;
    }

    /** Loader used by luava's package.searchers; returns null for missing or unsafe paths. */
    private @Nullable String loadResource(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("..") || normalized.indexOf(':') >= 0) {
            AsyncFileLogger.get().warn(LogStage.SYSTEM,
                    "Resource loader rejected path (traversal or absolute): " + path);
            return null;
        }
        File root = resolveLuaRoot();
        if (root == null) {
            return null;
        }
        try {
            Path resolved = LuaModPathSecurity.resolveContained(root.toPath(), normalized);
            if (!Files.isRegularFile(resolved)) {
                return null;
            }
            return Files.readString(resolved, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | IOException e) {
            AsyncFileLogger.get().warn(LogStage.SYSTEM,
                    "Resource loader rejected path '" + path + "': " + e.getMessage());
            return null;
        }
    }

    // ---- Execution ------------------------------------------------------

    @Override
    public void executeString(@NotNull String code, @NotNull String name) {
        checkRunning();
        enterExecution();
        activeModuleStack.get().push(name);
        try {
            state.eval(code, name);
        } catch (LuaException e) {
            String message = describeLuaException(e);
            lastExecutionError = message;
            AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                    "Error running bootstrap script " + name + ": " + message);
        } catch (Throwable e) {
            String message = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage() : e.toString();
            lastExecutionError = message;
            AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                    "Unexpected error running bootstrap script " + name + ": " + message);
        } finally {
            activeModuleStack.get().pop();
            exitExecution();
        }
    }

    @Override
    public void executeScript(@NotNull File file, @NotNull String context) {
        if (!file.exists()) {
            AsyncFileLogger.get().warn(LogStage.SCRIPT_LOAD,
                    "Script file does not exist: " + file.getAbsolutePath());
            return;
        }
        checkRunning();
        enterExecution();
        String chunkName = context + "/" + file.getName();
        activeModuleStack.get().push(chunkName);
        try {
            String source = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            state.eval(source, file.getName());
            AsyncFileLogger.get().info(LogStage.SCRIPT_LOAD,
                    "Successfully executed script: " + file.getName());
        } catch (LuaException e) {
            String message = describeLuaException(e);
            lastExecutionError = message;
            logFancyScriptError(context, file, message, isCompileErrorMessage(message));
        } catch (IOException e) {
            String message = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage() : e.toString();
            lastExecutionError = message;
            AsyncFileLogger.get().error(LogStage.SCRIPT_LOAD,
                    "Failed to read script " + file.getName() + ": " + message);
        } catch (Throwable e) {
            String message = e.getMessage() != null && !e.getMessage().isBlank()
                    ? e.getMessage() : e.toString();
            lastExecutionError = message;
            AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                    "Unexpected error running script " + file.getName() + ": " + message);
        } finally {
            activeModuleStack.get().pop();
            exitExecution();
        }
    }

    /** Distinguishes parser errors from runtime errors by their message text. */
    private static boolean isCompileErrorMessage(@NotNull String details) {
        return details.contains("syntax error")
                || details.contains("unexpected symbol")
                || details.contains(" expected ")
                || details.contains("near <eof>");
    }

    /** Renders the same fancy traceback shape as the former Cobalt-based linter. */
    private void logFancyScriptError(@NotNull String context, @NotNull File file,
                                     @NotNull String details, boolean compileTime) {
        int line = 1;
        java.util.regex.Matcher matcher = Pattern.compile(":(\\d+):").matcher(details);
        if (matcher.find()) {
            try {
                line = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // Keep the default line when the message carries no usable number.
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append("========================================================================\n");
        builder.append("                       ")
                .append(compileTime ? "LUA SYNTAX ERROR (COMPILE TIME)" : "LUA RUNTIME EXCEPTION")
                .append("\n");
        builder.append("========================================================================\n");
        builder.append("Source File : ").append(file.getName()).append("\n");
        builder.append("Line Number : ").append(line).append("\n");
        builder.append("Details     : ").append(details).append("\n\n");
        try {
            java.util.List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int start = Math.max(1, line - 2);
            int end = Math.min(lines.size(), line + 2);
            for (int index = start; index <= end; index++) {
                String content = lines.get(index - 1);
                if (index == line) {
                    builder.append(String.format("  > %3d | %s%n", index, content));
                    builder.append(String.format("        | %s%n", "^".repeat(Math.max(1, content.length()))));
                } else {
                    builder.append(String.format("    %3d | %s%n", index, content));
                }
            }
        } catch (IOException e) {
            AsyncFileLogger.get().warn(LogStage.SYSTEM,
                    "Cannot read source excerpt for " + file.getName() + ": " + e.getMessage());
        }
        builder.append("========================================================================");
        AsyncFileLogger.get().error(LogStage.SCRIPT_LOAD, "[" + context + "] " + builder);
    }

    @Override
    public @NotNull ILuaValue callFunction(@NotNull ILuaValue function,
                                           @NotNull ILuaValue... args) {
        ILuaValue[] results = callFunctionMulti(function, args);
        return results.length > 0 ? results[0] : nilValue();
    }

    @Override
    public @NotNull ILuaValue[] callFunctionMulti(@NotNull ILuaValue function,
                                                  @NotNull ILuaValue... args) {
        checkRunning();
        enterExecution();
        try {
            if (function.isNil()) {
                return new ILuaValue[0];
            }
            try {
                LuaValue rawFunction = function instanceof LuavaLuaValue luavaValue
                        ? luavaValue.getLuavaValue() : toLuavaValue(function);
                LuaValue[] luaArgs = new LuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    luaArgs[i] = toLuavaValue(args[i]);
                }

                LuaValue[] results;
                if (rawFunction instanceof LuaClosure closure) {
                    results = BytecodeVM.execute(state, closure, luaArgs);
                } else {
                    results = new LuaValue[]{rawFunction.call(luaArgs)};
                }

                ILuaValue[] wrapped = new ILuaValue[results.length];
                for (int i = 0; i < results.length; i++) {
                    wrapped[i] = LuavaLuaValue.wrap(results[i] == null ? LuaNil.NIL : results[i]);
                }
                return wrapped;
            } catch (LuaException e) {
                String rawMessage = e.getMessage();
                if (rawMessage == null || rawMessage.isBlank()) {
                    AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                            "Lua exception without message during callback execution: " + e);
                }
                String message = describeLuaException(e);
                lastExecutionError = message;
                AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                        "Lua error executing callback: " + message);
            } catch (Throwable e) {
                String message = e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage() : e.toString();
                lastExecutionError = message;
                AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                        "Lua error executing callback: " + message);
            }
            return new ILuaValue[0];
        } finally {
            exitExecution();
        }
    }

    // ---- SPI bindings ---------------------------------------------------

    @Override
    public void registerService(@NotNull String name, @NotNull ILuaTable service) {
        LuaServiceRegistry.register(name, service);
    }

    @Override
    public void registerService(@NotNull String name, @NotNull Object service) {
        LuaServiceRegistry.register(name, service);
    }

    @Override
    public void registerGlobal(@NotNull String name, @NotNull ILuaValue value) {
        checkRunning();
        enterExecution();
        try {
            state.getGlobals().rawset(key(name), toLuavaValue(value));
        } finally {
            exitExecution();
        }
    }

    @Override
    public void registerGlobal(@NotNull String name, @NotNull ILuaFunction function) {
        checkRunning();
        enterExecution();
        try {
            state.getGlobals().rawset(key(name), LuaFunction.of(args -> {
                ILuaValue[] wrappedArgs = new ILuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    wrappedArgs[i] = LuavaLuaValue.wrap(args[i] == null ? LuaNil.NIL : args[i]);
                }
                try {
                    ILuaValue result = function.invoke(wrappedArgs);
                    return result == null ? LuaNil.NIL : toLuavaValue(result);
                } catch (LuaException e) {
                    throw e;
                } catch (Exception e) {
                    String message = e.getMessage() != null ? e.getMessage() : e.toString();
                    throw new LuaException("Lua binding '" + name + "' failed: " + message);
                }
            }));
        } finally {
            exitExecution();
        }
    }

    @Override
    public @NotNull ILuaTable getGlobalEnvironment() {
        enterExecution();
        try {
            return new LuavaLuaTable(state.getGlobals());
        } finally {
            exitExecution();
        }
    }

    @Override
    public @NotNull ILuaTable createTable() {
        return new LuavaLuaTable(new LuaTable());
    }

    @Override
    public @NotNull ILuaValue wrapUserdata(@NotNull Object userdata) {
        return new LuavaLuaValue(new LuaUserdata(userdata));
    }

    @Override
    public @NotNull ILuaValue wrapString(@NotNull String value) {
        return new LuavaLuaValue(LuaValue.valueOf(value));
    }

    @Override
    public @NotNull ILuaValue wrapNumber(double value) {
        return new LuavaLuaValue(LuaValue.valueOf(value));
    }

    @Override
    public @NotNull ILuaValue wrapBoolean(boolean value) {
        return new LuavaLuaValue(LuaValue.valueOf(value));
    }

    @Override
    public @NotNull ILuaValue nilValue() {
        return new LuavaLuaValue(LuaNil.NIL);
    }

    @Override
    public @NotNull ILuaValue toLuaValue(@Nullable Object obj) {
        if (obj == null) {
            return nilValue();
        }
        if (obj instanceof ILuaValue luaValue) {
            return luaValue;
        }
        if (obj instanceof String string) {
            return wrapString(string);
        }
        if (obj instanceof Number number) {
            return wrapNumber(number.doubleValue());
        }
        if (obj instanceof Boolean bool) {
            return wrapBoolean(bool);
        }
        if (obj instanceof ILuaTable table) {
            return table;
        }
        if (obj instanceof Map<?, ?> map) {
            ILuaTable table = createTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                table.rawset(String.valueOf(entry.getKey()), toLuaValue(entry.getValue()));
            }
            return table;
        }
        if (obj instanceof List<?> list) {
            ILuaTable table = createTable();
            int index = 1;
            for (Object item : list) {
                table.rawset(index++, toLuaValue(item));
            }
            return table;
        }
        return wrapUserdata(obj);
    }

    @Override
    public @NotNull ILuaValue wrapDynamicJavaObject(@NotNull Object obj) {
        return new LuavaLuaValue(DynamicJavaProxy.createLuava(state, obj));
    }

    @Override
    public void runExclusive(@NotNull Runnable action) {
        checkRunning();
        enterExecution();
        try {
            action.run();
        } finally {
            exitExecution();
        }
    }

    @Override
    public void shutdown() {
        closed = true;
        for (LuaCoroutine coroutine : liveCoroutines) {
            try {
                if (coroutine.getStatus() == LuaCoroutine.Status.SUSPENDED) {
                    coroutine.close();
                }
            } catch (RuntimeException e) {
                AsyncFileLogger.get().warn(LogStage.RUNTIME_ERROR,
                        "Failed to close deferred coroutine during shutdown: " + e.getMessage());
            }
        }
        liveCoroutines.clear();
    }

    // ---- Helpers --------------------------------------------------------

    private @Nullable LuaValue toLuavaValue(@Nullable ILuaValue value) {
        if (value == null || value.isNil()) {
            return LuaNil.NIL;
        }
        if (value instanceof LuavaLuaTable luavaTable) {
            return luavaTable.getLuavaTable();
        }
        if (value instanceof LuavaLuaValue luavaValue) {
            return luavaValue.getLuavaValue();
        }
        Object raw = value.toJavaObject();
        if (raw instanceof String string) {
            return LuaValue.valueOf(string);
        }
        if (raw instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        if (raw instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }
        if (raw instanceof LuaValue luaValue) {
            return luaValue;
        }
        return new LuaUserdata(raw);
    }

    private static @NotNull LuaValue key(@NotNull String name) {
        return LuaValue.valueOf(name);
    }

    private static @NotNull LuaValue argAt(@NotNull LuaValue[] args, int index) {
        return index < args.length && args[index] != null ? args[index] : LuaNil.NIL;
    }

    private static @NotNull String firstArg(@NotNull LuaValue[] args) {
        LuaValue value = args.length > 0 && args[0] != null ? args[0] : LuaNil.NIL;
        return value.isNil() ? "" : value.toLuaString();
    }

    private static @NotNull String toJavaString(@NotNull LuaValue value) {
        return value.isNil() ? "" : value.toLuaString();
    }

    private static int readInt(@NotNull LuaValue value, int fallback) {
        return value.isNumber() ? (int) value.toLong() : fallback;
    }

    private @Nullable String getActiveModId() {
        String currentModule = activeModuleStack.get().peek();
        if (currentModule == null || currentModule.isBlank()) {
            return null;
        }
        int slash = currentModule.indexOf('/');
        if (slash > 0) {
            return currentModule.substring(0, slash);
        }
        int dot = currentModule.indexOf('.');
        return dot > 0 ? currentModule.substring(0, dot) : currentModule;
    }

    /** Resolves the mod id for the current call, including deferred coroutine chunks. */
    private @Nullable String resolveModId() {
        return getActiveModId();
    }

    private void writeModLog(@NotNull String level, @NotNull String message) {
        String modId = resolveModId();
        if (modId != null) {
            AsyncFileLogger.get().logMod(modId, level, message);
        }
    }

    private static @Nullable String sourceModuleHint(@NotNull LuaFunction function) {
        String source = function.getSource();
        if (source == null) {
            return null;
        }
        Matcher luamods = LUAMODS_SOURCE_PATTERN.matcher(source);
        if (luamods.find()) {
            return luamods.group(1);
        }
        Matcher script = MOD_ID_PATTERN.matcher(source);
        return script.find() ? script.group(0) : null;
    }

    private static @NotNull String describeLuaException(@NotNull LuaException exception) {
        StringBuilder builder = new StringBuilder();
        String message = exception.getMessage();
        builder.append(message != null && !message.isBlank() ? message : exception.toString());
        int line = exception.getLine();
        if (line > 0) {
            builder.append(" (line ").append(line).append(')');
        }
        List<CallStack.Frame> frames = exception.getLuaFrames();
        if (frames != null && !frames.isEmpty()) {
            builder.append(System.lineSeparator()).append("Lua stack:");
            for (CallStack.Frame frame : frames) {
                String source = frame.function != null ? frame.function.getSource() : "?";
                builder.append(System.lineSeparator()).append("  at ")
                        .append(frame.name != null ? frame.name : "?")
                        .append(" (").append(source != null ? source : "?")
                        .append(':').append(frame.line).append(')');
            }
        }
        return builder.toString();
    }
}
