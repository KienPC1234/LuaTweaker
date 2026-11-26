package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies shared prototype and metatable reuse across entity and player Lua tables:
 * shared metatables, isolated per-instance receivers, direct properties, and UUID equality.
 */
class EntitySharedPrototypeTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    void entitiesShareMetatableAndKeepReceiversIsolated() {
        FakeEntity first = new FakeEntity("uuid-1", 10.0f);
        FakeEntity second = new FakeEntity("uuid-2", 25.0f);
        FakeEntity sameUuid = new FakeEntity("uuid-1", 10.0f);

        engine.getGlobalEnvironment().rawset("entA", EntitiesLuaBinding.createEntityLuaTable(engine, first));
        engine.getGlobalEnvironment().rawset("entB", EntitiesLuaBinding.createEntityLuaTable(engine, second));
        engine.getGlobalEnvironment().rawset("entA2", EntitiesLuaBinding.createEntityLuaTable(engine, sameUuid));

        engine.executeStringOrThrow("""
                sameMeta = (getmetatable(entA) == getmetatable(entB))
                equalByUuid = (entA == entA2)
                differentByUuid = not (entA == entB)
                beforeA = entA:getHealth()
                beforeB = entB.Health
                entA.Health = 40
                entA:setPos(5, 6, 7)
                posA = entA:getPos()
                """, "shared_entity_proto_test");

        assertAll("entities must share metatable and keep state isolated",
                () -> assertTrue(engine.getGlobalEnvironment().rawget("sameMeta").asBoolean(),
                        "entities in the same engine must share one metatable"),
                () -> assertTrue(engine.getGlobalEnvironment().rawget("equalByUuid").asBoolean(),
                        "__eq must match tables wrapping the same UUID"),
                () -> assertTrue(engine.getGlobalEnvironment().rawget("differentByUuid").asBoolean(),
                        "__eq must reject entities with different UUIDs"),
                () -> assertEquals(10.0, engine.getGlobalEnvironment().rawget("beforeA").asDouble(), 1e-6,
                        "entA must read its own initial health"),
                () -> assertEquals(25.0, engine.getGlobalEnvironment().rawget("beforeB").asDouble(), 1e-6,
                        "entB must read its own initial health"),
                () -> assertEquals(40.0f, first.health, 1e-6f,
                        "entA.Health write must update only the first entity"),
                () -> assertEquals(25.0f, second.health, 1e-6f,
                        "entB must remain untouched when entA is mutated"),
                () -> assertEquals(5.0, first.x, 1e-6,
                        "entA:setPos must update the first entity's X coordinate"),
                () -> assertEquals(0.0, second.x, 1e-6,
                        "entB X coordinate must remain unchanged"));
    }

    @Test
    void playersShareMetatableAndSupportMethodsAndAliases() {
        FakePlayer alice = new FakePlayer("p-1", "Alice", 12.0f);
        FakePlayer bob = new FakePlayer("p-2", "Bob", 20.0f);

        engine.getGlobalEnvironment().rawset("playerA", EntitiesLuaBinding.createPlayerLuaTable(engine, alice));
        engine.getGlobalEnvironment().rawset("playerB", EntitiesLuaBinding.createPlayerLuaTable(engine, bob));

        engine.executeStringOrThrow("""
                samePlayerMeta = (getmetatable(playerA) == getmetatable(playerB))
                aliceName = playerA:GetName()
                bobName = playerB.Name
                playerA:actionText('Hi Alice')
                playerB.Level = 15
                canFlyType = type(playerA.CanFly)
                """, "shared_player_proto_test");

        assertAll("players must share metatable and expose both methods and properties",
                () -> assertTrue(engine.getGlobalEnvironment().rawget("samePlayerMeta").asBoolean(),
                        "players in the same engine must share one metatable"),
                () -> assertEquals("Alice", engine.getGlobalEnvironment().rawget("aliceName").asString(),
                        "playerA:GetName must return Alice"),
                () -> assertEquals("Bob", engine.getGlobalEnvironment().rawget("bobName").asString(),
                        "playerB.Name must return Bob"),
                () -> assertEquals(List.of("Hi Alice"), alice.actionBars,
                        "actionText alias must forward to Alice's sendActionBar"),
                () -> assertEquals(15, bob.level,
                        "playerB.Level write must update Bob's level"),
                () -> assertEquals("function", engine.getGlobalEnvironment().rawget("canFlyType").asString(),
                        "prototype methods such as CanFly must keep method precedence"));
    }

    private static final class FakeEntity implements IEntity {
        private final String uuid;
        float health;
        double x;
        double y;
        double z;

        FakeEntity(String uuid, float health) {
            this.uuid = uuid;
            this.health = health;
        }

        @Override public String getType() { return "test:entity"; }
        @Override public String getName() { return "TestEntity"; }
        @Override public String getUuid() { return uuid; }
        @Override public float getHealth() { return health; }
        @Override public void setHealth(float health) { this.health = health; }
        @Override public float getMaxHealth() { return 50.0f; }
        @Override public boolean isAlive() { return health > 0; }
        @Override public void remove() { }
        @Override public Object getRawEntity() { return this; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getZ() { return z; }

        @Override
        public void teleport(double nx, double ny, double nz) {
            this.x = nx;
            this.y = ny;
            this.z = nz;
        }
    }

    private static final class FakePlayer implements IPlayer {
        private final String uuid;
        private final String name;
        private float health;
        int level;
        final List<String> actionBars = new ArrayList<>();

        FakePlayer(String uuid, String name, float health) {
            this.uuid = uuid;
            this.name = name;
            this.health = health;
        }

        @Override public void sendMessage(String message) { }
        @Override public void sendActionBar(String message) { actionBars.add(message); }
        @Override public String getName() { return name; }
        @Override public String getUuid() { return uuid; }
        @Override public boolean isSneaking() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public void giveItem(String itemId, int count) { }
        @Override public void giveExperience(int exp) { }
        @Override public void addEffect(String effectId, int durationTicks, int amplifier) { }
        @Override public void playSound(String soundId, float volume, float pitch) { }
        @Override public String getType() { return "minecraft:player"; }
        @Override public float getHealth() { return health; }
        @Override public void setHealth(float health) { this.health = health; }
        @Override public float getMaxHealth() { return 20.0f; }
        @Override public boolean isAlive() { return health > 0; }
        @Override public void remove() { }
        @Override public Object getRawEntity() { return this; }
        @Override public int getLevel() { return level; }
        @Override public void setLevel(int level) { this.level = level; }
    }
}
