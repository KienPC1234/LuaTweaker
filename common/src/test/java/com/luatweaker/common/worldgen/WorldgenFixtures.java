package com.luatweaker.common.worldgen;

import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.worldgen.WorldgenServiceImpl;

import java.util.Map;

/** Shared apply + JSON-validity helpers for the worldgen provider tests. */
final class WorldgenFixtures {

    private WorldgenFixtures() {
    }

    static Map<String, String> apply(WorldgenServiceImpl service) {
        DatapackServiceImpl datapack = new DatapackServiceImpl();
        new MinecraftWorldgenProvider(service, datapack).applyAll();
        return datapack.getVirtualFiles();
    }
}
