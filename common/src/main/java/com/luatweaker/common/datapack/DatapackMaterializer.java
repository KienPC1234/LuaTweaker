package com.luatweaker.common.datapack;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.common.dimension.MinecraftDimensionProvider;
import com.luatweaker.common.loot.MinecraftLootProvider;
import com.luatweaker.common.structures.MinecraftStructureProvider;
import com.luatweaker.common.util.CallbackFirewall;
import com.luatweaker.common.worldgen.MinecraftWorldgenProvider;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.loot.LootServiceImpl;
import com.luatweaker.structures.StructureServiceImpl;
import com.luatweaker.worldgen.WorldgenServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Materializes all Lua-registered datapack domains (dimensions, worldgen, loot,
 * structures, and loader-registered asset hooks) into the in-memory
 * {@link IDatapackService}.
 */
public final class DatapackMaterializer {

    /**
     * Intentional static registry for loader-specific virtual file hooks (e.g. NeoForge fluid bucket models)
     * so reloads re-apply them without coupling {@code common} to a loader module.
     */
    private static final List<BiConsumer<IDatapackService, IContentService>> REBUILD_HOOKS =
            new CopyOnWriteArrayList<>();

    private DatapackMaterializer() {
    }

    public static void addRebuildHook(@NotNull BiConsumer<IDatapackService, IContentService> hook) {
        REBUILD_HOOKS.add(hook);
    }

    public static void applyAll(@NotNull File luaDir,
                                @NotNull IContentService contentService,
                                @NotNull IDatapackService datapackService) {
        CallbackFirewall.run(() -> MinecraftDimensionProvider.applyAll(datapackService),
                LogStage.SYSTEM, () -> "[DatapackMaterializer] Dimension materialization failed");
        applyWorldgen(datapackService);
        applyLoot(datapackService);
        applyStructures(luaDir, datapackService);
        for (BiConsumer<IDatapackService, IContentService> hook : REBUILD_HOOKS) {
            CallbackFirewall.run(() -> hook.accept(datapackService, contentService),
                    LogStage.SYSTEM, () -> "[DatapackMaterializer] Loader rebuild hook failed");
        }
    }

    private static void applyWorldgen(@NotNull IDatapackService datapackService) {
        Object raw = LuaServiceRegistry.get("WorldgenServiceImpl");
        if (raw instanceof WorldgenServiceImpl worldgenService) {
            CallbackFirewall.run(() -> new MinecraftWorldgenProvider(worldgenService, datapackService).applyAll(),
                    LogStage.SYSTEM, () -> "[DatapackMaterializer] Worldgen materialization failed");
        }
    }

    private static void applyLoot(@NotNull IDatapackService datapackService) {
        Object raw = LuaServiceRegistry.get("LootServiceImpl");
        if (raw instanceof LootServiceImpl lootService) {
            CallbackFirewall.run(() -> new MinecraftLootProvider(lootService, datapackService).applyAll(),
                    LogStage.SYSTEM, () -> "[DatapackMaterializer] Loot materialization failed");
        }
    }

    private static void applyStructures(@NotNull File luaDir, @NotNull IDatapackService datapackService) {
        Object raw = LuaServiceRegistry.get("StructureServiceImpl");
        if (raw instanceof StructureServiceImpl structureService) {
            List<File> dataRoots = collectDataRoots(luaDir);
            CallbackFirewall.run(
                    () -> new MinecraftStructureProvider(structureService, datapackService, dataRoots).applyAll(),
                    LogStage.SYSTEM, () -> "[DatapackMaterializer] Structure materialization failed");
        }
    }

    @NotNull
    public static List<File> collectDataRoots(@NotNull File luaDir) {
        List<File> roots = new ArrayList<>();
        File[] children = luaDir.listFiles(File::isDirectory);
        if (children != null) {
            for (File modDir : children) {
                File modData = modDir.toPath().resolve("data").toFile();
                if (modData.isDirectory()) {
                    roots.add(modData);
                }
            }
        }
        File sharedData = new File(luaDir, "data");
        if (sharedData.isDirectory()) {
            roots.add(sharedData);
        }
        return roots;
    }
}
