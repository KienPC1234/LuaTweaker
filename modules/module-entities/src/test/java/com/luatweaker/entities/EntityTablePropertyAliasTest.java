package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the unified entity Lua table: method-style API and Roblox-style
 * property aliases (entity.Health, entity.MaxHealth, entity.Type, entity.CustomName,
 * entity.Velocity, entity.IsAlive) — the pattern used by ruby_boss.lua.
 */
public class EntityTablePropertyAliasTest {

    private static final class MockEntity implements IEntity {
        float health = 20;
        float maxHealth = 20;
        String customName = "";
        double mx, my, mz;

        @Override public String getType() { return "luatweaker:ruby_boss"; }
        @Override public String getName() { return "Boss"; }
        @Override public float getHealth() { return health; }
        @Override public void setHealth(float health) { this.health = health; }
        @Override public float getMaxHealth() { return maxHealth; }
        @Override public void setMaxHealth(float maxHealth) { this.maxHealth = maxHealth; }
        @Override public void setCustomName(String name) { this.customName = name; }
        @Override public String getCustomName() { return customName; }
        @Override public void setMotion(double vx, double vy, double vz) { mx = vx; my = vy; mz = vz; }
        @Override public double getMotionX() { return mx; }
        @Override public double getMotionY() { return my; }
        @Override public double getMotionZ() { return mz; }
        @Override public boolean isAlive() { return health > 0; }
        @Override public void remove() {}
        @Override public Object getRawEntity() { return this; }
    }

    @Test
    public void propertyAliasesMirrorMethods() throws IOException {
        CobaltLuaEngine engine = new CobaltLuaEngine();
        MockEntity entity = new MockEntity();
        ILuaTable table = EntitiesLuaBinding.createEntityLuaTable(engine, entity);
        engine.getGlobalEnvironment().rawset("boss", table);

        // Mimic ruby_boss.lua property usage
        File file = File.createTempFile("boss_props", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(),
            "assert(boss.Type == 'luatweaker:ruby_boss', 'Type alias failed: ' .. tostring(boss.Type))\n" +
            "assert(type(boss.IsAlive) == 'function' or boss.IsAlive == true, 'IsAlive must resolve')\n" +
            "boss.MaxHealth = 300\n" +
            "boss.Health = 300\n" +
            "boss.CustomName = '[Ruby Overseer Boss]'\n" +
            "boss.Velocity = { X = 0, Y = 1.2, Z = 0 }\n" +
            "assert(boss.Health == 300, 'Health alias failed: ' .. tostring(boss.Health))\n" +
            "assert(boss.MaxHealth == 300, 'MaxHealth alias failed: ' .. tostring(boss.MaxHealth))\n" +
            "assert(boss.CustomName == '[Ruby Overseer Boss]', 'CustomName alias failed')\n"
        );
        engine.executeScript(file, "TEST");

        assertEquals(300.0f, entity.health);
        assertEquals(300.0f, entity.maxHealth);
        assertEquals("[Ruby Overseer Boss]", entity.customName);
        assertEquals(1.2, entity.my, 0.001);
    }

    @Test
    public void methodStyleStillWorksOnSameTable() throws IOException {
        CobaltLuaEngine engine = new CobaltLuaEngine();
        MockEntity entity = new MockEntity();
        ILuaTable table = EntitiesLuaBinding.createEntityLuaTable(engine, entity);
        engine.getGlobalEnvironment().rawset("boss", table);

        File file = File.createTempFile("boss_methods", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(),
            "boss:setHealth(50.0)\n" +
            "boss:setMaxHealth(120.0)\n" +
            "boss:setMotion(0, 0.5, 0)\n" +
            "assert(boss:getHealth() == 50, 'getHealth failed')\n" +
            "assert(boss:getMaxHealth() == 120, 'getMaxHealth failed')\n" +
            "assert(boss.Type == 'luatweaker:ruby_boss', 'Type must still resolve after method calls')\n"
        );
        engine.executeScript(file, "TEST");

        assertEquals(50.0f, entity.health);
        assertEquals(120.0f, entity.maxHealth);
        assertEquals(0.5, entity.my, 0.001);
    }
}
