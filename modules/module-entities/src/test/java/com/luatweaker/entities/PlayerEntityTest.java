package com.luatweaker.entities;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;

import com.luatweaker.api.entity.IPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerEntityTest {

    private ILuaEngine engine;
    private MockPlayer mockPlayer;

    private static class MockPlayer implements IPlayer {
        final List<String> messages = new ArrayList<>();
        final List<String> actionBars = new ArrayList<>();
        final List<String> projectiles = new ArrayList<>();
        final List<String> screens = new ArrayList<>();
        final java.util.Map<String, Object> syncedData = new java.util.HashMap<>();
        float health = 20.0f;
        boolean sneaking = false;

        @Override public void sendMessage(String message) { messages.add(message); }
        @Override public void sendActionBar(String message) { actionBars.add(message); }
        @Override public String getName() { return "Steve"; }
        @Override public String getUuid() { return "00000000-0000-0000-0000-000000000000"; }
        @Override public boolean isSneaking() { return sneaking; }
        @Override public boolean isCreative() { return true; }
        @Override public void giveItem(String itemId, int count) {}
        @Override public void giveExperience(int exp) {}
        @Override public void addEffect(String effectId, int durationTicks, int amplifier) {}
        @Override public void playSound(String soundId, float volume, float pitch) {}
        @Override public String getType() { return "minecraft:player"; }
        @Override public float getHealth() { return health; }
        @Override public void setHealth(float health) { this.health = health; }
        @Override public float getMaxHealth() { return 20.0f; }
        @Override public boolean isAlive() { return health > 0; }
        @Override public void remove() {}
        @Override public Object getRawEntity() { return this; }
        @Override public void shootProjectile(String projectileType, double speed, double inaccuracy) {
            projectiles.add(projectileType + "@" + speed);
        }
        @Override public void syncCustomData(String key, Object value) {
            Object unwrapped = value;
            if (value instanceof com.luatweaker.api.vm.ILuaValue lv) {
                if (lv.isNumber()) unwrapped = lv.asDouble();
                else if (lv.isString()) unwrapped = lv.asString();
                else if (lv.isBoolean()) unwrapped = lv.asBoolean();
            }
            syncedData.put(key, unwrapped);
        }
        @Override public void openScreen(String screenId, String title) { screens.add("OPEN:" + screenId + ":" + title); }
        @Override public void closeScreen() { screens.add("CLOSE"); }
    }

    @BeforeEach
    public void setUp() {
        engine = LuaEngineFactory.create(false);
        mockPlayer = new MockPlayer();
    }

    @Test
    public void testPlayerSendMessageFromLua() throws IOException {
        var playerTable = EntitiesLuaBinding.createPlayerLuaTable(engine, mockPlayer);
        engine.getGlobalEnvironment().rawset("player", playerTable);

        String script = "player:sendMessage('Hello Minecraft!')";
        File file = File.createTempFile("test_player", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), script);

        engine.executeScript(file, "TEST");

        assertEquals(1, mockPlayer.messages.size());
        assertEquals("Hello Minecraft!", mockPlayer.messages.get(0));
    }

    @Test
    public void testShootProjectileDispatchesToImpl() throws IOException {
        var playerTable = EntitiesLuaBinding.createPlayerLuaTable(engine, mockPlayer);
        engine.getGlobalEnvironment().rawset("player", playerTable);

        File file = File.createTempFile("test_shoot", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "player:shootProjectile('luatweaker:ruby_orb', 1.8)");
        engine.executeScript(file, "TEST");

        assertTrue(mockPlayer.projectiles.contains("luatweaker:ruby_orb@1.8"),
                "LuaBinder must dispatch shootProjectile to the entity impl: " + mockPlayer.projectiles);
    }

    @Test
    public void testEntityMethodsFromLua() throws IOException {
        var playerTable = EntitiesLuaBinding.createPlayerLuaTable(engine, mockPlayer);
        engine.getGlobalEnvironment().rawset("player", playerTable);

        String script = """
            player:setHealth(10.0)
            player:heal(5.0)
            player:addTag("test_tag")
        """;
        File file = File.createTempFile("test_entity_methods", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), script);

        engine.executeScript(file, "TEST");

        assertEquals(15.0f, mockPlayer.getHealth());
    }

    @Test
    public void testPlayerScreenAndCustomDataFromLua() throws IOException {
        var playerTable = EntitiesLuaBinding.createPlayerLuaTable(engine, mockPlayer);
        engine.getGlobalEnvironment().rawset("player", playerTable);

        String script = """
            player:syncCustomData("mana", 100)
            player:openScreen("quest_book", "Quests")
            player:closeScreen()
        """;
        File file = File.createTempFile("test_player_screen", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), script);

        engine.executeScript(file, "TEST");

        assertEquals(100, ((Number) mockPlayer.syncedData.get("mana")).intValue());
        assertEquals(2, mockPlayer.screens.size());
        assertEquals("OPEN:quest_book:Quests", mockPlayer.screens.get(0));
        assertEquals("CLOSE", mockPlayer.screens.get(1));
    }
}
