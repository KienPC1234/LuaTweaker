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
        System.err.println("LuaTweakerMod: static init at " + new java.util.Date());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LuaTweakerMod.class);

    private static ILuaEngine activeEngine;

    public static ILuaEngine getActiveEngine() {
        return activeEngine;
    }

    /**
     * The command registry; held as a field so external modules can register
     * commands before build().
     */
    private final LuaTweakerCommandRegistry commandRegistry;

    private final com.luatweaker.content.ContentServiceImpl contentService;
    private final com.luatweaker.content.StorageServiceImpl storageService;
    private final com.luatweaker.content.DatapackServiceImpl datapackService;

    private static LuaTweakerMod INSTANCE;

    public static LuaTweakerMod getInstance() {
        return INSTANCE;
    }

    public LuaTweakerMod(IEventBus modEventBus, ModContainer modContainer) {
        INSTANCE = this;
        System.err.println("LuaTweakerMod: static init at " + new java.util.Date());
        LOGGER.info("LuaTweaker constructor starting");
        LOGGER.info("CWD: {}", new File(".").getAbsolutePath());

        // Register NeoForge configuration specification
        modContainer.registerConfig(ModConfig.Type.COMMON, LuaTweakerConfig.COMMON_SPEC);

        // Initialize Platform Abstraction Layer (PAL) Helper
        Platform.set(new NeoForgePlatformHelper());
        LOGGER.info("Platform helper set");

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

        // Run startup scripts (Mod Construction phase)
        runStartupScripts(luaDir);

        // Register Mod Event Bus listeners for Content Registry and Asset Pack Finder
        modEventBus.register(new com.luatweaker.platform.content.NeoForgeContentRegistry(contentService));
        modEventBus.register(
                new com.luatweaker.platform.content.LuaAssetsPackFinder(luaDir, datapackService, contentService));
        modEventBus.addListener(LuaTweakerMod::registerPayloads);
        modEventBus.addListener(this::onClientSetup);

        // Register Game Event Bus listeners (gameplay events)
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(
                new com.luatweaker.platform.content.NeoForgeContentRegistry.BossBarTickHandler(contentService));
        NeoForge.EVENT_BUS.register(new com.luatweaker.platform.event.NeoForgeGameEventListener());

        if (Platform.get().isClient()) {
            modEventBus.addListener(com.luatweaker.platform.client.DynamicKeyMappingHandler::onRegisterKeyMappings);
            NeoForge.EVENT_BUS.addListener(com.luatweaker.platform.client.DynamicKeyMappingHandler::onClientTick);
        }

        // Build the command registry (core commands auto-registered inside)
        commandRegistry = new LuaTweakerCommandRegistry(luaDir);
    }

    private void runStartupScripts(File luaDir) {
        File startupDir = new File(luaDir, "startup");
        if (!startupDir.exists() || !startupDir.isDirectory())
            return;

        ILuaEngine startupEngine = new CobaltLuaEngine(isDebugEnabled());
        startupEngine.setLuaDirectory(luaDir);
        com.luatweaker.platform.bootstrap.LuaServiceBootstrap.registerAllServices(
                startupEngine, contentService, storageService, datapackService, new NeoForgeRecipeManager()
        );

        List<File> files = collectLuaFilesRecursively(startupDir);
        for (File f : files) {
            com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SCRIPT_LOAD,
                    "Executing startup script: " + f.getName());
            try {
                startupEngine.executeScript(f, "STARTUP");
            } catch (Exception e) {
                LOGGER.error("Failed to execute startup script: " + f.getName(), e);
            }
        }
    }

    public void runClientScripts(File luaDir) {
        File clientDir = new File(luaDir, "client");
        if (!clientDir.exists() || !clientDir.isDirectory())
            return;

        ILuaEngine engine = activeEngine != null ? activeEngine : new CobaltLuaEngine(isDebugEnabled());
        engine.setLuaDirectory(luaDir);
        activeEngine = engine;

        com.luatweaker.client.ClientServiceImpl clientService = new com.luatweaker.client.ClientServiceImpl();
        com.luatweaker.client.ClientLuaBinding.registerBindings(engine, clientService);

        com.luatweaker.tasks.TaskServiceImpl taskService = new com.luatweaker.tasks.TaskServiceImpl();
        com.luatweaker.tasks.TaskLuaBinding.registerBindings(engine, taskService);

        com.luatweaker.interception.InterceptionServiceImpl interceptionService = new com.luatweaker.interception.InterceptionServiceImpl();
        com.luatweaker.interception.InterceptionLuaBinding.registerBindings(engine, interceptionService);

        com.luatweaker.math.MathLuaBinding.registerBindings(engine);
        com.luatweaker.network.NetworkServiceImpl clientNetworkService = new com.luatweaker.network.NetworkServiceImpl(
                engine);
        com.luatweaker.network.NetworkLuaBinding.registerBindings(engine, clientNetworkService);
        com.luatweaker.events.EventLuaBinding.registerBindings(engine);
        com.luatweaker.interaction.InteractionLuaBinding.registerBindings(engine);

        List<File> files = collectLuaFilesRecursively(clientDir);
        for (File f : files) {
            com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SCRIPT_LOAD,
                    "Executing client script: " + f.getName());
            try {
                engine.executeScript(f, "CLIENT");
            } catch (Exception e) {
                LOGGER.error("Failed to execute client script: " + f.getName(), e);
            }
        }
    }

    private void onClientSetup(final net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        runClientScripts(getLuaDirectory());
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

        String[] requiredSubDirs = new String[] { "startup", "server", "client", "lib", ".luatweaker/stubs",
                "logs/luatweaker" };
        for (String sub : requiredSubDirs) {
            File subDir = new File(luaDir, sub);
            if (!subDir.exists()) {
                boolean created = subDir.mkdirs();
                if (created || subDir.exists()) {
                    com.luatweaker.api.log.LuaTweakerLog.get().info(
                            com.luatweaker.api.log.LogStage.SYSTEM,
                            "Created missing Lua sub-directory: " + subDir.getAbsolutePath());
                }
            }
        }
    }

    private File getLuaDirectory() {
        File gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toFile();
        File defaultLuaDir = new File(gameDir, "lua");
        if (hasLuaScripts(defaultLuaDir)) {
            return defaultLuaDir;
        }

        File relativeLuaDir = new File("lua");
        if (hasLuaScripts(relativeLuaDir)) {
            return relativeLuaDir;
        }

        return defaultLuaDir;
    }

    private boolean hasLuaScripts(File dir) {
        if (!dir.exists() || !dir.isDirectory())
            return false;
        File[] subDirs = new File[] {
                new File(dir, "server"),
                new File(dir, "startup"),
                new File(dir, "client"),
                dir
        };
        for (File subDir : subDirs) {
            if (subDir.exists() && subDir.isDirectory()) {
                File[] luaFiles = subDir.listFiles((d, name) -> name.endsWith(".lua"));
                if (luaFiles != null && luaFiles.length > 0) {
                    return true;
                }
            }
        }
        return false;
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
                    com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.STUB_GEN,
                            "Generating autocomplete stubs...");
                    LtvmStubGenerator stubGen = new LtvmStubGenerator();
                    stubGen.registerService("Recipes", com.luatweaker.api.recipe.IRecipeManagerService.class);
                    stubGen.registerService("AIGoals", com.luatweaker.api.entity.ai.IAIGoalService.class);
                    stubGen.registerService("WorldAction", com.luatweaker.api.entity.ai.IWorldActionService.class);
                    stubGen.registerService("Interaction", com.luatweaker.api.interaction.IInteractionService.class);
                    LtvmStubExporter.exportToWorkspace(new File(".").toPath(), stubGen);
                }
            }
        });
    }

    public void reloadServerRecipes(MinecraftServer server) {
        LOGGER.info("reloadServerRecipes started (server: {})", server != null ? "active" : "null/early");
        long startTime = System.currentTimeMillis();
        boolean debugMode = isDebugEnabled();

        AsyncFileLogger.get().setDebugEnabled(debugMode);
        com.luatweaker.api.log.LuaTweakerLog.get().stageBegin(com.luatweaker.api.log.LogStage.RELOAD);
        com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.RELOAD,
                "Reloading Lua server scripts... (Debug: " + debugMode + ")");

        // Clear pending Anvil/Brewing/Trade from previous reload cycle
        InterceptionHelper.clearPending();

        if (isAutoStubsEnabled()) {
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

        NeoForgeRecipeManager recipeManager = new NeoForgeRecipeManager();
        ILuaEngine engine = new CobaltLuaEngine(debugMode);
        engine.setLuaDirectory(getLuaDirectory());

        com.luatweaker.platform.bootstrap.LuaServiceBootstrap.registerAllServices(engine, contentService,
                storageService, datapackService, recipeManager);

        File serverDir = new File(getLuaDirectory(), "server");
        LOGGER.info("Lua server dir: {} (exists: {})", serverDir.getAbsolutePath(), serverDir.exists());

        if (serverDir.exists() && serverDir.isDirectory()) {
            List<File> files = collectLuaFilesRecursively(serverDir);
            LOGGER.info("Found {} Lua scripts to execute in server directory tree", files.size());
            for (File f : files) {
                com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SCRIPT_LOAD,
                        "Executing script: " + f.getName());
                try {
                    engine.executeScript(f, "SERVER");
                    com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SCRIPT_LOAD,
                            "Successfully executed script: " + f.getName());
                } catch (Exception e) {
                    com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.SCRIPT_LOAD,
                            "Failed executing script " + f.getName() + ": " + e.getMessage());
                    LOGGER.error("Failed to execute script: " + f.getName(), e);
                }
            }
        }

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
                    context.enqueueWork(() -> {
                        if (context.flow().isServerbound()) {
                            net.minecraft.world.entity.player.Player player = context.player();
                            if (player != null) {
                                String playerUuid = player.getUUID().toString();
                                ILuaEngine engine = activeEngine;
                                if (engine != null) {
                                    Object netService = com.luatweaker.core.service.LuaServiceRegistry
                                            .get("NetworkService");
                                    if (netService instanceof com.luatweaker.network.NetworkServiceImpl ns) {
                                        ILuaValue[] args = parseJsonToLua(engine, payload.dataJson());
                                        ns.OnClientFired(payload.channelName(), playerUuid, args);
                                    }
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

    private List<File> collectLuaFilesRecursively(File directory) {
        List<File> result = new ArrayList<>();
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return result;
        }
        try (var stream = java.nio.file.Files.walk(directory.toPath())) {
            stream.filter(java.nio.file.Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".lua"))
                  .map(java.nio.file.Path::toFile)
                  .sorted(Comparator.comparing(File::getAbsolutePath))
                  .forEach(result::add);
        } catch (Exception e) {
            LOGGER.error("Failed to scan directory recursively: " + directory.getAbsolutePath(), e);
        }
        return result;
    }
}
