package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.pal.IPlatformInteraction;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FindNearestBindingTest {

    private ILuaEngine engine;

    static IEntity mockEntity(String type, String uuid, double x, double y, double z) {
        return new IEntity() {
            @Override public String getType() { return type; }
            @Override public String getName() { return type; }
            @Override public String getUuid() { return uuid; }
            @Override public float getHealth() { return 20.0f; }
            @Override public void setHealth(float health) { }
            @Override public float getMaxHealth() { return 20.0f; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() { }
            @Override public double getX() { return x; }
            @Override public double getY() { return y; }
            @Override public double getZ() { return z; }
            @Override public Object getRawEntity() { return null; }
        };
    }

    static class StubInteraction extends BaseMockPlatformInteraction {
        final List<IEntity> nearby;
        StubInteraction(List<IEntity> nearby) { this.nearby = nearby; }

        @Override public @NotNull List<IEntity> getNearbyEntities(@NotNull IEntity center, double radius) { return nearby; }
    }

    @BeforeEach
    void setup() {
        engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);
    }

    @AfterEach
    void reset() {
        Platform.setInteraction(null);
    }

    private ILuaValue workspace() {
        return engine.getGlobalEnvironment().rawget("Workspace");
    }

    @Test
    void findNearest_ReturnsClosestOfType() {
        IEntity center = mockEntity("luatweaker:wisp", "center", 0, 64, 0);
        IEntity near = mockEntity("minecraft:zombie", "near", 3, 64, 0);
        IEntity far = mockEntity("minecraft:zombie", "far", 30, 64, 0);
        IEntity pig = mockEntity("minecraft:pig", "pig", 1, 64, 0);
        Platform.setInteraction(new StubInteraction(List.of(near, far, pig)));

        ILuaValue find = workspace().asTable().rawget("FindNearest");
        assertTrue(find.isFunction(), "Workspace.FindNearest must be registered");

        ILuaValue result = engine.callFunction(find, workspace(),
                com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, center),
                engine.wrapString("minecraft:zombie"),
                engine.wrapNumber(64));
        assertTrue(result.isTable());
        assertEquals("minecraft:zombie",
                com.luatweaker.entities.EntitiesLuaBinding.getEntityFromTable(result).getType());
        assertEquals("near", com.luatweaker.entities.EntitiesLuaBinding.getEntityFromTable(result).getUuid());
    }

    @Test
    void findNearest_NilWhenNothingMatches() {
        IEntity center = mockEntity("luatweaker:wisp", "center", 0, 64, 0);
        Platform.setInteraction(new StubInteraction(List.of(mockEntity("minecraft:pig", "pig", 1, 64, 0))));

        ILuaValue find = workspace().asTable().rawget("FindNearest");
        ILuaValue result = engine.callFunction(find, workspace(),
                com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, center),
                engine.wrapString("minecraft:zombie"),
                engine.wrapNumber(64));
        assertTrue(result.isNil(), "no zombie nearby must yield nil");
    }
}
