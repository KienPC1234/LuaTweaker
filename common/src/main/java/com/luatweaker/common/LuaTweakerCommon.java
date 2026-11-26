package com.luatweaker.common;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.task.ITaskService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.common.bootstrap.LuaRuntimeReloader;
import com.luatweaker.common.command.LuaTweakerCommandRegistry;
import com.luatweaker.common.content.BowItemProperties;
import com.luatweaker.common.content.ContentRendererRegistrar;
import com.luatweaker.common.content.MinecraftContentPlatform;
import com.luatweaker.common.content.registrar.BlockRegistrar;
import com.luatweaker.common.content.registrar.EntityRegistrar;
import com.luatweaker.common.content.registrar.FluidRegistrar;
import com.luatweaker.common.content.registrar.ItemRegistrar;
import com.luatweaker.common.content.registrar.MobEffectRegistrar;
import com.luatweaker.common.content.registrar.TabRegistrar;
import com.luatweaker.common.content.VirtualPackSource;
import com.luatweaker.common.datapack.MinecraftDatapackPlatform;
import com.luatweaker.common.dimension.MinecraftDimensionPlatform;
import com.luatweaker.common.entity.MinecraftEntityPlatform;
import com.luatweaker.common.event.UniversalEventForwarder;
import com.luatweaker.common.integration.geckolib.GeckoLibIntegration;
import com.luatweaker.common.integration.GenericPlatformIntegration;
import com.luatweaker.common.integration.IntegrationManager;
import com.luatweaker.common.interaction.MinecraftInteractionPlatform;
import com.luatweaker.common.network.CommonNetworkHandler;
import com.luatweaker.common.network.MinecraftNetworkPlatform;
import com.luatweaker.common.storage.MinecraftStoragePlatform;
import com.luatweaker.common.structures.MinecraftStructuresPlatform;
import com.luatweaker.common.worldgen.MinecraftWorldgenPlatform;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.tasks.TaskServiceImpl;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class LuaTweakerCommon {
    public static final String MOD_ID = "luatweaker";
    private static final String MOD_HELLO_CHANNEL = "LuaTweaker:ModHello";
    private static final String MOD_SYNC_CHANNEL = "LuaTweaker:ModSync";

    private static final AtomicReference<ILuaEngine> ACTIVE_ENGINE = new AtomicReference<>();
    private static final AtomicReference<ContentServiceImpl> CONTENT_SERVICE = new AtomicReference<>();
    private static final AtomicReference<DatapackServiceImpl> DATAPACK_SERVICE = new AtomicReference<>();
    private static final AtomicReference<LuaTweakerCommandRegistry> COMMAND_REGISTRY = new AtomicReference<>();
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static final Map<ResourceLocation, Item> CREATED_ITEMS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Block> CREATED_BLOCKS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, EntityType<?>> CREATED_ENTITIES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, java.util.function.Supplier<EntityType<?>>> CREATED_ENTITY_SUPPLIERS = new ConcurrentHashMap<>();
    private static final java.util.Set<ResourceLocation> CREATED_TABS = ConcurrentHashMap.newKeySet();

    private record TaskPumpCache(
            ILuaEngine engine,
            ILuaTable taskTable,
            @Nullable ILuaValue luaTick,
            @Nullable ILuaValue javaTick,
            @Nullable ITaskService javaTaskService) {}

    private static final AtomicReference<TaskPumpCache> TASK_PUMP_CACHE = new AtomicReference<>();

    private LuaTweakerCommon() {
    }

    public static String modHelloChannel() {
        return MOD_HELLO_CHANNEL;
    }

    public static String modSyncChannel() {
        return MOD_SYNC_CHANNEL;
    }

    private static final String TRUNCATION_MARKER = "...";
    private static final int MAX_LOAD_REASON_CHARS = 120;

    /**
     * Builds the in-game load-failure warning, pure for tests (no Minecraft types
     * in the signature). Reasons are truncated so one pathological entry cannot
     * flood chat.
     */
    public static java.util.List<String> buildLoadWarningLines(
            @Nullable Map<String, String> loadErrors, int maxShown) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (loadErrors == null || loadErrors.isEmpty()) {
            return lines;
        }
        lines.add("§c[LuaTweaker] WARNING: " + loadErrors.size() + " LuaMod(s) did not load:");
        java.util.List<String> ids = new java.util.ArrayList<>(loadErrors.keySet());
        java.util.Collections.sort(ids);
        int shown = 0;
        for (String id : ids) {
            if (shown >= maxShown) {
                break;
            }
            String reason = String.valueOf(loadErrors.get(id));
            if (reason.length() > MAX_LOAD_REASON_CHARS) {
                reason = reason.substring(0, MAX_LOAD_REASON_CHARS - TRUNCATION_MARKER.length())
                        + TRUNCATION_MARKER;
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

    public static void setActiveEngine(@Nullable ILuaEngine engine) {
        ACTIVE_ENGINE.set(engine);
        TASK_PUMP_CACHE.set(null);
    }

    public static Map<ResourceLocation, Item> getCreatedItems() {
        return CREATED_ITEMS;
    }

    public static Map<ResourceLocation, Block> getCreatedBlocks() {
        return CREATED_BLOCKS;
    }

    public static ResourceLocation parseLocation(String id) {
        if (id == null || id.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath(MOD_ID, "empty");
        }
        if (id.contains(":")) {
            return ResourceLocation.parse(id);
        }
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
    }

    @Nullable
    public static ILuaEngine getActiveEngine() {
        return ACTIVE_ENGINE.get();
    }

    @Nullable
    public static ContentServiceImpl getContentService() {
        return CONTENT_SERVICE.get();
    }

    @Nullable
    public static DatapackServiceImpl getDatapackService() {
        return DATAPACK_SERVICE.get();
    }

    public static void tickActiveEngineTasks() {
        ILuaEngine engine = ACTIVE_ENGINE.get();
        if (engine == null) {
            return;
        }
        TaskPumpCache pump = resolveTaskPump(engine);
        if (pump == null) {
            return;
        }
        try {
            tickLuaDeferredTasks(engine, pump);
            tickJavaScheduledTasks(engine, pump);
        } catch (IllegalStateException e) {
            TASK_PUMP_CACHE.compareAndSet(pump, null);
            LuaTweakerLog.get().debug(LogStage.SYSTEM, "Lua task pump skipped: engine was shut down by reload");
        }
    }

    private static @Nullable TaskPumpCache resolveTaskPump(@NotNull ILuaEngine engine) {
        TaskPumpCache cached = TASK_PUMP_CACHE.get();
        if (cached != null && engine.equals(cached.engine())) {
            return cached;
        }
        ILuaValue taskVal = engine.getGlobalEnvironment().rawget("task");
        if (taskVal == null || !taskVal.isTable()) {
            return null;
        }
        ILuaTable taskTbl = taskVal.asTable();
        ILuaValue luaTick = taskTbl.rawget("_tick");
        ILuaValue javaTick = taskTbl.rawget("_java_tick");
        boolean hasJavaTick = javaTick != null && javaTick.isFunction();
        Object serviceObj = hasJavaTick
                ? com.luatweaker.core.service.LuaServiceRegistry.get("TaskServiceImpl")
                : null;
        ITaskService taskService = serviceObj instanceof ITaskService ts ? ts : null;
        TaskPumpCache fresh = new TaskPumpCache(
                engine,
                taskTbl,
                luaTick != null && luaTick.isFunction() ? luaTick : null,
                hasJavaTick ? javaTick : null,
                taskService);
        TASK_PUMP_CACHE.set(fresh);
        return fresh;
    }

    private static void tickLuaDeferredTasks(@NotNull ILuaEngine engine, @NotNull TaskPumpCache pump) {
        if (pump.luaTick() == null) {
            return;
        }
        ILuaValue pendingFlag = pump.taskTable().rawget("_hasPending");
        if (pendingFlag != null && pendingFlag.isBoolean() && !pendingFlag.asBoolean()) {
            return;
        }
        engine.callFunction(pump.luaTick());
    }

    private static void tickJavaScheduledTasks(@NotNull ILuaEngine engine, @NotNull TaskPumpCache pump) {
        ITaskService javaTaskService = pump.javaTaskService();
        if (javaTaskService instanceof TaskServiceImpl impl) {
            if (impl.getQueuedTaskCount() > 0) {
                impl.tick(engine);
            }
            return;
        }
        if (javaTaskService != null) {
            javaTaskService.tick(engine);
            return;
        }
        if (pump.javaTick() != null) {
            engine.callFunction(pump.javaTick());
        }
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        AsyncFileLogger fileLogger = AsyncFileLogger.get();
        LuaTweakerLog.set(fileLogger);

        boolean isClient = dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT;
        initPlatform(isClient);

        File gameDir = dev.architectury.platform.Platform.getGameFolder().toFile();
        File luaDir = new File(gameDir, "luamods");
        if (!luaDir.exists()) {
            luaDir.mkdirs();
        }
        VirtualPackSource.setLuaDir(luaDir);

        ContentServiceImpl contentService = new ContentServiceImpl();
        DatapackServiceImpl datapackService = new DatapackServiceImpl();
        CONTENT_SERVICE.set(contentService);
        DATAPACK_SERVICE.set(datapackService);
        com.luatweaker.core.service.LuaServiceRegistry.register("ContentServiceImpl", contentService);
        com.luatweaker.core.service.LuaServiceRegistry.register("DatapackServiceImpl", datapackService);

        LuaRuntimeReloader.loadOrReload(gameDir, luaDir, isClient, contentService, datapackService, true);

        registerContent(contentService, datapackService, gameDir, luaDir, isClient);
        registerCommands(luaDir);
        CommonNetworkHandler.init();
        UniversalEventForwarder.registerAll();
        registerLifecycle();

        LuaTweakerLog.get().info(LogStage.SYSTEM, "[LuaTweaker] Common initialization completed successfully.");
    }

    /**
     * Hot-reloads all LuaMods, rebuilds virtual datapack/stub files, and registers any
     * newly declared top-level Lua commands on the running server dispatcher.
     */
    public static void reloadLuaRuntime(@Nullable net.minecraft.server.MinecraftServer server) {
        ContentServiceImpl contentService = CONTENT_SERVICE.get();
        DatapackServiceImpl datapackService = DATAPACK_SERVICE.get();
        if (contentService == null || datapackService == null) {
            return;
        }
        File gameDir = dev.architectury.platform.Platform.getGameFolder().toFile();
        File luaDir = new File(gameDir, "luamods");
        boolean isClient = dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT;
        LuaRuntimeReloader.loadOrReload(gameDir, luaDir, isClient, contentService, datapackService, false);
        LuaTweakerCommandRegistry registry = COMMAND_REGISTRY.get();
        if (server != null && registry != null) {
            registry.registerNewLuaCommands(server.getCommands().getDispatcher());
            server.getPlayerList().getPlayers().forEach(player -> server.getCommands().sendCommands(player));
        }
    }

    private static void initPlatform(boolean isClient) {
        Platform.setNetwork(new MinecraftNetworkPlatform());
        Platform.setEntity(new MinecraftEntityPlatform());
        Platform.setInteraction(new MinecraftInteractionPlatform());
        Platform.setContent(new MinecraftContentPlatform());
        Platform.setStorage(new MinecraftStoragePlatform());
        Platform.setDimension(new MinecraftDimensionPlatform());
        Platform.setStructures(new MinecraftStructuresPlatform());
        Platform.setWorldgen(new MinecraftWorldgenPlatform());
        Platform.setDatapack(new MinecraftDatapackPlatform());
        com.luatweaker.common.dimension.MinecraftDimensionProvider.registerCodecs();
        com.luatweaker.common.worldgen.MixedVeinRegistrar.registerFeatures();
        com.luatweaker.common.worldgen.ArchitecturyWorldgenApplier.register();
        if (isClient) {
            Platform.setClient(new com.luatweaker.common.client.MinecraftClientPlatform());
        }

        IntegrationManager.register(new GeckoLibIntegration());
        IntegrationManager.register(new GenericPlatformIntegration("jei", "Just Enough Items (JEI)"));
        IntegrationManager.initPlatform();
    }

    private static void registerContent(ContentServiceImpl contentService,
                                          DatapackServiceImpl datapackService, File gameDir, File luaDir, boolean isClient) {
        java.util.function.Function<String, ResourceLocation> locationParser = LuaTweakerCommon::parseLocation;

        ItemRegistrar itemRegistrar = new ItemRegistrar(contentService, CREATED_ITEMS, locationParser);
        itemRegistrar.registerItems();

        BlockRegistrar blockRegistrar = new BlockRegistrar(contentService, CREATED_BLOCKS, CREATED_ITEMS, locationParser);
        blockRegistrar.registerBlocks();
        blockRegistrar.registerBlockItems();
        blockRegistrar.registerContainerBlockEntities();
        blockRegistrar.registerContainerMenus();

        FluidRegistrar fluidRegistrar = new FluidRegistrar(contentService, CREATED_ITEMS, CREATED_BLOCKS, locationParser);
        fluidRegistrar.registerFluids();

        TabRegistrar tabRegistrar = new TabRegistrar(contentService, CREATED_ITEMS, CREATED_TABS, locationParser);
        tabRegistrar.registerCreativeTabs();

        EntityRegistrar entityRegistrar = new EntityRegistrar(contentService, CREATED_ENTITIES, CREATED_ENTITY_SUPPLIERS, CREATED_ITEMS, locationParser);
        entityRegistrar.registerEntities();

        MobEffectRegistrar effectRegistrar = new MobEffectRegistrar();
        effectRegistrar.registerMobEffects();
        effectRegistrar.registerPotions();

        LuaRuntimeReloader.finalizeDatapackAndStubs(gameDir, luaDir, contentService, datapackService);

        if (isClient) {
            registerClientContent(contentService);
        }
    }


    private static void registerClientContent(ContentServiceImpl contentService) {
        BowItemProperties.register(contentService, CREATED_ITEMS);
        FluidRegistrar.registerClientVisuals();
        ContentRendererRegistrar.register(contentService, CREATED_ENTITY_SUPPLIERS);
        dev.architectury.event.events.client.ClientLifecycleEvent.CLIENT_SETUP.register(client ->
                ContentRendererRegistrar.registerPendingBlockRenderers(contentService));
        com.luatweaker.common.client.MinecraftClientEventListener.init();
        com.luatweaker.common.client.ImGuiInputHook.init();
        com.luatweaker.common.client.DynamicKeyMappingHandler.init();
        com.luatweaker.common.client.ShaderRenderHook.init();
        com.luatweaker.common.client.ClientItemAnimationHandler.init();
        com.luatweaker.common.client.luamods.LuamodMenuButton.register();
        com.luatweaker.common.client.ExperimentalWarningHandler.register();
    }

    private static void registerCommands(File luaDir) {
        LuaTweakerCommandRegistry commandRegistry = new LuaTweakerCommandRegistry(luaDir);
        COMMAND_REGISTRY.set(commandRegistry);
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, selection) ->
                commandRegistry.build(dispatcher));
    }

    private static void registerLifecycle() {
        LifecycleEvent.SERVER_STARTING.register(CommonServerHolder::setServer);
        LifecycleEvent.SERVER_STOPPING.register(server -> CommonServerHolder.setServer(null));
        LifecycleEvent.SERVER_STOPPING.register(server -> IntegrationManager.onServerStopping());
        TickEvent.SERVER_PRE.register(server -> tickActiveEngineTasks());
    }
}
