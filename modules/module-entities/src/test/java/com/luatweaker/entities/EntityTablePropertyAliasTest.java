package com.luatweaker.entities;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaTable;
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
        final java.util.Map<String, Object> customData = new java.util.HashMap<>();

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
        @Override public double getLookVectorX() { return 0.0; }
        @Override public double getLookVectorY() { return 0.0; }
        @Override public double getLookVectorZ() { return 1.0; }
        @Override public boolean isAlive() { return health > 0; }
        @Override public void remove() {}
        @Override public Object getRawEntity() { return this; }
        @Override public Object getCustomData(String key) { return customData.get(key); }
        @Override public void setCustomData(String key, Object value) { customData.put(key, value); }

        String lastAttributeId;
        String lastModifierId;
        String lastOperation;
        double lastAmount;
        double baseValue = 20;

        @Override public Double getAttributeValue(String attributeId) { return baseValue; }
        @Override public Double getAttributeBaseValue(String attributeId) { return baseValue; }
        @Override public boolean setAttributeBaseValue(String attributeId, double value) {
            baseValue = value;
            return true;
        }
        @Override public boolean addAttributeModifier(String attributeId, String modifierId,
                                                      double amount, String operation) {
            lastAttributeId = attributeId;
            lastModifierId = modifierId;
            lastAmount = amount;
            lastOperation = operation;
            return true;
        }
        @Override public boolean removeAttributeModifier(String attributeId, String modifierId) { return true; }
    }

    @Test
    public void attributeApiUsesLuaDefaultsAndCamelCaseAliases() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockEntity entity = new MockEntity();
        engine.getGlobalEnvironment().rawset("mob", EntitiesLuaBinding.createEntityLuaTable(engine, entity));

        engine.executeStringOrThrow(
                "assert(mob:addAttributeModifier('minecraft:generic.armor', 'ruby_bonus', 4) == true)\n" +
                "assert(mob:AddAttributeModifier('minecraft:generic.armor', 'ruby_bonus', 4) == true)",
                "attribute_api_default");
        assertEquals("ADD_VALUE", entity.lastOperation,
                "omitting the operation must default to ADD_VALUE");
        assertEquals("ruby_bonus", entity.lastModifierId);
        assertEquals(4.0, entity.lastAmount, 1e-9);

        engine.executeStringOrThrow(
                "assert(mob:addAttributeModifier('minecraft:generic.armor', 'ruby_mul', 0.5, 'ADD_MULTIPLIED_BASE') == true)\n" +
                "assert(mob:getAttributeValue('minecraft:generic.armor') == 20)\n" +
                "assert(mob:setAttributeBaseValue('minecraft:generic.armor', 30) == true)\n" +
                "assert(mob:getAttributeBaseValue('minecraft:generic.armor') == 30)",
                "attribute_api_explicit");
        assertEquals("ADD_MULTIPLIED_BASE", entity.lastOperation);
        assertEquals("ruby_mul", entity.lastModifierId);
        assertEquals(0.5, entity.lastAmount, 1e-9);
    }

    @Test
    public void propertyAliasesMirrorMethods() throws IOException {
        ILuaEngine engine = LuaEngineFactory.create(false);
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
            "assert(boss.CustomName == '[Ruby Overseer Boss]', 'CustomName alias failed')\n" +
            "assert(boss.LookVector.Z == 1.0, 'LookVector.Z must be 1.0')\n" +
            "boss:setCustomData('mana', 250)\n" +
            "assert(boss:getCustomData('mana') == 250, 'getCustomData must return 250')\n"
        );
        engine.executeScript(file, "TEST");

        assertEquals(300.0f, entity.health);
        assertEquals(300.0f, entity.maxHealth);
        assertEquals("[Ruby Overseer Boss]", entity.customName);
        assertEquals(1.2, entity.my, 0.001);
    }

    @Test
    public void methodStyleStillWorksOnSameTable() throws IOException {
        ILuaEngine engine = LuaEngineFactory.create(false);
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
