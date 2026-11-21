package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.entities.EntitiesLuaBinding;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code Workspace.FindNearest}: type filtering, closest-candidate selection and the nil
 * result when nothing matches.
 */
class FindNearestBindingTest {

    private static final String WISP_TYPE = "luatweaker:wisp";
    private static final String ZOMBIE_TYPE = "minecraft:zombie";
    private static final String PIG_TYPE = "minecraft:pig";
    private static final String CENTER_UUID = "center";
    private static final String NEAR_UUID = "near";
    private static final String FAR_UUID = "far";
    private static final String PIG_UUID = "pig";
    private static final double CENTER_Y = 64.0;
    private static final double SEARCH_RADIUS = 64.0;

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);
    }

    @AfterEach
    void reset() {
        Platform.setInteraction(null);
    }

    private static IEntity wisp() {
        return EntityFixtures.simple(WISP_TYPE, CENTER_UUID, 0, CENTER_Y, 0);
    }

    /** Two zombies plus a nearer pig: type filtering and distance ordering both matter. */
    private static List<IEntity> mixedNeighbours() {
        return List.of(
                EntityFixtures.simple(ZOMBIE_TYPE, NEAR_UUID, 3, CENTER_Y, 0),
                EntityFixtures.simple(ZOMBIE_TYPE, FAR_UUID, 30, CENTER_Y, 0),
                EntityFixtures.simple(PIG_TYPE, PIG_UUID, 1, CENTER_Y, 0));
    }

    private ILuaTable workspaceTable() {
        return engine.getGlobalEnvironment().rawget("Workspace").asTable();
    }

    /** Calls Workspace.FindNearest with the given platform double installed. */
    private ILuaValue findNearest(IEntity center, List<IEntity> nearby, String type) {
        Platform.setInteraction(new StubInteraction(nearby));
        ILuaValue find = workspaceTable().rawget("FindNearest");
        return engine.callFunction(find, workspaceTable(),
                EntitiesLuaBinding.createEntityLuaTable(engine, center),
                engine.wrapString(type),
                engine.wrapNumber(SEARCH_RADIUS));
    }

    @Test
    void workspaceRegistersFindNearestAsFunction() {
        ILuaValue find = workspaceTable().rawget("FindNearest");
        assertTrue(find.isFunction(),
                "Workspace.FindNearest must be registered so scripts can query nearby entities");
    }

    @Test
    void findNearestReturnsTheClosestMatchingType() {
        ILuaValue result = findNearest(wisp(), mixedNeighbours(), ZOMBIE_TYPE);
        assertEquals(ZOMBIE_TYPE, EntitiesLuaBinding.getEntityFromTable(result).getType(),
                "FindNearest must skip nearby entities whose type was not requested");
    }

    @Test
    void findNearestReturnsTheClosestCandidate() {
        ILuaValue result = findNearest(wisp(), mixedNeighbours(), ZOMBIE_TYPE);
        assertEquals(NEAR_UUID, EntitiesLuaBinding.getEntityFromTable(result).getUuid(),
                "the zombie at distance 3 must win over the zombie at distance 30");
    }

    @Test
    void findNearestReturnsNilWhenNothingMatches() {
        ILuaValue result = findNearest(wisp(), List.of(EntityFixtures.simple(PIG_TYPE, PIG_UUID, 1, CENTER_Y, 0)),
                ZOMBIE_TYPE);
        assertTrue(result.isNil(),
                "a search with no matching type must yield nil so scripts can branch on absence");
    }

    /** Platform double that only reports the supplied neighbours. */
    private static final class StubInteraction implements MockPlatformInteraction {
        private final List<IEntity> nearby;

        private StubInteraction(List<IEntity> nearby) {
            this.nearby = nearby;
        }

        @NotNull
        @Override
        public List<IEntity> getNearbyEntities(@NotNull IEntity center, double radius) {
            return nearby;
        }
    }
}
