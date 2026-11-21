package com.luatweaker.core.vm;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.bind.DynamicJavaProxy;
import com.luatweaker.core.bind.JsonLuaBinding;
import com.luatweaker.core.json.JsonServiceImpl;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * luava-backed implementation of {@link ILuaEngine}.
 *
 * <p>Provides the full engine contract: globals, print/log routing, recipe DSL userdata, native
 * require, deferred task runner and error tracking, all on the luava 0.2.7 runtime. Every VM
 * entry point is synchronized because the server thread and the render thread share one state.</p>
 */
public class LuavaEngine implements ILuaEngine {

    private static final String DEFAULT_LUAMODS_DIR = "luamods";
    private static final String DEFAULT_LUA_DIR = "lua";
    private static final String BOOTSTRAP_RESOURCE = "/lua/luatweaker_bootstrap.lua";
    private static final String BRAIN_RESOURCE = "/lua/luatweaker_brain.lua";
    private static final int MILLIS_PER_SECOND = 1000;
    private static final int OUTERMOST_EXECUTION_DEPTH = 1;

    /** Lua value type names and argument errors shared by the recipe DSL bindings. */
    private static final String TYPE_STRING = "string";
    private static final String ARG_GOT_NIL = "got nil";
    private static final String LUA_EXTENSION = ".lua";
    private static final String LEVEL_ERROR = "ERROR";
    /** Capacity for one {@code print(...)} line; scripts rarely exceed a short line. */
    private static final int PRINT_CAPACITY = 128;
    /** Index of the optional options argument in {@code item(id, count, options)}. */
    private static final int OPTIONS_ARG_INDEX = 2;

    private final LuaState state;

    /** Resource caps enforced on {@link #state}; {@link LuaEngineLimits#UNLIMITED} when unset. */
    private final LuaEngineLimits resourceLimits;

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

    /**
     * A coroutine that yields is removed from {@link #liveCoroutines} only when it is closed
     * or pruned: the resume that eventually kills it happens in Lua ({@code coroutine.resume}
     * inside a delayed callback), which cannot touch this set. Pruning at this threshold keeps
     * the set bounded without walking it on every spawn.
     */
    private static final int COROUTINE_PRUNE_THRESHOLD = 512;

    /**
     * Last script error, published for {@link #getAndClearLastExecutionError()} and read from
     * other threads; an {@link AtomicReference} keeps the hand-off visible without a volatile
     * field (luava callbacks run on both the server and the render thread).
     */
    private final AtomicReference<String> lastExecutionError = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
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
        this(debugMode, LuaEngineLimits.UNLIMITED);
    }

    /**
     * Creates an engine with explicit resource caps.
     *
     * @param debugMode whether the {@code log.debug} Lua function emits output
     * @param limits    resource caps applied before the Lua bootstrap runs; never {@code null}
     */
    public LuavaEngine(boolean debugMode, @NotNull LuaEngineLimits limits) {
        this.debugMode = debugMode;
        this.resourceLimits = Objects.requireNonNull(limits, "limits");
        this.state = new LuaState();
        applyLimits();
        configureJavaAccessPolicy();
        setupGlobalBindings();
    }

    /**
     * Applies the configured caps to the luava state. Runs before the Lua bootstrap so a runaway
     * bootstrap chunk is already covered, and skips every {@code 0} value so an unlimited engine
     * keeps luava's defaults untouched.
     */
    private void applyLimits() {
        if (resourceLimits.maxMemoryBytes() > 0) {
            state.maxMemoryBytes(resourceLimits.maxMemoryBytes());
        }
        if (resourceLimits.maxCallDepth() > 0) {
            state.maxCallDepth(resourceLimits.maxCallDepth());
            // luava's LuaState#isJitEnabled() returns false while a call-depth cap is active.
            // Surface the trade-off instead of letting the VM silently get slower.
            AsyncFileLogger.get().warn(LogStage.SYSTEM,
                    "maxCallDepth=" + resourceLimits.maxCallDepth()
                            + " is set; luava disables its JIT while a call-depth cap is active");
        }
        if (resourceLimits.maxCoroutines() > 0) {
            state.maxCoroutines(resourceLimits.maxCoroutines());
        }
        if (resourceLimits.isAnyLimitSet()) {
            AsyncFileLogger.get().info(LogStage.SYSTEM, "Lua engine limits: " + resourceLimits.describe());
        }
    }

    /** Package-private for tests: the caps this engine enforces. */
    @NotNull LuaEngineLimits limits() {
        return resourceLimits;
    }

    /** Package-private for tests: the luava state the caps were applied to. */
    @NotNull LuaState rawState() {
        return state;
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
        if (depth <= OUTERMOST_EXECUTION_DEPTH) {
            externalDepth.remove();
            executionGate.release();
        } else {
            externalDepth.set(depth - 1);
        }
    }

    private void checkRunning() {
        if (closed.get()) {
            throw new IllegalStateException("Lua engine is shut down");
        }
    }

    @Override
    public @Nullable String getAndClearLastExecutionError() {
        return lastExecutionError.getAndSet(null);
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

        installPrint(globals);
        globals.rawset(key("log"), buildLogTable());
        globals.rawset(key("os"), buildOsTable());

        JsonServiceImpl jsonService = new JsonServiceImpl(this);
        JsonLuaBinding.registerBindings(this, jsonService);

        LuaTable modTable = buildServiceTable();
        globals.rawset(key("Mod"), modTable);
        globals.rawset(key("game"), modTable);

        globals.rawset(key("item"), LuaFunction.of(this::createItemFromArgs));
        globals.rawset(key("ingredient"), LuaFunction.of(this::createIngredientFromArgs));
        globals.rawset(key("tag"), LuaFunction.of(this::createTagFromArgs));
        globals.rawset(key("oredict"), LuaFunction.of(this::createTagFromArgs));

        loadBootstrapResource(BOOTSTRAP_RESOURCE);
        loadBootstrapResource(BRAIN_RESOURCE);
        installDeferredRunner(globals);
        setupNativeRequire(globals);

        state.resourceLoader(this::loadResource);
    }

    /** Installs the Lua {@code print} global, mirroring output to the console and per-mod log. */
    private void installPrint(@NotNull LuaTable globals) {
        globals.rawset(key("print"), LuaFunction.of(args -> {
            StringBuilder builder = new StringBuilder(PRINT_CAPACITY);
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
    }

    /** Builds the {@code log} table: info/warn/error always log, debug honours {@link #debugMode}. */
    private @NotNull LuaTable buildLogTable() {
        LuaTable logTable = new LuaTable();
        logTable.rawset(key("info"), logFunction("INFO", false,
                (stage, message) -> AsyncFileLogger.get().info(stage, message)));
        logTable.rawset(key("warn"), logFunction("WARN", false,
                (stage, message) -> AsyncFileLogger.get().warn(stage, message)));
        logTable.rawset(key("error"), logFunction("ERROR", false,
                (stage, message) -> AsyncFileLogger.get().error(stage, message)));
        logTable.rawset(key("debug"), logFunction("DEBUG", true,
                (stage, message) -> AsyncFileLogger.get().info(stage, message)));
        return logTable;
    }

    private @NotNull LuaFunction logFunction(@NotNull String level, boolean debugOnly,
                                             @NotNull BiConsumer<LogStage, String> sink) {
        LogStage stage = LEVEL_ERROR.equals(level) ? LogStage.RUNTIME_ERROR : LogStage.SCRIPT_LOAD;
        return LuaFunction.of(args -> {
            if (debugOnly && !debugMode) {
                return LuaNil.NIL;
            }
            String message = firstArg(args);
            sink.accept(stage, debugOnly ? "[" + level + "] " + message : message);
            writeModLog(level, message);
            return LuaNil.NIL;
        });
    }

    /** Replaces the stripped {@code os} table with the two clock helpers scripts rely on. */
    private @NotNull LuaTable buildOsTable() {
        LuaTable osTable = new LuaTable();
        osTable.rawset(key("clock"), LuaFunction.of(args ->
                LuaValue.valueOf(System.currentTimeMillis() / (double) MILLIS_PER_SECOND)));
        osTable.rawset(key("time"), LuaFunction.of(args ->
                LuaValue.valueOf(System.currentTimeMillis() / MILLIS_PER_SECOND)));
        return osTable;
    }

    /** Builds the {@code Mod}/{@code game} service lookup table. */
    private @NotNull LuaTable buildServiceTable() {
        LuaTable modTable = new LuaTable();
        modTable.rawset(key("GetService"), LuaFunction.of(args -> {
            if (args.length < 2 || argAt(args, 1).isNil()) {
                throw LuaValue.argError(2, TYPE_STRING, ARG_GOT_NIL);
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
        return modTable;
    }

    // ---- Recipe DSL userdata --------------------------------------------

    private LuaValue createItemFromArgs(LuaValue... args) {
        LuaValue first = argAt(args, 0);
        if (first instanceof LuaTable tbl) {
            return ItemUserdataFactory.createItemUserdataLuava(state, itemFromTable(tbl));
        }
        if (first.isNil()) {
            throw LuaValue.argError(1, TYPE_STRING, ARG_GOT_NIL);
        }
        String itemId = toJavaString(first);
        if (isTagDescriptor(itemId)) {
            return ItemUserdataFactory.createIngredientUserdataLuava(state, new IngredientWrapper(itemId));
        }
        ItemCount item = new ItemCount(itemId, itemCount(args));
        if (args.length > OPTIONS_ARG_INDEX && !args[OPTIONS_ARG_INDEX].isNil()) {
            item = applyOptions(item, args[OPTIONS_ARG_INDEX]);
        }
        return ItemUserdataFactory.createItemUserdataLuava(state, item);
    }

    /** Builds an {@link ItemCount} from the {@code {id=..., count=..., damage=..., name=..., nbt=...}} form. */
    private @NotNull ItemCount itemFromTable(@NotNull LuaTable tbl) {
        LuaValue idVal = tbl.rawget(key("id"));
        if (idVal.isNil()) {
            idVal = tbl.rawget(key("item"));
        }
        if (idVal.isNil()) {
            throw LuaValue.argError(1, "table with 'id' or 'item'", ARG_GOT_NIL);
        }
        ItemCount base = new ItemCount(toJavaString(idVal), readInt(tbl.rawget(key("count")), 1));
        return applyTableOptions(base, tbl);
    }

    /** Applies the optional {@code damage}/{@code name}/{@code nbt} keys shared by both item forms. */
    private @NotNull ItemCount applyTableOptions(@NotNull ItemCount base, @NotNull LuaTable options) {
        ItemCount item = base;
        LuaValue damage = options.rawget(key("damage"));
        if (damage.isNumber()) {
            item = item.withDamage((int) damage.toLong());
        }
        LuaValue name = options.rawget(key("name"));
        if (name.isString()) {
            item = item.withName(toJavaString(name));
        }
        LuaValue nbt = options.rawget(key("nbt"));
        if (!nbt.isNil()) {
            item = item.withNbt(toJavaString(nbt));
        }
        return item;
    }

    /** Applies the third {@code item(...)} argument: an options table or a raw NBT string. */
    private @NotNull ItemCount applyOptions(@NotNull ItemCount base, @NotNull LuaValue options) {
        if (options instanceof LuaTable table) {
            return applyTableOptions(base, table);
        }
        return base.withNbt(toJavaString(options));
    }

    private int itemCount(LuaValue... args) {
        int fallback = 1;
        return args.length < 2 || args[1].isNil() ? fallback : readInt(args[1], fallback);
    }

    private static boolean isTagDescriptor(@NotNull String itemId) {
        return itemId.startsWith("#") || itemId.startsWith("tag:") || itemId.startsWith("oredict:");
    }

    private LuaValue createIngredientFromArgs(LuaValue... args) {
        if (args.length < 1 || args[0].isNil()) {
            throw LuaValue.argError(1, TYPE_STRING, ARG_GOT_NIL);
        }
        return ItemUserdataFactory.createIngredientUserdataLuava(
                state, new IngredientWrapper(toJavaString(args[0])));
    }

    private LuaValue createTagFromArgs(LuaValue... args) {
        if (args.length < 1 || args[0].isNil()) {
            throw LuaValue.argError(1, TYPE_STRING, ARG_GOT_NIL);
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
                    "Failed to load system bootstrap script " + quoteForLog(resourcePath)
                            + LuaErrorFormatter.describe(e));
        } catch (IOException e) {
            AsyncFileLogger.get().error(LogStage.SYSTEM,
                    "Failed to read system bootstrap script " + quoteForLog(resourcePath) + e.getMessage());
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
        taskTable.rawset(key("_run_deferred"), LuaFunction.of(this::runDeferred));
    }

    /** Starts one deferred task, resumes it and keeps it tracked until it dies. */
    private @NotNull LuaValue runDeferred(@NotNull LuaValue... args) {
        LuaValue functionValue = argAt(args, 0);
        if (!(functionValue instanceof LuaFunction function)) {
            return LuaNil.NIL;
        }
        LuaCoroutine coroutine = new LuaCoroutine(markCoroutine(function));
        if (liveCoroutines.size() >= COROUTINE_PRUNE_THRESHOLD) {
            pruneFinishedCoroutines();
        }
        liveCoroutines.add(coroutine);
        try {
            coroutine.resume(toCallArgs(argAt(args, 1)));
        } catch (LuaException e) {
            AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                    "Deferred task failed: " + LuaErrorFormatter.describe(e));
        } finally {
            if (coroutine.getStatus() == LuaCoroutine.Status.DEAD) {
                liveCoroutines.remove(coroutine);
            }
        }
        return coroutine;
    }

    /** Marks the coroutine body as running off the resuming operation and restores the module hint. */
    private @NotNull LuaFunction markCoroutine(@NotNull LuaFunction function) {
        String moduleHint = ModuleSourceHint.from(function);
        return LuaFunction.of(coroutineArgs -> {
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
    }

    /** Flattens the optional Lua argument table into a Java array for {@code coroutine.resume}. */
    private static @NotNull LuaValue[] toCallArgs(@NotNull LuaValue argsValue) {
        if (!(argsValue instanceof LuaTable argsTable)) {
            return new LuaValue[0];
        }
        int length = argsTable.rawlen();
        LuaValue[] callArgs = new LuaValue[length];
        for (int i = 1; i <= length; i++) {
            callArgs[i - 1] = argsTable.rawgetInt(i);
        }
        return callArgs;
    }

    // ---- require --------------------------------------------------------

    private void setupNativeRequire(@NotNull LuaTable globals) {
        LuaValue oldRequire = globals.rawget(key("require"));
        if (oldRequire != null && !oldRequire.isNil()) {
            globals.rawset(key("_oldRequire"), oldRequire);
        }
        globals.rawset(key("require"), LuaFunction.of(args -> requireModule(args, globals)));
    }

    /** Java {@code require}: native modules first, then LuaMod files, then the stock searchers. */
    private @NotNull LuaValue requireModule(@NotNull LuaValue[] args, @NotNull LuaTable globals) {
        if (args.length < 1 || args[0] == null || args[0].isNil()) {
            throw new LuaException("require requires 1 argument (moduleName)");
        }
        String modName = normalizeModuleName(toJavaString(args[0]));
        LuaValue result = findModule(modName);
        if (result != null && !result.isNil()) {
            return result;
        }
        LuaValue oldRequireFunction = globals.rawget(key("_oldRequire"));
        if (oldRequireFunction instanceof LuaFunction function) {
            return function.call(args);
        }
        throw new LuaException("Module not found: " + modName);
    }

    /** Rewrites a sibling require ("./x" / ".x") into a module-root-qualified name. */
    private @NotNull String normalizeModuleName(@NotNull String modName) {
        if (!modName.startsWith(".")) {
            return modName;
        }
        if (modName.startsWith("..")) {
            throw new LuaException(
                    "Parent directory traversal ('..') is forbidden for Sibling Require security.");
        }
        String currentModule = activeModuleStack.get().peek();
        if (currentModule == null || currentModule.isBlank()) {
            return modName.substring(1);
        }
        return qualifyWithModRoot(currentModule, modName);
    }

    private static @NotNull String qualifyWithModRoot(@NotNull String currentModule, @NotNull String modName) {
        String stripped = currentModule.endsWith(LUA_EXTENSION)
                ? currentModule.substring(0, currentModule.length() - LUA_EXTENSION.length())
                : currentModule;
        stripped = stripped.replace('/', '.').replace('\\', '.');
        int firstDot = stripped.indexOf('.');
        String modRoot = firstDot > 0 ? stripped.substring(0, firstDot) : stripped;
        return modRoot + "." + modName.substring(1);
    }

    /** Resolves a module through the native registry, the file tree, or the module cache. */
    private @Nullable LuaValue findModule(@NotNull String modName) {
        LuaValue cached = moduleCache.get(modName);
        if (cached != null) {
            return cached;
        }
        LuaValue resolved = NativeModuleResolver.resolveLuava(getGlobalEnvironment(), modName);
        if (resolved == null || resolved.isNil()) {
            resolved = resolveFileModule(modName);
        }
        if (resolved != null && !resolved.isNil()) {
            moduleCache.put(modName, resolved);
        }
        return resolved;
    }

    private @NotNull LuaValue resolveFileModule(@NotNull String modName) {
        File root = resolveLuaRoot();
        if (root == null) {
            return LuaNil.NIL;
        }
        String relativeName = modName.replace('.', '/');
        LuaValue direct = loadFirstCandidate(root, modName, moduleCandidates(relativeName));
        return direct != null ? direct : loadFromModFolders(root, modName, relativeName);
    }

    private static @NotNull List<String> moduleCandidates(@NotNull String relativeName) {
        return List.of(
                relativeName + LUA_EXTENSION,
                relativeName + "/init" + LUA_EXTENSION,
                "lib/" + relativeName + LUA_EXTENSION,
                "lib/" + relativeName + "/init" + LUA_EXTENSION
        );
    }

    private @Nullable LuaValue loadFirstCandidate(@NotNull File root, @NotNull String modName,
                                                  @NotNull List<String> candidates) {
        for (String candidate : candidates) {
            LuaValue loaded = tryLoadModuleFile(root, candidate, modName);
            if (loaded != null && !loaded.isNil()) {
                return loaded;
            }
        }
        return null;
    }

    /** Second search pass: {@code <modFolder>/<module>.lua} for every LuaMod directory. */
    private @NotNull LuaValue loadFromModFolders(@NotNull File root, @NotNull String modName,
                                                 @NotNull String relativeName) {
        File[] subModules = root.listFiles();
        if (subModules == null) {
            return LuaNil.NIL;
        }
        for (File modFolder : subModules) {
            if (!modFolder.isDirectory()) {
                continue;
            }
            String folderPrefix = modFolder.getName() + "/";
            LuaValue loaded = tryLoadModuleFile(root, folderPrefix + relativeName + LUA_EXTENSION, modName);
            if (loaded == null || loaded.isNil()) {
                loaded = tryLoadModuleFile(root, folderPrefix + relativeName + "/init" + LUA_EXTENSION, modName);
            }
            if (loaded != null && !loaded.isNil()) {
                return loaded;
            }
        }
        return LuaNil.NIL;
    }

    /**
     * Loads one module file.
     *
     * @return the module value, or {@code null} when the candidate path does not exist
     * @throws LuaException when the file exists but fails to compile/run: falling through to
     *         "module not found" (and to the native require search path) hid the real cause
     */
    private @Nullable LuaValue tryLoadModuleFile(@NotNull File root, @NotNull String relativePath,
                                                 @NotNull String modName) {
        Path path = resolveModulePath(root, relativePath);
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        activeModuleStack.get().push(modName);
        try {
            return readModuleFile(path, relativePath, modName);
        } finally {
            activeModuleStack.get().pop();
        }
    }

    /** @return the contained path, or {@code null} when the path escapes the lua root */
    private @Nullable Path resolveModulePath(@NotNull File root, @NotNull String relativePath) {
        try {
            return LuaModPathSecurity.resolveContained(root.toPath(), relativePath);
        } catch (IllegalArgumentException e) {
            AsyncFileLogger.get().warn(LogStage.SYSTEM,
                    "Rejected require path '" + quoteForLog(relativePath) + e.getMessage());
            return null;
        }
    }

    private @NotNull LuaValue readModuleFile(@NotNull Path path, @NotNull String relativePath,
                                             @NotNull String modName) {
        LuaValue loaded = LuaInvocationGuard.call(
                () -> evalModuleFile(path),
                null,
                failure -> {
                    throw moduleLoadFailure(modName, relativePath, failure);
                });
        return loaded == null ? LuaNil.NIL : loaded;
    }

    private @Nullable LuaValue evalModuleFile(@NotNull Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            LuaValue returned = state.eval(source, path.getFileName().toString());
            return returned != null && !returned.isNil() ? returned : LuaValue.valueOf(true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Logs and converts an unexpected module failure into a require error that keeps its cause. */
    private @NotNull LuaException moduleLoadFailure(@NotNull String modName, @NotNull String relativePath,
                                                    @NotNull Throwable failure) {
        Throwable cause = failure instanceof UncheckedIOException wrapped ? wrapped.getCause() : failure;
        String detail = failureDetail(cause);
        LogStage stage = cause instanceof IOException ? LogStage.SCRIPT_LOAD : LogStage.RUNTIME_ERROR;
        String action = describeModuleFailureKind(cause);
        AsyncFileLogger.get().error(stage,
                action + " Lua module file " + quoteForLog(relativePath) + detail);
        return moduleFailure(action + " module '" + modName + "' (" + relativePath + "): " + detail, cause);
    }

    private static @NotNull String describeModuleFailureKind(@NotNull Throwable failure) {
        if (failure instanceof IOException) {
            return "Failed to read";
        }
        if (failure instanceof LuaException) {
            return "Failed to load";
        }
        return "Unexpected error loading";
    }

    /** Builds a luava exception carrying {@code cause} so the original traceback survives. */
    private static @NotNull LuaException moduleFailure(@NotNull String message, @NotNull Throwable cause) {
        LuaException failure = new LuaException(message);
        failure.initCause(cause);
        return failure;
    }

    /** Renders {@code value} as a quoted log fragment, e.g. {@code 'x/y.lua': }. */
    private static @NotNull String quoteForLog(@NotNull String value) {
        return "'" + value + "': ";
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
                    "Resource loader rejected path " + quoteForLog(path) + e.getMessage());
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
            runBootstrapChunk(code, name);
        } finally {
            activeModuleStack.get().pop();
            exitExecution();
        }
    }

    /** Evaluates one chunk, recording and logging any failure as the last execution error. */
    private void runBootstrapChunk(@NotNull String code, @NotNull String name) {
        LuaInvocationGuard.call(
                () -> state.eval(code, name),
                null,
                failure -> recordBootstrapFailure(name, failure));
    }

    private void recordBootstrapFailure(@NotNull String name, @NotNull Throwable failure) {
        String message = failureDetail(failure);
        lastExecutionError.set(message);
        if (failure instanceof LuaException) {
            AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                    "Error running bootstrap script " + name + ": " + message);
            return;
        }
        AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                "Unexpected error running bootstrap script " + name + ": " + message);
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
            runScriptFile(file, context);
        } finally {
            activeModuleStack.get().pop();
            exitExecution();
        }
    }

    private void runScriptFile(@NotNull File file, @NotNull String context) {
        LuaInvocationGuard.call(
                () -> evalScriptFile(file),
                null,
                failure -> recordScriptFailure(file, context, failure));
    }

    private @Nullable LuaValue evalScriptFile(@NotNull File file) {
        try {
            String source = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            LuaValue evaluated = state.eval(source, file.getName());
            AsyncFileLogger.get().info(LogStage.SCRIPT_LOAD,
                    "Successfully executed script: " + file.getName());
            return evaluated;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void recordScriptFailure(@NotNull File file, @NotNull String context, @NotNull Throwable failure) {
        Throwable cause = failure instanceof UncheckedIOException wrapped ? wrapped.getCause() : failure;
        String message = failureDetail(cause);
        lastExecutionError.set(message);
        if (cause instanceof LuaException) {
            LuaErrorFormatter.logScriptError(context, file, message, LuaErrorFormatter.isCompileError(message));
            return;
        }
        if (cause instanceof IOException) {
            AsyncFileLogger.get().error(LogStage.SCRIPT_LOAD,
                    "Failed to read script " + file.getName() + ": " + message);
            return;
        }
        AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                "Unexpected error running script " + file.getName() + ": " + message);
    }

    /** Renders a failure as one line: full luava detail for VM errors, message otherwise. */
    private static @NotNull String failureDetail(@NotNull Throwable failure) {
        if (failure instanceof LuaException luaFailure) {
            return LuaErrorFormatter.describe(luaFailure);
        }
        String message = failure.getMessage();
        return message != null && !message.isBlank() ? message : failure.toString();
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
            ILuaValue[] results = LuaInvocationGuard.call(
                    () -> invokeAndWrap(function, args),
                    new ILuaValue[0],
                    this::recordCallbackFailure);
            return results == null ? new ILuaValue[0] : results;
        } finally {
            exitExecution();
        }
    }

    /** Invokes the callback and wraps every Lua result back into the SPI value type. */
    private @NotNull ILuaValue[] invokeAndWrap(@NotNull ILuaValue function, @NotNull ILuaValue... args) {
        LuaValue rawFunction = function instanceof LuavaLuaValue luavaValue
                ? luavaValue.getLuavaValue() : toLuavaValue(function);
        LuaValue[] luaArgs = new LuaValue[args.length];
        for (int i = 0; i < args.length; i++) {
            luaArgs[i] = toLuavaValue(args[i]);
        }
        LuaValue[] results = rawFunction instanceof LuaClosure closure
                ? BytecodeVM.execute(state, closure, luaArgs)
                : new LuaValue[]{rawFunction.call(luaArgs)};
        ILuaValue[] wrapped = new ILuaValue[results.length];
        for (int i = 0; i < results.length; i++) {
            wrapped[i] = LuavaLuaValue.wrap(results[i] == null ? LuaNil.NIL : results[i]);
        }
        return wrapped;
    }

    private void recordCallbackFailure(@NotNull Throwable failure) {
        String message = failureDetail(failure);
        lastExecutionError.set(message);
        if (failure instanceof LuaException luaFailure) {
            String rawMessage = luaFailure.getMessage();
            if (rawMessage == null || rawMessage.isBlank()) {
                AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                        "Lua exception without message during callback execution: " + luaFailure);
            }
        }
        AsyncFileLogger.get().error(LogStage.RUNTIME_ERROR,
                "Lua error executing callback: " + message);
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
            state.getGlobals().rawset(key(name), LuaFunction.of(args -> invokeGlobalBinding(name, function, args)));
        } finally {
            exitExecution();
        }
    }

    /** Bridges a Java {@link ILuaFunction} into the VM, converting failures into Lua errors. */
    private @NotNull LuaValue invokeGlobalBinding(@NotNull String name, @NotNull ILuaFunction function,
                                                  @NotNull LuaValue... args) {
        ILuaValue[] wrappedArgs = new ILuaValue[args.length];
        for (int i = 0; i < args.length; i++) {
            wrappedArgs[i] = LuavaLuaValue.wrap(args[i] == null ? LuaNil.NIL : args[i]);
        }
        ILuaValue result = LuaInvocationGuard.<ILuaValue>call(
                () -> function.invoke(wrappedArgs),
                null,
                failure -> {
                    throw bindingFailure(name, failure);
                });
        return result == null ? LuaNil.NIL : toLuavaValue(result);
    }

    /**
     * Wraps a host-binding failure in a luava error that keeps the original cause.
     * A {@link LuaException} raised by the binding itself is already a Lua error and is
     * returned unchanged so the script sees the original message instead of a re-wrap.
     */
    private static @NotNull LuaException bindingFailure(@NotNull String name, @NotNull Throwable cause) {
        if (cause instanceof LuaException luaFailure) {
            return luaFailure;
        }
        String message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        LuaException failure = new LuaException("Lua binding '" + name + "' failed: " + message);
        failure.initCause(cause);
        return failure;
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
            return wrapNumberValue(number);
        }
        if (obj instanceof Boolean bool) {
            return wrapBoolean(bool);
        }
        if (obj instanceof ILuaTable table) {
            return table;
        }
        if (obj instanceof Map<?, ?> map) {
            return mapToTable(map);
        }
        if (obj instanceof List<?> list) {
            return listToTable(list);
        }
        return wrapUserdata(obj);
    }

    private @NotNull ILuaTable mapToTable(@NotNull Map<?, ?> map) {
        ILuaTable table = createTable();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            table.rawset(String.valueOf(entry.getKey()), toLuaValue(entry.getValue()));
        }
        return table;
    }

    private @NotNull ILuaTable listToTable(@NotNull List<?> list) {
        ILuaTable table = createTable();
        int index = 1;
        for (Object item : list) {
            table.rawset(index++, toLuaValue(item));
        }
        return table;
    }

    @Override
    public @NotNull ILuaValue wrapDynamicJavaObject(@NotNull Object obj) {
        return new LuavaLuaValue(DynamicJavaProxy.createLuava(state, obj));
    }

    /**
     * Converts a Java number to the matching Lua kind.
     *
     * <p>Gson/JSON parses every number as a {@code Double}, so a stored integer {@code 42} used to
     * come back as the Lua float {@code 42.0} ({@code tostring} = "42.0", {@code math.type} =
     * "float"), which broke session-storage round-trips. Whole numbers that fit in a long become
     * Lua integers; everything else stays a float.</p>
     */
    private @NotNull ILuaValue wrapNumberValue(@NotNull Number number) {
        if (number instanceof Byte || number instanceof Short
                || number instanceof Integer || number instanceof Long) {
            return new LuavaLuaValue(LuaValue.valueOf(number.longValue()));
        }
        double value = number.doubleValue();
        if (Double.isFinite(value) && value == Math.rint(value)
                && Math.abs(value) <= 9_007_199_254_740_992.0) {
            return new LuavaLuaValue(LuaValue.valueOf((long) value));
        }
        return wrapNumber(value);
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
        closed.set(true);
        pruneFinishedCoroutines();
        for (LuaCoroutine coroutine : liveCoroutines) {
            if (coroutine.getStatus() != LuaCoroutine.Status.SUSPENDED) {
                continue;
            }
            LuaInvocationGuard.call(
                    () -> {
                        coroutine.close();
                        return null;
                    },
                    null,
                    failure -> AsyncFileLogger.get().warn(LogStage.RUNTIME_ERROR,
                            "Failed to close deferred coroutine during shutdown: " + failure.getMessage()));
        }
        liveCoroutines.clear();
    }

    /**
     * Drops coroutines that already finished. A task that yields is resumed by Lua
     * ({@code coroutine.resume} inside {@code task.wait}'s delayed callback), so the resume
     * never reaches the {@code _run_deferred} frame that registered it; without this pass the
     * set would keep every finished task (and its captured closures) for the engine's lifetime.
     */
    void pruneFinishedCoroutines() {
        liveCoroutines.removeIf(coroutine -> coroutine.getStatus() == LuaCoroutine.Status.DEAD);
    }

    /** Number of coroutines still tracked for shutdown; package-private for tests. */
    int liveCoroutineCount() {
        return liveCoroutines.size();
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

    private static @NotNull String firstArg(@NotNull LuaValue... args) {
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
}
