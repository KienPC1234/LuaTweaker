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
import java.util.Arrays;
import java.util.Comparator;

@Mod(LuaTweakerMod.MODID)
public class LuaTweakerMod {
    public static final String MODID = "luatweaker";

    static {
        System.err.println("LuaTweakerMod: static init at " + new java.util.Date());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LuaTweakerMod.class);

    /** The command registry; held as a field so external modules can register commands before build(). */
    private final LuaTweakerCommandRegistry commandRegistry;

    public LuaTweakerMod(IEventBus modEventBus, ModContainer modContainer) {
        System.err.println("LuaTweakerMod: constructor at " + new java.util.Date());
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

        // Build the command registry (core commands auto-registered inside)
        commandRegistry = new LuaTweakerCommandRegistry(getLuaDirectory());

        // Register all NeoForge event listeners (game event bus)
        NeoForge.EVENT_BUS.register(this);
    }

    /** Expose the registry so addon modules can call commandRegistry.register(myCmd) during setup. */
    public LuaTweakerCommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    private void initializeUserDirectories() {
        File luaDir = getLuaDirectory();
        if (!luaDir.exists()) {
            luaDir.mkdirs();
            com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "Created main Lua directory at: " + luaDir.getAbsolutePath()
            );
        }

        String[] requiredSubDirs = new String[] { "startup", "server", "client", "lib", ".luatweaker/stubs", "logs/luatweaker" };
        for (String sub : requiredSubDirs) {
            File subDir = new File(luaDir, sub);
            if (!subDir.exists()) {
                boolean created = subDir.mkdirs();
                if (created || subDir.exists()) {
                    com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "Created missing Lua sub-directory: " + subDir.getAbsolutePath()
                    );
                }
            }
        }
    }

    private File getLuaDirectory() {
        File[] candidates = new File[] {
            new File("lua"),
            new File("../lua"),
            new File("../../lua"),
            new File("../../run/lua"),
            new File("run/lua")
        };

        // 1. Return first candidate that contains .lua scripts
        for (File candidate : candidates) {
            if (hasLuaScripts(candidate)) {
                return candidate;
            }
        }

        // 2. Return first candidate that exists as a directory
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isDirectory()) {
                return candidate;
            }
        }

        // 3. Fallback
        return candidates[0];
    }

    private boolean hasLuaScripts(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return false;
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
                    com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.STUB_GEN, "Generating autocomplete stubs...");
                    LtvmStubGenerator stubGen = new LtvmStubGenerator();
                    stubGen.registerService("Recipes", com.luatweaker.api.recipe.IRecipeManagerService.class);
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
        com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.RELOAD, "Reloading Lua server scripts... (Debug: " + debugMode + ")");

        // Clear pending Anvil/Brewing/Trade from previous reload cycle
        InterceptionHelper.clearPending();

        if (isAutoStubsEnabled()) {
            com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.STUB_GEN, "Generating autocomplete stubs...");
            LtvmStubGenerator stubGen = new LtvmStubGenerator();
            stubGen.registerService("Recipes", com.luatweaker.api.recipe.IRecipeManagerService.class);
            LtvmStubExporter.exportToWorkspace(new File(".").toPath(), stubGen);
        }

        NeoForgeRecipeManager recipeManager = new NeoForgeRecipeManager();
        ILuaEngine engine = new CobaltLuaEngine(debugMode);

        ILuaTable recipesTable = engine.createTable();
        RecipesLuaBinding.bind(recipesTable, recipeManager);
        engine.registerService("Recipes", recipesTable);

        File serverDir = new File(getLuaDirectory(), "server");
        LOGGER.info("Lua server dir: {} (exists: {})", serverDir.getAbsolutePath(), serverDir.exists());

        if (serverDir.exists() && serverDir.isDirectory()) {
            File[] files = serverDir.listFiles((dir, name) -> name.endsWith(".lua"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                LOGGER.info("Found {} Lua scripts to execute", files.length);
                for (File f : files) {
                    com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.SCRIPT_LOAD, "Executing script: " + f.getName());
                    try {
                        engine.executeScript(f, "SERVER");
                    } catch (Exception e) {
                        LOGGER.error("Failed to execute script: " + f.getName(), e);
                    }
                }
            }
        }

        InterceptionHelper.populatePendingEvents(recipeManager.getModifications());

        if (server != null) {
            com.luatweaker.api.log.LuaTweakerLog.get().stageBegin(com.luatweaker.api.log.LogStage.RECIPE_APPLY);
            RecipeManager mcRecipeManager = server.getRecipeManager();
            InterceptionHelper.applyModifications(mcRecipeManager, recipeManager.getModifications());
            com.luatweaker.api.log.LuaTweakerLog.get().stageEnd(com.luatweaker.api.log.LogStage.RECIPE_APPLY, System.currentTimeMillis() - startTime);
        }
        com.luatweaker.api.log.LuaTweakerLog.get().stageEnd(com.luatweaker.api.log.LogStage.RELOAD, System.currentTimeMillis() - startTime);
        LOGGER.info("reloadServerRecipes completed in {}ms", System.currentTimeMillis() - startTime);
    }
}
