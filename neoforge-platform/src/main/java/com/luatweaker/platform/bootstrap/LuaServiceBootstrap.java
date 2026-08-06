package com.luatweaker.platform.bootstrap;

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
import com.luatweaker.entities.WorldActionLuaBinding;
import com.luatweaker.events.EventLuaBinding;
import com.luatweaker.interaction.InteractionLuaBinding;
import com.luatweaker.interception.InterceptionLuaBinding;
import com.luatweaker.interception.InterceptionServiceImpl;
import com.luatweaker.math.MathLuaBinding;
import com.luatweaker.network.NetworkLuaBinding;
import com.luatweaker.network.NetworkServiceImpl;
import com.luatweaker.platform.recipe.NeoForgeRecipeManager;
import com.luatweaker.recipes.RecipesLuaBinding;
import com.luatweaker.storage.StorageLuaBinding;
import com.luatweaker.tasks.TaskLuaBinding;
import com.luatweaker.tasks.TaskServiceImpl;
import org.jetbrains.annotations.NotNull;

public final class LuaServiceBootstrap {

    private LuaServiceBootstrap() {}

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
            @NotNull NeoForgeRecipeManager recipeManager
    ) {
        // 1. Content & Datapack Services
        ContentLuaBinding.registerBindings(engine, contentService, storageService, datapackService);

        // 2. Entities & AI Goals
        com.luatweaker.entities.EntitiesLuaBinding.registerBindings(engine);
        AIGoalLuaBinding.registerBindings(engine);
        WorldActionLuaBinding.registerBindings(engine);

        // 3. Interaction & Workspace
        InteractionLuaBinding.registerBindings(engine);

        // 4. Events System
        EventLuaBinding.registerBindings(engine);

        // 5. Roblox Spatial Math & Utilities
        MathLuaBinding.registerBindings(engine);

        // 6. Roblox BSON Storage (World, Player, Session)
        com.luatweaker.storage.StorageServiceImpl robloxStorage = new com.luatweaker.storage.StorageServiceImpl(engine);
        StorageLuaBinding.registerBindings(engine, robloxStorage);

        // 7. Network Service (Rocket RemoteEvents & RemoteFunctions)
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);
        try {
            com.luatweaker.platform.LuaTweakerMod.setServerNetworkService(networkService);
        } catch (Throwable ignored) {
            // Unit test environment without NeoForge FML
        }
        NetworkLuaBinding.registerBindings(engine, networkService);

        // 8. Task Service (Roblox task.spawn, task.delay, task.wait)
        TaskServiceImpl taskService = new TaskServiceImpl();
        TaskLuaBinding.registerBindings(engine, taskService);

        // 9. Recipe Interception Service (Anvil, Brewing, Villager Trade)
        InterceptionServiceImpl interceptionService = new InterceptionServiceImpl();
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

        // 9b. Server-side command registration (Commands:Register for /lt sub-commands)
        com.luatweaker.command.CommandLuaBinding.registerBindings(engine);

        // 9c. Update Checker (declarative, default for every mod) + Web Service
        // (HTTP GET locked behind the 'net.http' manifest permission).
        com.luatweaker.update.UpdateServiceImpl updateService = new com.luatweaker.update.UpdateServiceImpl(engine);
        com.luatweaker.update.WebServiceImpl webService = new com.luatweaker.update.WebServiceImpl(engine);
        com.luatweaker.update.UpdateLuaBinding.registerBindings(engine, updateService, webService);

        // 10. Client Visual & Audio Effects (Camera, Particle, Sound, Screen Flash, KeyBinds, GUI)
        ClientServiceImpl clientService = new ClientServiceImpl();
        com.luatweaker.client.KeyBindServiceImpl keyBindService = new com.luatweaker.client.KeyBindServiceImpl();
        
        boolean isDedicatedServer = com.luatweaker.api.pal.Platform.isInitialized() && com.luatweaker.api.pal.Platform.getContent().isDedicatedServer();
        com.luatweaker.api.client.IGuiService guiService = null;
        
        if (!isDedicatedServer) {
            try {
                guiService = (com.luatweaker.api.client.IGuiService) Class.forName("com.luatweaker.platform.client.NeoForgeGuiService").getDeclaredConstructor().newInstance();
            } catch (Exception ignored) {}
        }
        
        ClientLuaBinding.registerBindings(engine, clientService, keyBindService, guiService);
        engine.registerService("KeyBindService", keyBindService);

        // 10b. World-space render service + Client.OnRenderWorld signal (all engines,
        // so client scripts loaded on the runtime engine can require it).
        ILuaValue clientVal = engine.getGlobalEnvironment().rawget("Client");
        if (clientVal != null && clientVal.isTable() && !isDedicatedServer) {
            try {
                Class<?> clazz = Class.forName("com.luatweaker.platform.client.NeoForgeWorldRenderEventListener");
                clazz.getMethod("registerRenderService", ILuaEngine.class, ILuaTable.class).invoke(null, engine, clientVal.asTable());
            } catch (Exception ignored) {}
        }

        // 11. Recipe Manager Table
        ILuaTable recipesTable = engine.createTable();
        RecipesLuaBinding.bind(engine, recipesTable, recipeManager);
        engine.registerService("Recipes", recipesTable);
    }
}
