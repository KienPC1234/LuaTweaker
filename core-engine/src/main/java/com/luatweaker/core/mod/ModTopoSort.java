package com.luatweaker.core.mod;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dependency-order sorting for discovery order (topological, cycle-tolerant:
 * a cycle warns and keeps both mods instead of dead-locking the load).
 */
final class ModTopoSort {
    private ModTopoSort() {}

    static @NotNull List<DiscoveredMod> sortModsByDependencies(@NotNull List<DiscoveredMod> mods) {
        Map<String, DiscoveredMod> modMap = new ConcurrentHashMap<>();
        for (DiscoveredMod found : mods) {
            modMap.put(found.manifest().id(), found);
        }
        List<DiscoveredMod> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (DiscoveredMod found : mods) {
            visitMod(found, modMap, sorted, visited, visiting);
        }
        return sorted;
    }

    private static void visitMod(@NotNull DiscoveredMod mod,
            @NotNull Map<String, DiscoveredMod> modMap,
            @NotNull List<DiscoveredMod> sorted,
            @NotNull Set<String> visited,
            @NotNull Set<String> visiting) {
        String id = mod.manifest().id();
        if (visited.contains(id)) {
            return;
        }
        if (visiting.contains(id)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[LuaModManager] Circular dependency detected involving: " + id);
            return;
        }
        visiting.add(id);
        visitDependencies(mod.manifest().dependencies(), modMap, sorted, visited, visiting);
        visitDependencies(mod.manifest().optionalDependencies(), modMap, sorted, visited, visiting);
        visiting.remove(id);
        visited.add(id);
        sorted.add(mod);
    }

    private static void visitDependencies(@NotNull List<ModDependency> dependencies,
            @NotNull Map<String, DiscoveredMod> modMap,
            @NotNull List<DiscoveredMod> sorted,
            @NotNull Set<String> visited,
            @NotNull Set<String> visiting) {
        for (ModDependency dep : dependencies) {
            DiscoveredMod depMod = modMap.get(dep.id());
            if (depMod != null) {
                visitMod(depMod, modMap, sorted, visited, visiting);
            }
        }
    }
}
