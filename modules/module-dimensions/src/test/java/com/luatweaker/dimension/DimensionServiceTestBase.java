package com.luatweaker.dimension;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.IRecipe;
import com.luatweaker.api.pal.IPlatformContent;
import com.luatweaker.api.pal.IPlatformDimension;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared engine/service fixture for {@link DimensionServiceImpl} tests.
 * Every test class gets a fresh engine and service; the platform content stub is
 * installed once per class so block-id validation works without Minecraft.
 */
class DimensionServiceTestBase {

    protected ILuaEngine engine;
    protected DimensionServiceImpl service;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeAll
    static void installContentPlatform() {
        Platform.setContent(new IPlatformContent() {
            @Override public IItem createItem(String itemId, int count) { return null; }
            @Override public boolean itemExists(String itemId) { return false; }
            @Override public boolean blockExists(String blockId) { return false; }
            @Override public boolean fluidExists(String fluidId) { return false; }
            @Override public boolean tagExists(String tagId) { return false; }
            @Override public boolean isModLoaded(String modId) { return false; }
            @Override public boolean isClient() { return false; }
            @Override public boolean isDedicatedServer() { return false; }
            @Override public String getPlatformName() { return "test"; }
            @Override public Set<String> getSupportedMobParents() { return Set.of(); }
            @Override public List<IRecipe> getAllRecipes() { return List.of(); }
        });
    }

    @BeforeEach
    void createService() {
        LuaServiceRegistry.clear();
        engine = LuaEngineFactory.create(false);
        service = new DimensionServiceImpl(engine);
    }

    /**
     * Recording platform dimension: records the teleport target and whether a teleport
     * was attempted, and reports the configured result.
     */
    protected static IPlatformDimension dimensionPlatform(boolean teleportResult,
                                                           AtomicReference<String> teleportedTo,
                                                           AtomicBoolean teleportCalled,
                                                           AtomicReference<Object> rawSeen) {
        return new IPlatformDimension() {
            @Override public boolean teleportToDimension(Object rawPlayerEntity, String dimensionId) {
                if (rawSeen != null) {
                    rawSeen.set(rawPlayerEntity);
                }
                if (teleportedTo != null) {
                    teleportedTo.set(dimensionId);
                }
                if (teleportCalled != null) {
                    teleportCalled.set(true);
                }
                return teleportResult;
            }
            @Override public Object getLevel(String dimensionId) { return null; }
            @Override public String getEntityDimensionId(Object rawEntity) { return null; }
        };
    }

    protected static IEntity playerWithRaw(Object raw) {
        return new IEntity() {
            @Override public String getType() { return "minecraft:player"; }
            @Override public String getName() { return "P"; }
            @Override public float getHealth() { return 20; }
            @Override public void setHealth(float health) { }
            @Override public float getMaxHealth() { return 20; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() { }
            @Override public Object getRawEntity() { return raw; }
        };
    }
}
