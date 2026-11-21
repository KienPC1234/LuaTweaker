package com.luatweaker.platform.worldgen;

import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.worldgen.WorldgenServiceImpl;

import java.util.Map;

/** Shared apply + JSON-validity helpers for the worldgen provider tests. */
final class WorldgenFixtures {

    private WorldgenFixtures() {
    }

    static Map<String, String> apply(WorldgenServiceImpl service) {
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        new NeoForgeWorldgenProvider(service, datapack).applyAll();
        return datapack.getVirtualFiles();
    }
}
