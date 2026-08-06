package com.luatweaker.platform;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.*;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.lsp.LtvmStubExporter;
import com.luatweaker.core.lsp.LtvmStubGenerator;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.platform.command.LuaTweakerCommandRegistry;
import com.luatweaker.platform.config.LuaTweakerConfig;
import com.luatweaker.platform.recipe.InterceptionHelper;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import com.luatweaker.recipes.RecipesLuaBinding;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Mod(LuaTweakerMod.MODID)
public class LuaTweakerMod {
    public static final String MODID = "luatweaker";

    static {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LuaTweakerMod.class);

    private static ILuaEngine activeEngine;
    private static com.luatweaker.network.NetworkServiceImpl serverNetworkService;
    private static com.luatweaker.network.NetworkServiceImpl clientNetworkService;

    public static ILuaEngine getActiveEngine() {
        return activeEngine;
    }

    /**
     * Pumps the Lua task queues of the ACTIVE engine (bootstrap {@code task._tick}
     * plus the Java {@code _java_tick}). Every {@code Signal:Fire}, {@code Task.spawn},
     * {@code task.delay} and {@code task.wait} schedules through these queues, so this
     * MUST be called once per game tick (server tick + client tick); without it all
     * async Lua callbacks (network RemoteEvents, mana loops, HUD sync) silently die.
     */
    public static void tickActiveEngineTasks() {
        ILuaEngine engine = activeEngine;
        if (engine == null) return;
        ILuaValue taskVal = engine.getGlobalEnvironment().rawget("task");
        if (taskVal == null || !taskVal.isTable()) return;
        ILuaTable taskTbl = taskVal.asTable();
        ILuaValue luaTick = taskTbl.rawget("_tick");
        if (luaTick != null && luaTick.isFunction()) {
            engine.callFunction(luaTick);
        }
        ILuaValue javaTick = taskTbl.rawget("_java_tick");
        if (javaTick != null && javaTick.isFunction()) {
            engine.callFunction(javaTick);
        }
    }

    public static void setServerNetworkService(com.luatweaker.network.NetworkServiceImpl service) {
        serverNetworkService = service;
    }

    public static void setClientNetworkService(com.luatweaker.network.NetworkServiceImpl service) {
        clientNetworkService = service;
    }

    public static com.luatweaker.network.NetworkServiceImpl getServerNetworkService() {
        return serverNetworkService;
    }

    public static com.luatweaker.network.NetworkServiceImpl getClientNetworkService() {
        return clientNetworkService;
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

    private static LuaTweakerMod INSTANCE;

    public static LuaTweakerMod getInstance() {
        return INSTANCE;
    }

    private void initClientPlatform() {
        Platform.setClient(new com.luatweaker.platform.client.NeoForgeClientPlatform());
    }

    public LuaTweakerMod(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        LOGGER.info("LuaTweaker constructor starting");
        LOGGER.info("CWD: {}", new File(".").getAbsolutePath());

        // Register NeoForge configuration specification
        modContainer.registerConfig(ModConfig.Type.COMMON, LuaTweakerConfig.COMMON_SPEC);

        // Initialize Platform Abstraction Layer (PAL) Helpers
        Platform.setNetwork(new com.luatweaker.platform.network.NeoForgeNetworkPlatform());
        Platform.setEntity(new com.luatweaker.platform.entity.NeoForgeEntityPlatform());
        Platform.setInteraction(new com.luatweaker.platform.interaction.NeoForgeInteractionPlatform());
        Platform.setContent(new com.luatweaker.platform.content.NeoForgeContentPlatform());
        Platform.setStorage(new com.luatweaker.platform.storage.NeoForgeStoragePlatform());
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            initClientPlatform();
        }
        LOGGER.info("Platform helpers set");

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
        modEventBus.addListener(this::onClientSetup);

        // Register Game Event Bus listeners (gameplay events)
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(
                new com.luatweaker.platform.content.NeoForgeContentRegistry.BossBarTickHandler(contentService));
        com.luatweaker.platform.event.UniversalEventForwarder.registerAll();

        if (Platform.getContent().isClient()) {
            modEventBus.addListener(com.luatweaker.platform.client.DynamicKeyMappingHandler::onRegisterKeyMappings);
            NeoForge.EVENT_BUS.addListener(com.luatweaker.platform.client.DynamicKeyMappingHandler::onClientTick);
            NeoForge.EVENT_BUS.addListener(com.luatweaker.platform.client.NeoForgeWorldRenderEventListener::onRenderLevel);
        }
        // Server tick pump for the Lua task queues is registered via @SubscribeEvent
        // on onServerTick (LuaTweakerMod is registered on NeoForge.EVENT_BUS above).

        // Build the command registry (core commands auto-registered inside)
        commandRegistry = new LuaTweakerCommandRegistry(luaDir);
    }

    private void loadStartupLuaMods(File luaDir) {
        ILuaEngine startupEngine = new CobaltLuaEngine(isDebugEnabled());
        startupEngine.setLuaDirectory(luaDir);
        com.luatweaker.platform.bootstrap.LuaServiceBootstrap.registerAllServices(
                startupEngine, contentService, storageService, datapackService, new NeoForgeRecipeManager()
        );

        // Fresh command set per load cycle: only the mods loaded below register commands.
        com.luatweaker.command.CommandServiceImpl.clear();
        // Load Autonomous LuaMods from luamods/ directory
        String dist = net.neoforged.fml.loading.FMLEnvironment.dist.isClient() ? "universal" : "server";
        com.luatweaker.core.mod.LuaModManager.loadLuaMods(luaDir, startupEngine, dist);

        // Loud warning for keybind payloads that have no matching server RemoteEvent
        // (typo'd names would otherwise silently drop every key press).
        com.luatweaker.platform.bootstrap.KeyBindPayloadValidator.validate();

        // Materialize Lua-registered worldgen + loot entries into datapack files.
        applyLuaDatapackProviders();

        // Kick off declarative update checks for mods that declare update_url.
        com.luatweaker.update.UpdateServiceImpl.checkAll();
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
    }

    private void onClientSetup(final net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        ILuaEngine engine = activeEngine != null ? activeEngine : new CobaltLuaEngine(isDebugEnabled());
        engine.setLuaDirectory(getLuaDirectory());
        activeEngine = engine;

        com.luatweaker.client.ClientServiceImpl clientService = new com.luatweaker.client.ClientServiceImpl();
        com.luatweaker.client.ClientLuaBinding.registerBindings(engine, clientService);

        ILuaValue clientVal = engine.getGlobalEnvironment().rawget("Client");
        if (clientVal != null && clientVal.isTable()) {
            com.luatweaker.platform.client.NeoForgeWorldRenderEventListener.registerRenderService(engine, clientVal.asTable());
        }

        com.luatweaker.math.MathLuaBinding.registerBindings(engine);

        com.luatweaker.tasks.TaskServiceImpl taskService = new com.luatweaker.tasks.TaskServiceImpl();
        com.luatweaker.tasks.TaskLuaBinding.registerBindings(engine, taskService);

        com.luatweaker.interception.InterceptionServiceImpl interceptionService = new com.luatweaker.interception.InterceptionServiceImpl();
        com.luatweaker.interception.InterceptionLuaBinding.registerBindings(engine, interceptionService);

        com.luatweaker.network.NetworkServiceImpl clientNetService = new com.luatweaker.network.NetworkServiceImpl(engine);
        setClientNetworkService(clientNetService);
        com.luatweaker.network.NetworkLuaBinding.registerBindings(engine, clientNetService);
        com.luatweaker.events.EventLuaBinding.registerBindings(engine);
        com.luatweaker.interaction.InteractionLuaBinding.registerBindings(engine);
    }

    /**
     * Expose the registry so addon modules can call commandRegistry.register(myCmd)
     * during setup.
     */
    public LuaTweakerCommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    private void initializeUserDirectories() {
        File luaDir = getLuaDirectory();
        if (!luaDir.exists()) {
            luaDir.mkdirs();
            com.luatweaker.api.log.LuaTweakerLog.get().info(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Created main Lua directory at: " + luaDir.getAbsolutePath());
        }

        String[] requiredSubDirs = new String[] { ".luatweaker/stubs" };
        for (String sub : requiredSubDirs) {
            File subDir = new File(luaDir, sub);
            if (!subDir.exists()) {
                subDir.mkdirs();
            }
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
        try {
            return LuaTweakerConfig.DEBUG.get();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAutoStubsEnabled() {
        try {
            return LuaTweakerConfig.AUTO_GENERATE_STUBS.get();
        } catch (Exception e) {
            return true;
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        commandRegistry.build(event);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;

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
    }

    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // ALWAYS pump from the server thread: Lua game logic (mana loops, summoning,
        // AI goals, RemoteEvent listeners) touches server-side Minecraft APIs that
        // must run on the server thread. Pumping from the client tick (singleplayer)
        // makes e.g. ServerLevel.addFreshEntity run on the client thread, which races
        // with the integrated server and freezes the game.
        tickActiveEngineTasks();
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        LOGGER.info("ServerAboutToStartEvent fired, calling reloadServerRecipes");
        reloadServerRecipes(event.getServer());
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
        LOGGER.info("RegisterBrewingRecipesEvent fired, executing Lua scripts for brewing registration...");
        reloadServerRecipes(null);
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

    private long lastReloadTime = 0;

    public synchronized void reloadServerRecipes(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - lastReloadTime < 500) {
            LOGGER.info("Skipping redundant reloadServerRecipes call (reloaded {}ms ago)", now - lastReloadTime);
            return;
        }
        lastReloadTime = now;

        LOGGER.info("reloadServerRecipes started (server: {})", server != null ? "active" : "null/early");
        long startTime = System.currentTimeMillis();
        boolean debugMode = isDebugEnabled();

        AsyncFileLogger.get().setDebugEnabled(debugMode);
        com.luatweaker.api.log.LuaTweakerLog.get().stageBegin(com.luatweaker.api.log.LogStage.RELOAD);
        com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.RELOAD,
                "Reloading Lua server scripts... (Debug: " + debugMode + ")");

        if (activeEngine != null) {
            com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.RELOAD,
                    "Firing OnScriptUnload teardown hooks...");
            try {
                com.luatweaker.events.EventServiceImpl.fireTeardownHooks();
            } catch (Exception e) {
                com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RELOAD,
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
        activeEngine = new CobaltLuaEngine(debugMode);
        ILuaEngine engine = activeEngine;
        engine.setLuaDirectory(getLuaDirectory());

        com.luatweaker.core.service.LuaServiceRegistry.clear();
        com.luatweaker.platform.bootstrap.LuaServiceBootstrap.registerAllServices(engine, contentService,
                storageService, datapackService, recipeManager);

        // Fresh command set per load cycle: disabled/removed mods must not leave
        // stale /lt commands behind (see CommandServiceImpl.clear()).
        com.luatweaker.command.CommandServiceImpl.clear();

        // Load Autonomous LuaMods from luamods/
        String dist = net.neoforged.fml.loading.FMLEnvironment.dist.isClient() ? "universal" : "server";
        com.luatweaker.core.mod.LuaModManager.loadLuaMods(getLuaDirectory(), engine, dist);

        // Loud warning for keybind payloads that have no matching server RemoteEvent
        // (typo'd names would otherwise silently drop every key press).
        com.luatweaker.platform.bootstrap.KeyBindPayloadValidator.validate();

        // Materialize Lua-registered worldgen + loot entries into datapack files.
        applyLuaDatapackProviders();

        // Kick off declarative update checks for mods that declare update_url.
        com.luatweaker.update.UpdateServiceImpl.checkAll();

        InterceptionHelper.populatePendingEvents(recipeManager.getModifications());

        if (server != null) {
            com.luatweaker.api.log.LuaTweakerLog.get().stageBegin(com.luatweaker.api.log.LogStage.RECIPE_APPLY);
            RecipeManager mcRecipeManager = server.getRecipeManager();
            InterceptionHelper.applyModifications(mcRecipeManager, recipeManager.getModifications());
            com.luatweaker.api.log.LuaTweakerLog.get().stageEnd(com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    System.currentTimeMillis() - startTime);
        }
        com.luatweaker.api.log.LuaTweakerLog.get().stageEnd(com.luatweaker.api.log.LogStage.RELOAD,
                System.currentTimeMillis() - startTime);
        activeEngine = engine;
        LOGGER.info("reloadServerRecipes completed in {}ms", System.currentTimeMillis() - startTime);
    }

    public static void registerPayloads(final net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        final net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(MODID)
                .versioned("1.0.0");
        registrar.playBidirectional(
                com.luatweaker.platform.network.LuaTweakerPayload.TYPE,
                com.luatweaker.platform.network.LuaTweakerPayload.STREAM_CODEC,
                (payload, context) -> {
                    com.luatweaker.api.log.LuaTweakerLog.get().info(
                            com.luatweaker.api.log.LogStage.SYSTEM,
                            "[Network Packet Received] Channel='" + payload.channelName() + "', Flow=" + context.flow()
                    );
                    context.enqueueWork(() -> {
                        ILuaEngine engine = activeEngine;
                        if (engine != null) {
                            ILuaValue[] args = parseJsonToLua(engine, payload.dataJson());
                            if (context.flow().isServerbound()) {
                                net.minecraft.world.entity.player.Player player = context.player();
                                String playerUuid = player != null ? player.getUUID().toString() : "";
                                com.luatweaker.network.NetworkServiceImpl ns = serverNetworkService;
                                if (ns != null) {
                                    ns.OnClientFired(payload.channelName(), playerUuid, args);
                                } else {
                                    LOGGER.warn("serverNetworkService is null when handling serverbound payload {}", payload.channelName());
                                }
                            } else {
                                // Clientbound: the OnClientEvent listeners are connected on the
                                // network service of the CURRENTLY ACTIVE engine (where the mods
                                // loaded), not on the client setup engine which never loads mods.
                                Object ns = com.luatweaker.core.service.LuaServiceRegistry.get("NetworkServiceImpl");
                                if (ns instanceof com.luatweaker.network.NetworkServiceImpl networkService) {
                                    networkService.OnServerFired(payload.channelName(), "", args);
                                } else {
                                    LOGGER.warn("No active NetworkService for clientbound payload {}", payload.channelName());
                                }
                            }
                        }
                    });
                });
    }

    private static ILuaValue[] parseJsonToLua(ILuaEngine engine, String json) {
        try {
            com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            ILuaValue[] result = new ILuaValue[array.size()];
            for (int i = 0; i < array.size(); i++) {
                result[i] = parseElement(engine, array.get(i));
            }
            return result;
        } catch (Exception e) {
            return new ILuaValue[0];
        }
    }

    private static ILuaValue parseElement(ILuaEngine engine, com.google.gson.JsonElement el) {
        if (el == null || el.isJsonNull())
            return engine.nilValue();
        if (el.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isBoolean())
                return engine.wrapBoolean(p.getAsBoolean());
            if (p.isNumber())
                return engine.wrapNumber(p.getAsDouble());
            return engine.wrapString(p.getAsString());
        }
        if (el.isJsonObject()) {
            ILuaTable table = engine.createTable();
            com.google.gson.JsonObject obj = el.getAsJsonObject();
            for (String key : obj.keySet()) {
                table.rawset(key, parseElement(engine, obj.get(key)));
            }
            return table;
        }
        return engine.nilValue();
    }

    private void generateStubs() {
        com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.STUB_GEN,
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
        stubGen.registerService("WorldAction", com.luatweaker.api.entity.ai.IWorldActionService.class);
        stubGen.registerService("Interaction", com.luatweaker.api.interaction.IInteractionService.class);
        stubGen.registerService("GuiService", com.luatweaker.api.client.IGuiService.class);
        stubGen.registerService("Commands", com.luatweaker.api.command.ICommandService.class);
        stubGen.registerService("Update", com.luatweaker.api.update.IUpdateService.class);
        stubGen.registerService("Net", com.luatweaker.api.web.IWebService.class);

        // Runtime wrapper classes (entity/player tables created dynamically at runtime)
        stubGen.registerClassStub(com.luatweaker.api.entity.IEntity.class, "Entity");
        stubGen.registerClassStub(com.luatweaker.api.entity.IPlayer.class, "Player");
        stubGen.registerClassStub(com.luatweaker.api.command.ICommandSender.class, "CommandSender");

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
