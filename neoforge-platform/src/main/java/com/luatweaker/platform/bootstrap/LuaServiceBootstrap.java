package com.luatweaker.platform.bootstrap;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
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

        // 10. Client Visual & Audio Effects (Camera, Particle, Sound, Screen Flash, KeyBinds)
        ClientServiceImpl clientService = new ClientServiceImpl();
        com.luatweaker.client.KeyBindServiceImpl keyBindService = new com.luatweaker.client.KeyBindServiceImpl();
        ClientLuaBinding.registerBindings(engine, clientService, keyBindService);
        engine.registerService("KeyBindService", keyBindService);

        // 11. Recipe Manager Table
        ILuaTable recipesTable = engine.createTable();
        RecipesLuaBinding.bind(engine, recipesTable, recipeManager);
        engine.registerService("Recipes", recipesTable);
    }
}
