package com.luatweaker.common.bootstrap;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.client.ClientLuaBinding;
import com.luatweaker.client.ClientServiceImpl;
import com.luatweaker.content.ContentLuaBinding;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.content.StorageServiceImpl;
import com.luatweaker.entities.AIGoalLuaBinding;
import com.luatweaker.events.EventLuaBinding;
import com.luatweaker.interaction.InteractionLuaBinding;
import com.luatweaker.interaction.ParticleLuaBinding;
import com.luatweaker.interaction.ParticleServiceImpl;
import com.luatweaker.interception.InterceptionLuaBinding;
import com.luatweaker.interception.InterceptionServiceImpl;
import com.luatweaker.math.MathLuaBinding;
import com.luatweaker.network.NetworkLuaBinding;
import com.luatweaker.network.NetworkServiceImpl;
import com.luatweaker.common.recipe.CommonRecipeManager;
import com.luatweaker.recipes.RecipesLuaBinding;
import com.luatweaker.storage.StorageLuaBinding;
import com.luatweaker.tasks.TaskLuaBinding;
import com.luatweaker.tasks.TaskServiceImpl;
import org.jetbrains.annotations.NotNull;

public final class LuaServiceBootstrap {

    private LuaServiceBootstrap() {}

    /**
     * Server-side control surfaces: status/kick primitives plus the LuaMod
     * sync handshake (required mods, client reports, enforcement mode).
     */
    private static void registerServerControlServices(@NotNull ILuaEngine engine) {
        com.luatweaker.core.bind.LuaBinder.bind(engine, "Server",
                new com.luatweaker.common.server.ServerServiceImpl(),
                com.luatweaker.api.server.IServerService.class, "ServerService");
        com.luatweaker.core.bind.LuaBinder.bind(engine, "ModSync",
                new com.luatweaker.common.modsync.ModSyncServiceImpl(),
                com.luatweaker.api.modsync.IModSyncService.class, "ModSyncService");
    }

    /**
     * Registers all standard services into the provided Lua engine.
     * This ensures that all modules (Tasks, Interception, Storage, Client, Network, etc.)
     * are uniformly accessible via Mod:GetService(...) in both Server and Client script phases.
     */
    public static void registerAllServices(
            @NotNull ILuaEngine engine,
            @NotNull ContentServiceImpl contentService,
            @NotNull StorageServiceImpl storageService,
            @NotNull DatapackServiceImpl datapackService,
            @NotNull CommonRecipeManager recipeManager
    ) {
        // 1. Content & Datapack Services
        ContentLuaBinding.registerBindings(engine, contentService, storageService, datapackService);

        // 2. Entities & AI Goals
        com.luatweaker.entities.EntitiesLuaBinding.registerBindings(engine);
        AIGoalLuaBinding.registerBindings(engine);

        // 3. Interaction & Workspace
        InteractionLuaBinding.registerBindings(engine);

        // 3a. Low-level particle primitives (shapes + Bezier paths)
        ParticleLuaBinding.registerBindings(engine, new ParticleServiceImpl());

        // 4. Events System
        EventLuaBinding.registerBindings(engine);

        // 5. Roblox Spatial Math & Utilities
        MathLuaBinding.registerBindings(engine);

        // 5a. Minecraft text components (literal/translate/keybind/score + styles)
        com.luatweaker.text.TextLuaBinding.registerBindings(
                engine, new com.luatweaker.text.TextServiceImpl());

        // 6. Roblox BSON Storage (World, Player, Session)
        StorageLuaBinding.registerBindings(engine, new com.luatweaker.storage.StorageServiceImpl(engine));

        // 7. Network Service (Rocket RemoteEvents & RemoteFunctions)
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);
        com.luatweaker.common.network.CommonNetworkHandler.setServerNetworkService(networkService);
        NetworkLuaBinding.registerBindings(engine, networkService);

        // 8. Task Service (Roblox task.spawn, task.delay, task.wait)
        TaskServiceImpl taskService = new TaskServiceImpl();
        TaskLuaBinding.registerBindings(engine, taskService);

        // 9. Recipe Interception Service (Anvil, Brewing, Villager Trade)
        // Publish the active recipe manager so the client-side compatibility constructor
        // (LuaTweakerMod.onClientSetup) resolves the same delegate as this engine.
        com.luatweaker.core.service.LuaServiceRegistry.register(
            InterceptionServiceImpl.RECIPE_SERVICE_KEY, recipeManager);
        InterceptionServiceImpl interceptionService = new InterceptionServiceImpl(recipeManager);
        InterceptionLuaBinding.registerBindings(engine, interceptionService);

        // 9a. Loot Service (Mob Drops, Chest Loot, Block Drops, Fishing)
        com.luatweaker.loot.LootServiceImpl lootService = new com.luatweaker.loot.LootServiceImpl();
        com.luatweaker.loot.LootLuaBinding.registerBindings(engine, lootService);
        engine.registerService("LootServiceImpl", lootService);

        // 9b. Worldgen Service (Ores, Vegetation, Biome Features)
        com.luatweaker.worldgen.WorldgenServiceImpl worldgenService = new com.luatweaker.worldgen.WorldgenServiceImpl();
        com.luatweaker.worldgen.WorldgenLuaBinding.registerBindings(engine, worldgenService);
        engine.registerService("WorldgenServiceImpl", worldgenService);

        // 9b1. Noise Service (fBm, ridged, domain warp, voronoi, simplex for terrain generation)
        com.luatweaker.noise.NoiseLuaBinding.registerBindings(engine);

        // 9b2. Dimension Service (custom dimensions with Lua terrain generation)
        com.luatweaker.dimension.DimensionLuaBinding.registerBindings(engine);

        // 9b3. Biome customization (entity spawn entries inside biomes)
        com.luatweaker.dimension.BiomesLuaBinding.registerBindings(engine);

        // 9b4. Full-control spawn handlers (the Lua code decides every spawn)
        com.luatweaker.spawn.SpawnRuleLuaBinding.registerBindings(engine);

        // 9b5. Structures Service (single-template NBT structures, validated registry)
        com.luatweaker.structures.StructureServiceImpl structureService = new com.luatweaker.structures.StructureServiceImpl();
        com.luatweaker.structures.StructureLuaBinding.registerBindings(engine, structureService);
        engine.registerService("StructureServiceImpl", structureService);

        // 9b. Server-side command registration (Commands:Register for /lt sub-commands)
        com.luatweaker.command.CommandLuaBinding.registerBindings(engine);

        // 9b1. Server status/control primitives (players, TPS/MSPT, broadcast, kick)
        // plus mod-sync primitives (required mods, client reports, enforcement).
        registerServerControlServices(engine);

        // 9c. Update Checker (declarative, default for every mod) + Web Service
        // (HTTP GET locked behind the 'net.http' manifest permission).
        com.luatweaker.update.UpdateServiceImpl updateService = new com.luatweaker.update.UpdateServiceImpl(engine);
        com.luatweaker.update.WebServiceImpl webService = new com.luatweaker.update.WebServiceImpl(engine);
        com.luatweaker.update.UpdateLuaBinding.registerBindings(engine, updateService, webService);

        // 10. Client Visual & Audio Effects (Camera, Particle, Sound, Screen Flash, KeyBinds, GUI)
        ClientServiceImpl clientService = new ClientServiceImpl();
        com.luatweaker.client.KeyBindServiceImpl keyBindService = new com.luatweaker.client.KeyBindServiceImpl();
        
        boolean isDedicatedServer = com.luatweaker.api.pal.Platform.isInitialized() && com.luatweaker.api.pal.Platform.getContent().isDedicatedServer();
        // One GuiService surface everywhere. The client implementation references
        // client-only Minecraft classes, so a dedicated server gets the Minecraft-free
        // headless implementation instead of loading it.
        com.luatweaker.api.client.IGuiService guiService = isDedicatedServer
                ? new com.luatweaker.client.HeadlessGuiService()
                : new com.luatweaker.common.client.MinecraftGuiService();

        ClientLuaBinding.registerBindings(engine, clientService, keyBindService, guiService);
        engine.registerService("KeyBindService", keyBindService);

        // 10c. Screen-space shader effects (named camera/overlay effects + custom
        // GLSL post passes). The disk cache lives next to the storage directory:
        // game-scoped on clients, world-scoped on dedicated servers.
        com.luatweaker.client.ShaderServiceImpl shaderService =
                new com.luatweaker.client.ShaderServiceImpl(datapackService,
                        new java.io.File(com.luatweaker.api.pal.Platform.getStorage()
                                .getStorageDirectory().getParentFile(), "shader-cache"));
        com.luatweaker.client.ShaderLuaBinding.registerBindings(engine, shaderService);

        // 10d. Dear ImGui for advanced Lua-driven UI (config screens, machine
        // panels, debug overlays). Same one-surface-everywhere rule as
        // GuiService: headless on a dedicated server, live on a client.
        com.luatweaker.client.ImGuiLuaBinding.registerBindings(engine, new com.luatweaker.client.ImGuiServiceImpl());

        // 10b. World-space render service + Client.OnRenderWorld signal (all engines,
        // so client scripts loaded on the runtime engine can require it).
        ILuaValue clientVal = engine.getGlobalEnvironment().rawget("Client");
        if (clientVal != null && clientVal.isTable() && !isDedicatedServer) {
            try {
                Class<?> clazz = com.luatweaker.common.client.MinecraftWorldRenderEventListener.class;
                clazz.getMethod("registerRenderService", ILuaEngine.class, ILuaTable.class).invoke(null, engine, clientVal.asTable());
            } catch (ReflectiveOperationException | LinkageError e) {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                        "MinecraftWorldRenderEventListener not available: " + e.getMessage());
            }
        }

        // 11. Recipe Manager Table
        ILuaTable recipesTable = engine.createTable();
        RecipesLuaBinding.bind(engine, recipesTable, recipeManager);
        engine.registerService("Recipes", recipesTable);

        // 12. Out-of-tree Java plugins (no fork required).
        // Built-ins above always register first so plugin overrides are explicit.
        try {
            com.luatweaker.core.plugin.LuaTweakerPluginLoader.loadPlugins(engine);
        } catch (org.luava.runtime.LuaException | IllegalStateException | java.util.ServiceConfigurationError
                | LinkageError e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.SYSTEM,
                    "[LuaServiceBootstrap] Java plugin loading failed: " + e.getMessage());
        }
    }
}
