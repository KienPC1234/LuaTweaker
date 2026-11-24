package com.luatweaker.common.bootstrap;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.mod.IModExports;
import com.luatweaker.api.mod.IModFiles;
import com.luatweaker.common.config.LuaTweakerConfig;
import com.luatweaker.core.lsp.LtvmStubExporter;
import com.luatweaker.core.lsp.LtvmStubGenerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Generates EmmyLua workspace stubs from registered {@code @LuaDoc} interfaces
 * and the {@code StubCatalog} when enabled in {@link LuaTweakerConfig}.
 */
public final class LuaStubSynchronizer {

    private LuaStubSynchronizer() {
    }

    public static void exportIfEnabled(@NotNull File gameDir) {
        if (!LuaTweakerConfig.isAutoGenerateStubs()) {
            return;
        }
        LtvmStubGenerator generator = new LtvmStubGenerator();
        generator.registerClassStub(IEntity.class, "Entity");
        generator.registerClassStub(IPlayer.class, "Player");
        generator.registerClassStub(IModFiles.class, "ModFiles");
        generator.registerClassStub(IModExports.class, "ModExports");
        LtvmStubExporter.exportToWorkspace(gameDir.toPath(), generator);
    }
}
