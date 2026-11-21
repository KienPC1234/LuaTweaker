package com.luatweaker.content;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.content.IStorageService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;

public final class ContentLuaBinding {

    private ContentLuaBinding() {
    }

    public static void registerBindings(ILuaEngine engine, IContentService contentService,
            IStorageService storageService, IDatapackService datapackService) {
        ILuaTable env = engine.getGlobalEnvironment();

        ILuaTable startupTable =
                ContentStartupBinding.registerStartupBindings(engine, contentService, datapackService);
        env.rawset("startup", startupTable);
        env.rawset("Content", startupTable);
        engine.registerService("Startup", startupTable);
        engine.registerService("Content", startupTable);

        ILuaTable storageTable = ContentStorageBinding.registerStorageBindings(engine, storageService);
        env.rawset("storage", storageTable);
        env.rawset("Storage", storageTable);
        engine.registerService("Storage", storageService);

        ILuaTable datapackTable = ContentDatapackBinding.registerDatapackBindings(engine, datapackService);
        // Datapack is ALSO the Lua require target ("LuaTweaker.Datapack") and a
        // Java-visible service (consumed by the worldgen/loot/dimension/biomes
        // providers) - the Java side must get the impl, not the Lua table.
        env.rawset("datapack", datapackTable);
        env.rawset("Datapack", datapackTable);
        engine.registerService("Datapack", datapackService);

        ILuaTable tagTable = ContentDatapackBinding.registerTagTable(engine, datapackService);
        env.rawset("tag", tagTable);
    }
}
