package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exercises the unified entity Lua table: method-style API and Roblox-style
 * property aliases (entity.Health, entity.MaxHealth, entity.Type, entity.CustomName,
 * entity.Velocity, entity.IsAlive) — the pattern used by ruby_boss.lua.
 */
class EntityTablePropertyAliasTest {

    private static final String BOSS_GLOBAL = "boss";
    private static final String ENTITY_ID = "luatweaker:ruby_boss";
    private static final String BOSS_NAME = "[Ruby Overseer Boss]";
    private static final String ARMOR_ATTRIBUTE = "minecraft:generic.armor";
    private static final String ADD_VALUE = "ADD_VALUE";
    private static final String ADD_MULTIPLIED_BASE = "ADD_MULTIPLIED_BASE";
    private static final String BONUS_MODIFIER = "ruby_bonus";
    private static final String MULTIPLIER_MODIFIER = "ruby_mul";

    @Test
    void addAttributeModifierUsesTheDefaultOperation() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockEntity entity = bossEntity(engine);

        runScript(engine, "attribute_default",
                "assert(boss:addAttributeModifier('" + ARMOR_ATTRIBUTE + "', '" + BONUS_MODIFIER + "', 4) == true)\n"
                + "assert(boss:AddAttributeModifier('" + ARMOR_ATTRIBUTE + "', '" + BONUS_MODIFIER + "', 4) == true)",
                BOSS_GLOBAL);

        Assertions.assertAll("omitting the operation must default to ADD_VALUE",
            () -> Assertions.assertEquals(ADD_VALUE, entity.lastOperation,
                "the omitted operation must default to ADD_VALUE"),
            () -> Assertions.assertEquals(BONUS_MODIFIER, entity.lastModifierId,
                "the modifier id must be forwarded"),
            () -> Assertions.assertEquals(4.0, entity.lastAmount, 1e-9,
                "the modifier amount must be forwarded"));
    }

    @Test
    void addAttributeModifierForwardsAnExplicitOperation() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockEntity entity = bossEntity(engine);

        runScript(engine, "attribute_explicit",
                "assert(boss:addAttributeModifier('" + ARMOR_ATTRIBUTE + "', '" + MULTIPLIER_MODIFIER
                + "', 0.5, '" + ADD_MULTIPLIED_BASE + "') == true)",
                BOSS_GLOBAL);

        Assertions.assertAll("an explicit operation must be forwarded unchanged",
            () -> Assertions.assertEquals(ADD_MULTIPLIED_BASE, entity.lastOperation,
                "the explicit operation must reach the entity"),
            () -> Assertions.assertEquals(MULTIPLIER_MODIFIER, entity.lastModifierId,
                "the explicit modifier id must reach the entity"),
            () -> Assertions.assertEquals(0.5, entity.lastAmount, 1e-9,
                "the explicit amount must reach the entity"));
    }

    @Test
    void attributeValuesCanBeSetAndReadFromLua() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockEntity entity = bossEntity(engine);

        runScript(engine, "attribute_values",
                "assert(boss:getAttributeValue('" + ARMOR_ATTRIBUTE + "') == 20)\n"
                + "assert(boss:setAttributeBaseValue('" + ARMOR_ATTRIBUTE + "', 30) == true)\n"
                + "assert(boss:getAttributeBaseValue('" + ARMOR_ATTRIBUTE + "') == 30)",
                BOSS_GLOBAL);

        Assertions.assertEquals(30.0, entity.baseValue, 1e-9,
                "setAttributeBaseValue must update the entity attribute base value");
    }

    @Test
    void propertyAliasesMirrorTheMethodApi() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockEntity entity = bossEntity(engine);

        runScript(engine, "boss_props",
                "assert(boss.Type == '" + ENTITY_ID + "', 'Type alias failed: ' .. tostring(boss.Type))\n"
                + "assert(type(boss.IsAlive) == 'function' or boss.IsAlive == true, 'IsAlive must resolve')\n"
                + "boss.MaxHealth = 300\n"
                + "boss.Health = 300\n"
                + "boss.CustomName = '" + BOSS_NAME + "'\n"
                + "boss.Velocity = { X = 0, Y = 1.2, Z = 0 }\n"
                + "assert(boss.Health == 300, 'Health alias failed: ' .. tostring(boss.Health))\n"
                + "assert(boss.MaxHealth == 300, 'MaxHealth alias failed: ' .. tostring(boss.MaxHealth))\n"
                + "assert(boss.CustomName == '" + BOSS_NAME + "', 'CustomName alias failed')\n"
                + "assert(boss.LookVector.Z == 1.0, 'LookVector.Z must be 1.0')\n"
                + "boss:setCustomData('mana', 250)\n"
                + "assert(boss:getCustomData('mana') == 250, 'getCustomData must return 250')\n",
                BOSS_GLOBAL);

        Assertions.assertAll("property aliases must write through to the entity",
            () -> Assertions.assertEquals(300.0f, entity.health, "boss.Health must set the health"),
            () -> Assertions.assertEquals(300.0f, entity.maxHealth,
                "boss.MaxHealth must set the maximum health"),
            () -> Assertions.assertEquals(BOSS_NAME, entity.customName,
                "boss.CustomName must set the custom name"),
            () -> Assertions.assertEquals(1.2, entity.my, 0.001,
                "boss.Velocity must set the Y motion"));
    }

    @Test
    void methodStyleStillWorksOnTheSameTable() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        MockEntity entity = bossEntity(engine);

        runScript(engine, "boss_methods",
                "boss:setHealth(50.0)\n"
                + "boss:setMaxHealth(120.0)\n"
                + "boss:setMotion(0, 0.5, 0)\n"
                + "assert(boss:getHealth() == 50, 'getHealth failed')\n"
                + "assert(boss:getMaxHealth() == 120, 'getMaxHealth failed')\n"
                + "assert(boss.Type == '" + ENTITY_ID + "', 'Type must still resolve after method calls')\n",
                BOSS_GLOBAL);

        Assertions.assertAll("method-style calls must mutate the same entity as the aliases",
            () -> Assertions.assertEquals(50.0f, entity.health, "setHealth must update the health"),
            () -> Assertions.assertEquals(120.0f, entity.maxHealth,
                "setMaxHealth must update the maximum health"),
            () -> Assertions.assertEquals(0.5, entity.my, 0.001, "setMotion must update the Y motion"));
    }

    private static MockEntity bossEntity(ILuaEngine engine) {
        MockEntity entity = new MockEntity();
        ILuaTable table = EntitiesLuaBinding.createEntityLuaTable(engine, entity);
        engine.getGlobalEnvironment().rawset(BOSS_GLOBAL, table);
        return entity;
    }

    private static void runScript(ILuaEngine engine, String name, String source, String globalName)
            throws AssertionError {
        try {
            File file = File.createTempFile(name, ".lua");
            file.deleteOnExit();
            Files.writeString(file.toPath(), source);
            engine.executeScript(file, "TEST");
        } catch (IOException error) {
            throw new AssertionError("could not write the Lua fixture for " + globalName, error);
        }
    }

    private static final class MockEntity implements IEntity {
        private float health = 20;
        private float maxHealth = 20;
        private String customName = "";
        private double mx;
        private double my;
        private double mz;
        private final Map<String, Object> customData = new ConcurrentHashMap<>();

        private String lastModifierId;
        private String lastOperation;
        private double lastAmount;
        private double baseValue = 20;

        @Override public String getType() { return ENTITY_ID; }
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
        @Override public void remove() { }
        @Override public Object getRawEntity() { return this; }
        @Override public Object getCustomData(String key) { return customData.get(key); }
        @Override public void setCustomData(String key, Object value) { customData.put(key, value); }
        @Override public Double getAttributeValue(String attributeId) { return baseValue; }
        @Override public Double getAttributeBaseValue(String attributeId) { return baseValue; }

        @Override
        public boolean setAttributeBaseValue(String attributeId, double value) {
            baseValue = value;
            return true;
        }

        @Override
        public boolean addAttributeModifier(String attributeId, String modifierId,
                                           double amount, String operation) {
            lastModifierId = modifierId;
            lastAmount = amount;
            lastOperation = operation;
            return true;
        }

        @Override
        public boolean removeAttributeModifier(String attributeId, String modifierId) { return true; }
    }
}
