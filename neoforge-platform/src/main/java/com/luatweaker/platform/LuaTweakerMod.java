package com.luatweaker.platform;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.*;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.lsp.LtvmStubExporter;
import com.luatweaker.core.lsp.LtvmStubGenerator;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.core.vm.LuaEngineLimits;
import com.luatweaker.platform.command.LuaTweakerCommandRegistry;
import com.luatweaker.platform.config.LuaTweakerConfig;
import com.luatweaker.platform.recipe.InterceptionHelper;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@Mod(LuaTweakerMod.MODID)
public class LuaTweakerMod {
    private static final String TRUNCATION_MARKER = "...";
    /** Load-error reasons longer than this are cut with {@link #TRUNCATION_MARKER} appended. */
    private static final int MAX_LOAD_REASON_CHARS = 120;

    public static final String MODID = "luatweaker";

    private static final Logger LOGGER = LoggerFactory.getLogger(LuaTweakerMod.class);

    /** Client-only control channels; they carry a small UTF-8 JSON object, never Lua values. */
    private static final String SYNC_DATA_CHANNEL = "LuaTweaker:SyncData";
    private static final String OPEN_SCREEN_CHANNEL = "LuaTweaker:OpenScreen";
    private static final String CLOSE_SCREEN_CHANNEL = "LuaTweaker:CloseScreen";

    /**
     * Reassembles chunked inbound frames. Bounded and self-expiring, so a client cannot
     * exhaust memory by opening partial messages; cleared on logout.
     */
    private static final com.luatweaker.network.ChunkAssembler INBOUND_CHUNKS =
            new com.luatweaker.network.ChunkAssembler();

    // Written on the server/client tick thread and read by the render thread, network handlers
    // and worldgen workers; published through atomic holders so those threads see the switch.
    private static final java.util.concurrent.atomic.AtomicReference<ILuaEngine> ACTIVE_ENGINE =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicReference<com.luatweaker.network.NetworkServiceImpl>
            SERVER_NETWORK_SERVICE = new java.util.concurrent.atomic.AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicReference<com.luatweaker.network.NetworkServiceImpl>
            CLIENT_NETWORK_SERVICE = new java.util.concurrent.atomic.AtomicReference<>();

    public static ILuaEngine getActiveEngine() {
        return ACTIVE_ENGINE.get();
    }

    /**
     * Pumps the Lua task queues of the ACTIVE engine (bootstrap {@code task._tick}
     * plus the Java {@code _java_tick}). Every {@code Signal:Fire}, {@code Task.spawn},
     * {@code task.delay} and {@code task.wait} schedules through these queues, so this
     * MUST be called once per game tick (server tick + client tick); without it all
     * async Lua callbacks (network RemoteEvents, mana loops, HUD sync) silently die.
     */
    public static void tickActiveEngineTasks() {
        ILuaEngine engine = ACTIVE_ENGINE.get();
        if (engine == null) { return; }
        ILuaValue taskVal = engine.getGlobalEnvironment().rawget("task");
        if (taskVal == null || !taskVal.isTable()) { return; }
        ILuaTable taskTbl = taskVal.asTable();
        try {
            ILuaValue luaTick = taskTbl.rawget("_tick");
            if (luaTick != null && luaTick.isFunction()) {
                engine.callFunction(luaTick);
            }
            ILuaValue javaTick = taskTbl.rawget("_java_tick");
            if (javaTick != null && javaTick.isFunction()) {
                engine.callFunction(javaTick);
            }
        } catch (IllegalStateException e) {
            // A reload shut this engine down between reading ACTIVE_ENGINE.get() and pumping it.
            // The next tick reads the new engine; never let this take down the tick loop.
            LOGGER.debug("Lua task pump skipped: engine was shut down by a reload");
        }
    }

    public static void setServerNetworkService(com.luatweaker.network.NetworkServiceImpl service) {
        SERVER_NETWORK_SERVICE.set(service);
    }

    public static void setClientNetworkService(com.luatweaker.network.NetworkServiceImpl service) {
        CLIENT_NETWORK_SERVICE.set(service);
    }

    public static com.luatweaker.network.NetworkServiceImpl getServerNetworkService() {
        return SERVER_NETWORK_SERVICE.get();
    }

    /**
     * The command registry; held as a field so external modules can register
     * commands before build().
     */
    private final LuaTweakerCommandRegistry commandRegistry;

    private final com.luatweaker.content.ContentServiceImpl contentService;
    private final com.luatweaker.content.StorageServiceImpl storageService;
    private final com.luatweaker.content.DatapackServiceImpl datapackService;
    private final com.luatweaker.platform.content.LuaAssetsPackFinder luaAssetsPackFinder;

    private void initClientPlatform() {
        Platform.setClient(new com.luatweaker.platform.client.NeoForgeClientPlatform());
    }

    public LuaTweakerMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("LuaTweaker constructor starting");
        LOGGER.info("CWD: {}", new File(".").getAbsolutePath());

        // Register NeoForge configuration specification
        modContainer.registerConfig(ModConfig.Type.COMMON, LuaTweakerConfig.COMMON_SPEC);
        // STARTUP configs load inside registerConfig, before LuaMods load below; the VM caps
        // must be in place by then. Do not move this after loadStartupLuaMods.
        modContainer.registerConfig(ModConfig.Type.STARTUP, LuaTweakerConfig.ENGINE_SPEC);

        // Initialize Platform Abstraction Layer (PAL) Helpers
        Platform.setNetwork(new com.luatweaker.platform.network.NeoForgeNetworkPlatform());
        Platform.setEntity(new com.luatweaker.platform.entity.NeoForgeEntityPlatform());
        Platform.setInteraction(new com.luatweaker.platform.interaction.NeoForgeInteractionPlatform());
        Platform.setContent(new com.luatweaker.platform.content.NeoForgeContentPlatform());
        Platform.setStorage(new com.luatweaker.platform.storage.NeoForgeStoragePlatform());
        Platform.setDimension(new com.luatweaker.platform.dimension.NeoForgeDimensionPlatform());
        Platform.setStructures(new com.luatweaker.platform.structures.NeoForgeStructuresPlatform());
        Platform.setCapability(new com.luatweaker.platform.interaction.NeoForgeCapabilityPlatform());
        Platform.setWorldgen(new com.luatweaker.platform.worldgen.NeoForgeWorldgenPlatform());
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            initClientPlatform();
        }
        LOGGER.info("Platform helpers set");

        // Initialize Platform Integrations (GeckoLib, JEI, and extensible hooks)
        com.luatweaker.platform.integration.IntegrationManager.register(new com.luatweaker.platform.integration.geckolib.GeckoLibIntegration());
        com.luatweaker.platform.integration.IntegrationManager.register(new com.luatweaker.platform.integration.jei.JeiIntegration());
        com.luatweaker.platform.integration.IntegrationManager.initModBus(modEventBus);
        com.luatweaker.platform.integration.IntegrationManager.logStatus();

        // Setup the global stage-aware logger
        AsyncFileLogger fileLogger = AsyncFileLogger.get();
        LOGGER.info("AsyncFileLogger obtained, log file: {}", fileLogger.getLogFile());
        com.luatweaker.api.log.LuaTweakerLog.set(fileLogger);

        // Create user directories if they don't exist
        initializeUserDirectories();

        // Initialize Content, Storage, Datapack Services
        File luaDir = getLuaDirectory();
        this.contentService = new com.luatweaker.content.ContentServiceImpl();
        this.storageService = new com.luatweaker.content.StorageServiceImpl(new File(luaDir, "storage.json"));
        this.datapackService = new com.luatweaker.content.DatapackServiceImpl();
        this.luaAssetsPackFinder = new com.luatweaker.platform.content.LuaAssetsPackFinder(luaDir, datapackService, contentService);

        // Load Autonomous LuaMods for Mod Construction Phase (Content & Registrations)
        loadStartupLuaMods(luaDir);

        // Register Mod Event Bus listeners for Content Registry and Asset Pack Finder
        com.luatweaker.platform.content.NeoForgeContentRegistry contentRegistry = new com.luatweaker.platform.content.NeoForgeContentRegistry(contentService);
        modEventBus.register(contentRegistry);
        if (Platform.getContent().isClient()) {
            modEventBus.register(new com.luatweaker.platform.content.NeoForgeContentRegistry.ClientModEvents(contentRegistry));
        }
        modEventBus.register(luaAssetsPackFinder);        modEventBus.addListener(LuaTweakerMod::registerPayloads);
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> onClientSetup());
        modEventBus.addListener(com.luatweaker.platform.dimension.NeoForgeDimensionProvider::registerCodecs);
        modEventBus.addListener(com.luatweaker.platform.worldgen.MixedVeinRegistrar::registerFeatures);

        // Register Game Event Bus listeners (gameplay events)
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(
                new com.luatweaker.platform.content.NeoForgeContentRegistry.BossBarTickHandler(contentService));
        com.luatweaker.platform.event.UniversalEventForwarder.registerAll();
        com.luatweaker.platform.loot.NeoForgeRuntimeLootHandler.register();

        if (Platform.getContent().isClient()) {
            modEventBus.addListener(com.luatweaker.platform.client.DynamicKeyMappingHandler::onRegisterKeyMappings);
            NeoForge.EVENT_BUS.addListener(com.luatweaker.platform.client.DynamicKeyMappingHandler::onClientTick);
            NeoForge.EVENT_BUS.addListener(com.luatweaker.platform.client.NeoForgeWorldRenderEventListener::onRenderLevel);
            modEventBus.addListener(com.luatweaker.platform.client.dimension.LuaDimensionEffects::register);
            NeoForge.EVENT_BUS.addListener(com.luatweaker.platform.client.ExperimentalWarningHandler::onScreenInit);
            NeoForge.EVENT_BUS.addListener(com.luatweaker.platform.client.luamods.LuamodMenuButton::onScreenInit);
        }
        // Server tick pump for the Lua task queues is registered via @SubscribeEvent
        // on onServerTick (LuaTweakerMod is registered on NeoForge.EVENT_BUS above).

        // Build the command registry (core commands auto-registered inside)
        commandRegistry = new LuaTweakerCommandRegistry(luaDir);
        commandRegistry.register(new com.luatweaker.platform.command.core.DimensionCommand());
        commandRegistry.register(new com.luatweaker.platform.command.core.SpawnCommand());
    }

    private void loadStartupLuaMods(File luaDir) {
        // Feed the dependency gates BEFORE any mod loads: the running engine
        // version, the game version, and every installed platform mod. Core-engine
        // stays NeoForge-free; this is the only place ModList is read.
        publishPlatformEnvironment();

        ILuaEngine startupEngine = LuaEngineFactory.create(isDebugEnabled(), engineLimits());
        startupEngine.setLuaDirectory(luaDir);
        com.luatweaker.platform.bootstrap.LuaServiceBootstrap.registerAllServices(
                startupEngine, contentService, storageService, datapackService, new NeoForgeRecipeManager(datapackService)
        );
        com.luatweaker.platform.integration.IntegrationManager.initEngine(startupEngine);

        // Fresh command set per load cycle: only the mods loaded below register commands.
        com.luatweaker.command.CommandServiceImpl.clear();
        // Load Autonomous LuaMods from luamods/ directory
        String dist = net.neoforged.fml.loading.FMLEnvironment.dist.isClient() ? "universal" : "server";
        // Only the client owns the FML early loading window; a dedicated server keeps the log.
        com.luatweaker.core.mod.LuaModLoadObserver loadProgress =
                net.neoforged.fml.loading.FMLEnvironment.dist.isClient()
                        ? new com.luatweaker.platform.bootstrap.FmlLoadingScreenProgress()
                        : com.luatweaker.core.mod.LuaModLoadObserver.NONE;
        com.luatweaker.core.mod.LuaModManager.loadLuaMods(luaDir, startupEngine, dist, loadProgress);

        // Loud warning for keybind payloads that have no matching server RemoteEvent
        // (typo'd names would otherwise silently drop every key press).
        com.luatweaker.platform.bootstrap.KeyBindPayloadValidator.validate();

        // Materialize Lua-registered worldgen + loot entries into datapack files.
        applyLuaDatapackProviders();

        // Kick off declarative update checks for mods that declare update_url.
        com.luatweaker.update.UpdateServiceImpl.checkAll();

        // Publish the engine that actually loaded the mods. Without this a dedicated client
        // (and the first dedicated-server reload) ends up ticking/dispatching a different,
        // empty VM: no HUD listeners, no RemoteEvent handlers, no teardown on reload.
        if (ACTIVE_ENGINE.get() == null) {
            ACTIVE_ENGINE.set(startupEngine);
        }
    }

    /**
     * Reads the running platform versions into the LuaMod dependency gates.
     * Engine version comes from our own mod container, the game version from the
     * {@code minecraft} container NeoForge always registers, and the installed
     * set from every loaded mod. Failures fail open (unknown = allowed) so a
     * broken lookup can never mass-skip version-gated mods.
     */
    private static void publishPlatformEnvironment() {
        String engineVersion = null;
        String minecraftVersion = null;
        // Insertion-ordered snapshot built once at mod construction; never shared concurrently.
        java.util.Map<String, String> installed;
        installed = new java.util.LinkedHashMap<>();
        try {
            net.neoforged.fml.ModList mods = net.neoforged.fml.ModList.get();
            if (mods != null) {
                engineVersion = mods.getModContainerById(MODID)
                        .map(c -> c.getModInfo().getVersion().toString())
                        .orElse(null);
                minecraftVersion = mods.getModContainerById("minecraft")
                        .map(c -> c.getModInfo().getVersion().toString())
                        .orElse(null);
                for (net.neoforged.neoforgespi.language.IModInfo c : mods.getMods()) {
                    installed.put(c.getModId(), c.getVersion().toString());
                }
            }
        } catch (IllegalStateException | LinkageError e) {
            LOGGER.warn("Platform environment lookup failed, dependency gates fail open: {}", e.getMessage());
        }
        com.luatweaker.core.mod.LuaModManager.setEngineVersion(engineVersion);
        com.luatweaker.core.mod.LuaModManager.setMinecraftVersion(minecraftVersion);
        com.luatweaker.core.mod.LuaModManager.setInstalledMods(installed);
    }

    /**
     * The Loot/Worldgen Lua services collect entries during mod load; nothing
     * consumes them unless a provider turns them into virtual datapack files.
     */
    private void applyLuaDatapackProviders() {
        Object worldgenObj = com.luatweaker.core.service.LuaServiceRegistry.get("WorldgenServiceImpl");
        if (worldgenObj instanceof com.luatweaker.worldgen.WorldgenServiceImpl worldgenService) {
            new com.luatweaker.platform.worldgen.NeoForgeWorldgenProvider(worldgenService, datapackService).applyAll();
        }
        Object lootObj = com.luatweaker.core.service.LuaServiceRegistry.get("LootServiceImpl");
        if (lootObj instanceof com.luatweaker.loot.LootServiceImpl lootService) {
            new com.luatweaker.platform.loot.NeoForgeLootProvider(lootService, datapackService).applyAll();
        }
        com.luatweaker.platform.dimension.NeoForgeDimensionProvider.applyAll(datapackService);
        Object structureObj = com.luatweaker.core.service.LuaServiceRegistry.get("StructureServiceImpl");
        if (structureObj instanceof com.luatweaker.structures.StructureServiceImpl structureService) {
            new com.luatweaker.platform.structures.NeoForgeStructureProvider(
                    structureService, datapackService, collectStructureDataRoots(getLuaDirectory())).applyAll();
        }
    }

    /**
     * Data roots searched (in order) for {@code data/<ns>/structure/<path>.nbt}
     * templates, mirroring {@code LuaTweakerVirtualPackResources.resolvePhysicalFile}:
     * the shared {@code <luaDir>/data} dir first, then every {@code <mod>/data} dir.
     * Zip-packaged mods have no physical dir and are skipped, same as the runtime
     * fallback. Sorted by mod id for deterministic emission order.
     */
    private static java.util.List<File> collectStructureDataRoots(File luaDir) {
        java.util.List<File> roots = new java.util.ArrayList<>();
        roots.add(new File(luaDir, "data"));
        File luamodsDir = "luamods".equalsIgnoreCase(luaDir.getName())
                ? luaDir : new File(luaDir, "luamods");
        File[] mods = luamodsDir.isDirectory() ? luamodsDir.listFiles(File::isDirectory) : null;
        if (mods != null) {
            java.util.Arrays.sort(mods, java.util.Comparator.comparing(File::getName));
            for (File mod : mods) {
                roots.add(new File(mod, "data"));
            }
        }
        return roots;
    }

    private void onClientSetup() {
        // Lua mods are loaded in the constructor and LuaServiceBootstrap binds every
        // service on that same engine. Re-running the bindings here used to replace
        // Events, GuiService and Client.OnRenderWorld with fresh empty tables, silently
        // dropping every listener a Lua mod connected during startup (HUD, events, world
        // rendering, keybinds). The only case that needs work is a missing engine, which
        // the normal mod-load path never leaves behind.
        if (ACTIVE_ENGINE.get() == null) {
            ILuaEngine engine = LuaEngineFactory.create(isDebugEnabled(), engineLimits());
            engine.setLuaDirectory(getLuaDirectory());
            ACTIVE_ENGINE.set(engine);
            com.luatweaker.platform.bootstrap.LuaServiceBootstrap.registerAllServices(
                    engine, contentService, storageService, datapackService, new NeoForgeRecipeManager());
        }
    }

    private void initializeUserDirectories() {
        File luaDir = getLuaDirectory();
        if (!luaDir.exists()) {
            luaDir.mkdirs();
            com.luatweaker.api.log.LuaTweakerLog.get().info(
                    LogStage.SYSTEM,
                    "Created main Lua directory at: " + luaDir.getAbsolutePath());
        }

        String[] requiredSubDirs = { ".luatweaker/stubs" };
        for (String sub : requiredSubDirs) {
            ensureSubDirectory(luaDir, sub);
        }
    }

    private static void ensureSubDirectory(File parent, String name) {
        File subDir = new File(parent, name);
        if (!subDir.exists()) {
            subDir.mkdirs();
        }
    }

    private File getLuaDirectory() {
        File gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toFile();
        File defaultLuamodsDir = new File(gameDir, "luamods");
        if (!defaultLuamodsDir.exists()) {
            defaultLuamodsDir.mkdirs();
        }
        return defaultLuamodsDir;
    }

    private boolean isDebugEnabled() {
        return com.luatweaker.platform.util.CallbackFirewall.supply(LuaTweakerConfig.DEBUG::get, false,
                LogStage.SYSTEM, () -> "debug flag read");
    }

    private boolean isAutoStubsEnabled() {
        return com.luatweaker.platform.util.CallbackFirewall.supply(LuaTweakerConfig.AUTO_GENERATE_STUBS::get,
                true, LogStage.SYSTEM, () -> "auto-stubs flag read");
    }

    /**
     * Reads the VM resource caps from the STARTUP config.
     *
     * <p>NeoForge loads STARTUP configs inside {@code registerConfig}, so the values are already
     * available while the mod constructor loads LuaMods. The guard only covers a failed config
     * load: in that case the engine stays unlimited instead of guessing a cap that could break a
     * working setup, and the failure is logged instead of silently swallowed.</p>
     *
     * @return the configured caps, never {@code null}
     */
    private LuaEngineLimits engineLimits() {
        try {
            return new LuaEngineLimits(
                    LuaTweakerConfig.MAX_MEMORY_BYTES.get(),
                    LuaTweakerConfig.MAX_CALL_DEPTH.get(),
                    LuaTweakerConfig.MAX_COROUTINES.get());
        } catch (IllegalStateException e) {
            AsyncFileLogger.get().warn(LogStage.SYSTEM,
                    "Engine limits unavailable because the STARTUP config is not loaded; "
                            + "running with no caps. " + e.getMessage());
            return LuaEngineLimits.UNLIMITED;
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        commandRegistry.build(event);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) { return; }

        // Available updates discovered by the engine-side declarative checker.
        for (com.luatweaker.update.UpdateStatus status : com.luatweaker.update.UpdateServiceImpl.getUpdates()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§a[LuaTweaker] §fUpdate available: §e" + status.modId() + " §fv" + status.currentVersion()
                            + " §7-> §a" + status.latestVersion()
                            + (status.updateName() == null || status.updateName().isEmpty()
                                    ? "" : " §7(" + status.updateName() + ")")));
        }

        // Loud warning for mods holding the network permission (anti-malware notice).
        for (com.luatweaker.core.mod.LuaMod mod : com.luatweaker.core.mod.LuaModManager.getLoadedMods().values()) {
            if (mod.getManifest().permissions().contains(com.luatweaker.update.WebServiceImpl.PERMISSION)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[LuaTweaker] WARNING: mod '" + mod.getManifest().id()
                                + "' holds the 'net.http' permission and can access the internet."));
            }
        }

        // Dependency/condition skips: tell every joining player which LuaMods did
        // NOT load and why (missing dependency, version mismatch, disabled), and
        // point at /lt doctor for the full per-mod report.
        java.util.Map<String, String> loadErrors = com.luatweaker.core.mod.LuaModManager.getLoadErrors();
        for (String line : buildLoadWarningLines(loadErrors, 5)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
        }
    }

    /**
     * Builds the in-game load-failure warning, pure for tests (no Minecraft types
     * in the signature). Reasons are truncated so one pathological entry cannot
     * flood chat.
     *
     * @param loadErrors mod id to skip/crash reason, as reported by
     *                   {@link com.luatweaker.core.mod.LuaModManager#getLoadErrors()}
     * @param maxShown   how many per-mod lines to include before summarizing
     * @return chat lines, empty when everything loaded cleanly
     */
    static java.util.List<String> buildLoadWarningLines(java.util.Map<String, String> loadErrors, int maxShown) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (loadErrors == null || loadErrors.isEmpty()) { return lines; }
        lines.add("§c[LuaTweaker] WARNING: " + loadErrors.size() + " LuaMod(s) did not load:");
        java.util.List<String> ids = new java.util.ArrayList<>(loadErrors.keySet());
        java.util.Collections.sort(ids);
        int shown = 0;
        for (String id : ids) {
            if (shown >= maxShown) { break; }
            String reason = String.valueOf(loadErrors.get(id));
            if (reason.length() > MAX_LOAD_REASON_CHARS) {
                reason = reason.substring(0, MAX_LOAD_REASON_CHARS - TRUNCATION_MARKER.length()) + TRUNCATION_MARKER;
            }
            lines.add("§7 - " + id + ": " + reason);
            shown++;
        }
        if (loadErrors.size() > shown) {
            lines.add("§7... and " + (loadErrors.size() - shown) + " more. Run /lt doctor for details.");
        } else {
            lines.add("§7Run /lt doctor for details.");
        }
        return lines;
    }

    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // ALWAYS pump from the server thread: Lua game logic (mana loops, summoning,
        // AI goals, RemoteEvent listeners) touches server-side Minecraft APIs that
        // must run on the server thread. Pumping from the client tick (singleplayer)
        // makes e.g. ServerLevel.addFreshEntity run on the client thread, which races
        // with the integrated server and freezes the game.
        tickActiveEngineTasks();
        // RunService on the server: Stepped (before the tick's world work is done) and
        // Heartbeat (end of tick), with a real wall-clock delta. Previously only the client
        // fired these, so server scripts connecting to Heartbeat never ran.
        ILuaEngine tickEngine = ACTIVE_ENGINE.get();
        if (tickEngine != null) {
            double delta = com.luatweaker.platform.runservice.ServerTickClock.nextDelta();
            com.luatweaker.platform.runservice.RunServiceDispatcher.fire(tickEngine, "Stepped", delta);
            com.luatweaker.platform.runservice.RunServiceDispatcher.fire(tickEngine, "Heartbeat", delta);
        }
        com.luatweaker.platform.dimension.DimensionMobSpawner.onServerTick();
    }

    /**
     * Fires the "DimensionEntered" world event (payload: player, dimensionId,
     * fromDimensionId) whenever a player changes dimensions.
     */
    @SubscribeEvent
    public void onPlayerChangedDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        Object events = com.luatweaker.core.service.LuaServiceRegistry.get("Events");
        if (!(events instanceof com.luatweaker.api.event.IEventService eventService)) { return; }
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) { return; }
        try {
            com.luatweaker.platform.entity.NeoForgePlayerWrapper wrapper =
                    new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player);
            ILuaTable payload = eventService.getEngine().createTable();
            payload.rawset("player", com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(
                    eventService.getEngine(), wrapper));
            payload.rawset("dimensionId", event.getTo().location().toString());
            payload.rawset("fromDimensionId", event.getFrom().location().toString());
            eventService.fireEvent("DimensionEntered", payload);
        } catch (org.luava.runtime.LuaException | IllegalStateException e) {
            LOGGER.warn("DimensionEntered event dispatch failed: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            INBOUND_CHUNKS.clear("S:" + player.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        LOGGER.info("ServerAboutToStartEvent fired, calling reloadServerRecipes");
        reloadServerRecipes(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        LOGGER.info("ServerStoppingEvent fired, flushing pending storage commits...");
        com.luatweaker.platform.integration.IntegrationManager.onServerStopping();
        com.luatweaker.storage.StorageServiceImpl.flushAllActive();
    }

    @SubscribeEvent
    public void onLevelSave(net.neoforged.neoforge.event.level.LevelEvent.Save event) {
        if (!event.getLevel().isClientSide()
                && event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && serverLevel.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            com.luatweaker.storage.StorageServiceImpl.flushAllActive();
        }
    }

    @SubscribeEvent
    public void onVillagerTrades(VillagerTradesEvent event) {
        InterceptionHelper.applyPendingTrades(event);
    }

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        InterceptionHelper.applyPendingAnvil(event);
    }

    @SubscribeEvent
    public void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        LOGGER.info("RegisterBrewingRecipesEvent fired, applying pending brewing recipes...");
        InterceptionHelper.applyPendingBrewing(event);
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void object, ResourceManager resourceManager, ProfilerFiller profiler) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    reloadServerRecipes(server);
                } else if (isAutoStubsEnabled()) {
                    generateStubs();
                }
            }
        });
    }

    private long lastReloadTime;

    /** Explicit reload mutex (block-level locking instead of method-level synchronization). */
    private final java.util.concurrent.locks.ReentrantLock reloadLock =
            new java.util.concurrent.locks.ReentrantLock();

    /** Minimum gap between reloadServerRecipes calls to dedupe redundant reloads. */
    private static final long MIN_RELOAD_INTERVAL_MS = 500;

    public void reloadServerRecipes(MinecraftServer server) {
        reloadLock.lock();
        try {
            reloadServerRecipesLocked(server);
        } finally {
            reloadLock.unlock();
        }
    }

    private void reloadServerRecipesLocked(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastReloadTime < MIN_RELOAD_INTERVAL_MS) {
            LOGGER.info("Skipping redundant reloadServerRecipes call (reloaded {}ms ago)", now - lastReloadTime);
            return;
        }
        lastReloadTime = now;

        LOGGER.info("reloadServerRecipes started (server: {})", server != null ? "active" : "null/early");
        long startTime = System.currentTimeMillis();
        boolean debugMode = isDebugEnabled();

        AsyncFileLogger.get().setDebugEnabled(debugMode);
        com.luatweaker.api.log.LuaTweakerLog.get().stageBegin(LogStage.RELOAD);
        com.luatweaker.api.log.LuaTweakerLog.get().info(LogStage.RELOAD,
                "Reloading Lua server scripts... (Debug: " + debugMode + ")");

        if (ACTIVE_ENGINE.get() != null) {
            com.luatweaker.api.log.LuaTweakerLog.get().info(LogStage.RELOAD,
                    "Firing OnScriptUnload teardown hooks...");
            try {
                com.luatweaker.events.EventServiceImpl.fireTeardownHooks();
            } catch (Exception e) {
                com.luatweaker.api.log.LuaTweakerLog.get().error(LogStage.RELOAD,
                        "Error during teardown hooks: " + e.getMessage());
            }
        }

        // Clear pending Anvil/Brewing/Trade from previous reload cycle
        InterceptionHelper.clearPending();

        // Rebuild the virtual datapack from scratch: files from disabled/edited mods
        // or removed worldgen/loot entries must not survive a reload. Content-driven
        // files (tags/models/lang) are regenerated right away from the same
        // contentService; Lua-driven files are re-added by the script re-run below.
        datapackService.clear();
        luaAssetsPackFinder.rebuildVirtualFiles();

        if (isAutoStubsEnabled()) {
            generateStubs();
        }

        NeoForgeRecipeManager recipeManager = new NeoForgeRecipeManager();
        ILuaEngine previousEngine = ACTIVE_ENGINE.get();
        if (previousEngine != null) {
            // OnDisable must run on the VM that owns the closures, before it is shut down.
            com.luatweaker.core.mod.LuaModManager.fireModTeardownHooks(previousEngine);
            // Drop tweens pinned to the old VM before it is shut down; their per-frame writes
            // would otherwise touch tables from a dead engine.
            com.luatweaker.client.TweenServiceImpl.clearAll();
            // Stop suspended task coroutines before replacing the VM, otherwise they keep
            // holding Lua closures and can be resumed by a stray delay callback after reload.
            previousEngine.shutdown();
        }
        ACTIVE_ENGINE.set(LuaEngineFactory.create(debugMode, engineLimits()));
        ILuaEngine engine = ACTIVE_ENGINE.get();
        engine.setLuaDirectory(getLuaDirectory());

        com.luatweaker.core.service.LuaServiceRegistry.clear();
        com.luatweaker.platform.bootstrap.LuaServiceBootstrap.registerAllServices(engine, contentService,
                storageService, datapackService, recipeManager);
        com.luatweaker.platform.integration.IntegrationManager.initEngine(engine);

        // Fresh command set per load cycle: disabled/removed mods must not leave
        // stale /lt commands behind (see CommandServiceImpl.clear()).
        com.luatweaker.command.CommandServiceImpl.clear();

        // Load Autonomous LuaMods from luamods/
        String dist = net.neoforged.fml.loading.FMLEnvironment.dist.isClient() ? "universal" : "server";
        com.luatweaker.core.mod.LuaModManager.loadLuaMods(getLuaDirectory(), engine, dist);

        // Re-attach registered blocks to the new VM: the registry is frozen, the Lua
        // closures are not. Without this every :OnTick/:ItemFilter/:OnRightClick kept
        // calling the engine that was just shut down.
        com.luatweaker.platform.container.ContainerSpecRebinder.rebindAll(
                contentService, com.luatweaker.platform.content.NeoForgeContentRegistry::parseLocation);

        // Re-attach registered items to the new VM: without this, item callbacks
        // (:OnUseTick, :OnReleaseUsing, :OnSwing, :OnHurtEnemy, etc.) kept calling
        // the engine that was shut down.
        com.luatweaker.platform.content.registrar.ItemRegistrar.rebindAll(
                contentService, com.luatweaker.platform.content.NeoForgeContentRegistry::parseLocation);

        // Loud warning for keybind payloads that have no matching server RemoteEvent
        // (typo'd names would otherwise silently drop every key press).
        com.luatweaker.platform.bootstrap.KeyBindPayloadValidator.validate();

        // Materialize Lua-registered worldgen + loot entries into datapack files.
        applyLuaDatapackProviders();

        if (server != null) {
            // RegisterCommandsEvent fired once at startup, so brand-new Lua command
            // paths registered by this reload would 404 until a datapack reload or
            // restart. Changed handlers need nothing (wrappers resolve live).
            // Deleted commands are NOT removed here (Brigadier has no unregister);
            // they stop working on the next datapack reload.
            commandRegistry.registerNewLuaCommands(server.getCommands().getDispatcher());
        }

        // Kick off declarative update checks for mods that declare update_url.
        com.luatweaker.update.UpdateServiceImpl.checkAll();

        InterceptionHelper.populatePendingEvents(recipeManager.getModifications());

        if (server != null) {
            com.luatweaker.api.log.LuaTweakerLog.get().stageBegin(LogStage.RECIPE_APPLY);
            RecipeManager mcRecipeManager = server.getRecipeManager();
            InterceptionHelper.applyModifications(mcRecipeManager, recipeManager.getModifications());
            com.luatweaker.api.log.LuaTweakerLog.get().stageEnd(LogStage.RECIPE_APPLY,
                    System.currentTimeMillis() - startTime);
        }
        com.luatweaker.api.log.LuaTweakerLog.get().stageEnd(LogStage.RELOAD,
                System.currentTimeMillis() - startTime);
        ACTIVE_ENGINE.set(engine);
        LOGGER.info("reloadServerRecipes completed in {}ms", System.currentTimeMillis() - startTime);
    }

    public static void registerPayloads(final net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        final net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(MODID)
                .versioned("1.1.0");
        registrar.playBidirectional(
                com.luatweaker.platform.network.LuaTweakerPayload.TYPE,
                com.luatweaker.platform.network.LuaTweakerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleLuaPayload(payload, context)));
        registrar.playToServer(
                com.luatweaker.platform.network.ContainerButtonPayload.TYPE,
                com.luatweaker.platform.network.ContainerButtonPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleContainerButtonPayload(payload, context)));
        registrar.playToClient(
                com.luatweaker.platform.network.ItemAnimationPayload.TYPE,
                com.luatweaker.platform.network.ItemAnimationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    com.luatweaker.platform.client.ClientItemAnimationHandler.trigger(
                            payload.entityId(), payload.animationName(), payload.durationTicks(), payload.speed());
                }));
        registrar.playToClient(
                com.luatweaker.platform.network.OpenGuiEditorPayload.TYPE,
                com.luatweaker.platform.network.OpenGuiEditorPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                        com.luatweaker.platform.client.ClientGuiOpener.openGuiEditor(
                                payload.title(), payload.guiWidth(), payload.guiHeight(), payload.isJeiMode());
                    }
                }));
    }

    private static void handleContainerButtonPayload(
            com.luatweaker.platform.network.ContainerButtonPayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
            net.minecraft.world.level.Level level = player.level();
            net.minecraft.core.BlockPos pos = payload.pos();
            if (level.hasChunkAt(pos)) {
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                var handler = com.luatweaker.platform.container.CustomContainerRegistry.BUTTON_HANDLERS.get(state.getBlock());
                if (handler != null) {
                    try {
                        java.util.Map<String, Object> posMap = java.util.Map.of(
                                "X", pos.getX(), "Y", pos.getY(), "Z", pos.getZ());
                        handler.accept(posMap, payload.buttonId());
                    } catch (Exception e) {
                        com.luatweaker.api.log.LuaTweakerLog.get().error(
                                LogStage.SYSTEM,
                                "Failed container button handler for " + payload.buttonId() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    private static void handleLuaPayload(com.luatweaker.platform.network.LuaTweakerPayload payload,
                                         net.neoforged.neoforge.network.handling.IPayloadContext context) {
        boolean serverbound = context.flow().isServerbound();
        String connectionKey = serverbound
                ? "S:" + (context.player() != null ? context.player().getUUID() : "")
                : "C:";
        String channel = payload.channelName();

        byte[] data;
        try {
            data = INBOUND_CHUNKS.accept(connectionKey, channel, payload.index(), payload.total(),
                    payload.data());
        } catch (RuntimeException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    LogStage.SYSTEM,
                    "[Network] Dropped frame '" + channel + "': " + e.getMessage());
            INBOUND_CHUNKS.clear(connectionKey, channel);
            return;
        }
        if (data == null) {
            return; // still waiting for the remaining frames
        }

        if (!serverbound && dispatchClientboundChannel(channel, data)) {
            return;
        }

        if (com.luatweaker.network.NetworkServiceImpl.RPC_CHANNEL.equals(channel)) {
            handleRpcFrame(data, serverbound, context);
            return;
        }

        if (serverbound) {
            dispatchServerbound(channel, data, context);
            return;
        }
        dispatchClientbound(channel, data);
    }

    /** Handles the client-only channels; true when the payload was consumed. */
    private static boolean dispatchClientboundChannel(String channel, byte[] data) {
        if (SYNC_DATA_CHANNEL.equals(channel)) {
            handleClientSyncData(new String(data, java.nio.charset.StandardCharsets.UTF_8));
            return true;
        }
        if (OPEN_SCREEN_CHANNEL.equals(channel)) {
            handleClientOpenScreen(new String(data, java.nio.charset.StandardCharsets.UTF_8));
            return true;
        }
        if (CLOSE_SCREEN_CHANNEL.equals(channel)) {
            Platform.getClient().closeScreen();
            return true;
        }
        return false;
    }

    private static void dispatchServerbound(String channel, byte[] data,
                                            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        String playerUuid = context.player() != null ? context.player().getUUID().toString() : "";
        com.luatweaker.network.NetworkServiceImpl ns = SERVER_NETWORK_SERVICE.get();
        if (ns != null) {
            ns.OnClientFired(channel, playerUuid, data);
        } else {
            LOGGER.warn("SERVER_NETWORK_SERVICE.get() is null when handling serverbound payload {}", channel);
        }
    }

    private static void dispatchClientbound(String channel, byte[] data) {
        // Clientbound: the OnClientEvent listeners are connected on the network service of
        // the CURRENTLY ACTIVE engine (where the mods loaded), not on the client setup engine
        // which never loads mods.
        Object ns = com.luatweaker.core.service.LuaServiceRegistry.get("NetworkServiceImpl");
        if (ns instanceof com.luatweaker.network.NetworkServiceImpl networkService) {
            networkService.OnServerFired(channel, "", data);
        } else {
            LOGGER.warn("No active NetworkService for clientbound payload {}", channel);
        }
    }

    private static void handleRpcFrame(byte[] data, boolean serverbound,
                                       net.neoforged.neoforge.network.handling.IPayloadContext context) {
        com.luatweaker.network.NetworkServiceImpl ns = rpcService(serverbound);
        if (ns == null) {
            LOGGER.warn("No NetworkService to handle a RemoteFunction frame (serverbound={})", serverbound);
            return;
        }
        com.luatweaker.network.RpcEnvelope envelope;
        try {
            envelope = com.luatweaker.network.RpcEnvelope.decode(data);
        } catch (RuntimeException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    LogStage.SYSTEM,
                    "[Network] Dropped malformed RemoteFunction envelope: " + e.getMessage());
            return;
        }
        if (envelope.phase() == com.luatweaker.network.RpcEnvelope.PHASE_INVOKE) {
            String playerUuid = serverbound && context.player() != null
                    ? context.player().getUUID().toString() : "";
            ns.OnRpcInvoke(envelope.requestId(), envelope.functionName(), playerUuid, envelope.payload());
            return;
        }
        ns.OnRpcResult(envelope.requestId(), envelope.payload(), envelope.error());
    }

    private static void handleClientSyncData(String dataJson) {
        Platform.getClient().handleClientSyncData(dataJson);
    }

    private static void handleClientOpenScreen(String dataJson) {
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(dataJson).getAsJsonObject();
            String screenId = obj.get("id").getAsString();
            String title = obj.has("title") ? obj.get("title").getAsString() : "";
            Object gs = com.luatweaker.core.service.LuaServiceRegistry.get("GuiService");
            if (gs instanceof com.luatweaker.api.client.IGuiService guiService) {
                guiService.openScreen(screenId, title, false);
            } else {
                Platform.getClient().openScreen(screenId, title, null, ACTIVE_ENGINE.get(), false);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to open canvas screen: {}", e.getMessage());
        }
    }

    /**
     * Serverbound packets belong to the server engine; clientbound ones belong to the engine
     * that actually loaded the mods (the active engine), never to a bare client setup engine.
     */
    private static com.luatweaker.network.NetworkServiceImpl rpcService(boolean serverbound) {
        if (serverbound) {
            return SERVER_NETWORK_SERVICE.get();
        }
        Object ns = com.luatweaker.core.service.LuaServiceRegistry.get("NetworkServiceImpl");
        return ns instanceof com.luatweaker.network.NetworkServiceImpl networkService ? networkService : null;
    }

    private void generateStubs() {
        com.luatweaker.api.log.LuaTweakerLog.get().info(LogStage.STUB_GEN,
                "Generating autocomplete stubs...");
        LtvmStubGenerator stubGen = new LtvmStubGenerator();
        // Static Module Namespaces (require("LuaTweaker.ModuleName"))
        stubGen.registerService("Content", com.luatweaker.api.content.IContentService.class);
        stubGen.registerService("Recipe", com.luatweaker.api.recipe.IRecipeManagerService.class);
        stubGen.registerService("Events", com.luatweaker.api.event.IEventService.class);
        stubGen.registerService("World", com.luatweaker.api.interaction.IInteractionService.class);
        stubGen.registerService("Entities", com.luatweaker.api.interaction.IInteractionService.class);
        stubGen.registerService("Storage", com.luatweaker.api.content.IStorageService.class);
        stubGen.registerService("Datapack", com.luatweaker.api.content.IDatapackService.class);
        stubGen.registerService("Network", com.luatweaker.api.network.IRocketNetworkService.class);
        stubGen.registerService("AIGoals", com.luatweaker.api.entity.ai.IAIGoalService.class);
        stubGen.registerService("Interaction", com.luatweaker.api.interaction.IInteractionService.class);
        stubGen.registerService("GuiService", com.luatweaker.api.client.IGuiService.class);
        stubGen.registerService("Commands", com.luatweaker.api.command.ICommandService.class);
        stubGen.registerService("Update", com.luatweaker.api.update.IUpdateService.class);
        stubGen.registerService("Net", com.luatweaker.api.web.IWebService.class);
        stubGen.registerService("Noise", com.luatweaker.api.noise.INoiseService.class);
        stubGen.registerService("Dimensions", com.luatweaker.api.dimension.IDimensionService.class);
        stubGen.registerService("SpawnRules", com.luatweaker.api.spawn.ISpawnRuleService.class);
        stubGen.registerService("Biomes", com.luatweaker.api.biome.IBiomesService.class);
        stubGen.registerService("Json", com.luatweaker.api.json.IJsonService.class);

        // Runtime wrapper classes (entity/player tables created dynamically at runtime)
        stubGen.registerClassStub(com.luatweaker.api.entity.IEntity.class, "Entity");
        stubGen.registerClassStub(com.luatweaker.api.entity.IPlayer.class, "Player");
        stubGen.registerClassStub(com.luatweaker.api.command.ICommandSender.class, "CommandSender");
        stubGen.registerClassStub(com.luatweaker.api.content.IBlockRenderer.class, "BlockRenderer");
        // Per-mod file handles returned by mod:Files() / mod:Exports().
        stubGen.registerClassStub(com.luatweaker.api.mod.IModFiles.class, "ModFiles");
        stubGen.registerClassStub(com.luatweaker.api.mod.IModExports.class, "ModExports");

        // Out-of-tree Java plugins contribute their @LuaDoc interfaces here so
        // external APIs get the same EmmyLua autocomplete as built-in services.
        try {
            for (com.luatweaker.api.plugin.ILuaTweakerPlugin plugin
                    : com.luatweaker.core.plugin.LuaTweakerPluginLoader.discoverPlugins()) {
                for (java.util.Map.Entry<String, Class<?>> stub : plugin.stubServices().entrySet()) {
                    if (stub.getKey() != null && stub.getValue() != null) {
                        try {
                            stubGen.registerService(stub.getKey(), stub.getValue());
                        } catch (Exception inner) {
                            LOGGER.warn("Plugin '{}' stub '{}' failed: {}",
                                plugin.pluginId(), stub.getKey(), inner.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Java plugin stub discovery failed: {}", e.getMessage());
        }

        // Legacy Mod Service Names (Compatibility)
        stubGen.registerService("Recipes", com.luatweaker.api.recipe.IRecipeManagerService.class);
        stubGen.registerService("Startup", com.luatweaker.api.content.IContentService.class);
        stubGen.registerService("WorldStorage", com.luatweaker.api.storage.IRobloxStorageService.IDataStore.class);
        stubGen.registerService("PlayerStorage", com.luatweaker.api.storage.IRobloxStorageService.class);
        stubGen.registerService("SessionStorage", com.luatweaker.api.storage.IRobloxStorageService.IDataStore.class);
        stubGen.registerService("NetworkService", com.luatweaker.api.network.IRocketNetworkService.class);
        stubGen.registerService("Workspace", com.luatweaker.api.interaction.IInteractionService.class);
        stubGen.registerService("EntityService", com.luatweaker.api.interaction.IInteractionService.class);
        LtvmStubExporter.exportToWorkspace(new File(".").toPath(), stubGen);
    }
}
