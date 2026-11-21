package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;

/** Shared real-VM fixture for {@link DimensionLuaBinding} tests. */
class DimensionLuaTestBase {

    protected static final String REALM = "arcane:crystal_realm";
    protected static final String PORTAL = "arcane:crystal_portal";
    protected static final String STONE = "minecraft:stone";
    protected static final String GRASS = "minecraft:grass_block";

    protected ILuaEngine engine;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void registerDimensionBindings() {
        LuaServiceRegistry.clear();
        engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);
    }

    protected DimensionServiceImpl dimensionService() {
        return (DimensionServiceImpl) LuaServiceRegistry.get("DimensionServiceImpl");
    }
}
