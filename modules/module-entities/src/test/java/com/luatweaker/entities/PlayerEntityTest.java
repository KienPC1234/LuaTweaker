package com.luatweaker.entities;

import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies the player entity table end-to-end: messages, projectile dispatch,
 * entity methods, screens and synced custom data.
 */
class PlayerEntityTest {

    private static final String SCRIPTS_GLOBAL = "player";
    private static final String PLAYER_UUID = "00000000-0000-0000-0000-000000000000";

    private ILuaEngine engine;
    private RecordingPlayer player;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        player = new RecordingPlayer();
        engine.getGlobalEnvironment().rawset(SCRIPTS_GLOBAL,
                EntitiesLuaBinding.createPlayerLuaTable(engine, player));
    }

    @Test
    void playerSendMessageFromLua() throws IOException {
        runLua("player:sendMessage('Hello Minecraft!')");

        Assertions.assertEquals(List.of("Hello Minecraft!"), player.messages,
                "player:sendMessage must reach the IPlayer implementation exactly once");
    }

    @Test
    void shootProjectileDispatchesToTheImplementation() throws IOException {
        runLua("player:shootProjectile('luatweaker:ruby_orb', 1.8)");

        Assertions.assertEquals(List.of("luatweaker:ruby_orb@1.8"), player.projectiles,
                "LuaBinder must dispatch shootProjectile with its type and speed");
    }

    @Test
    void entityMethodsFromLua() throws IOException {
        runLua("player:setHealth(10.0)\n"
                + "player:heal(5.0)\n"
                + "player:addTag(\"test_tag\")");

        Assertions.assertEquals(15.0f, player.getHealth(),
                "setHealth followed by the default heal must leave the player at 15 health");
    }

    @Test
    void playerScreenAndCustomDataFromLua() throws IOException {
        runLua("player:syncCustomData('mana', 100)\n"
                + "player:openScreen('quest_book', 'Quests')\n"
                + "player:closeScreen()");

        Assertions.assertAll("screen and custom-data calls must reach the player",
            () -> Assertions.assertEquals(100, ((Number) player.syncedData.get("mana")).intValue(),
                "syncCustomData must unwrap the Lua number"),
            () -> Assertions.assertEquals(List.of("OPEN:quest_book:Quests", "CLOSE"), player.screens,
                "openScreen and closeScreen must be forwarded in order"));
    }

    private void runLua(String script) throws IOException {
        File file = File.createTempFile("player_script", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), script);
        engine.executeScript(file, "TEST");
    }

    /** No-op IPlayer surface: keeps the interface boilerplate out of the recording fake. */
    private static class PlayerStub implements IPlayer {
        private static final float MAX_HEALTH = 20.0f;

        final List<String> messages = new ArrayList<>();
        final List<String> actionBars = new ArrayList<>();
        final List<String> screens = new ArrayList<>();
        final Map<String, Object> syncedData = new ConcurrentHashMap<>();
        float health = MAX_HEALTH;

        @Override public void sendMessage(String message) { messages.add(message); }
        @Override public void sendActionBar(String message) { actionBars.add(message); }
        @Override public String getName() { return "Steve"; }
        @Override public String getUuid() { return PLAYER_UUID; }
        @Override public boolean isSneaking() { return false; }
        @Override public boolean isCreative() { return true; }
        @Override public void giveItem(String itemId, int count) { }
        @Override public void giveExperience(int exp) { }
        @Override public void addEffect(String effectId, int durationTicks, int amplifier) { }
        @Override public void playSound(String soundId, float volume, float pitch) { }
        @Override public String getType() { return "minecraft:player"; }
        @Override public float getHealth() { return health; }
        @Override public void setHealth(float health) { this.health = health; }
        @Override public float getMaxHealth() { return MAX_HEALTH; }
        @Override public boolean isAlive() { return health > 0; }
        @Override public void remove() { }
        @Override public Object getRawEntity() { return this; }
    }

    /** Records the five calls under test; every other IPlayer method stays a no-op. */
    private static final class RecordingPlayer extends PlayerStub {
        final List<String> projectiles = new ArrayList<>();

        @Override
        public void shootProjectile(String projectileType, double speed, double inaccuracy) {
            projectiles.add(projectileType + "@" + speed);
        }

        @Override
        public void syncCustomData(String key, Object value) {
            Object unwrapped = value;
            if (value instanceof ILuaValue luaValue) {
                if (luaValue.isNumber()) {
                    unwrapped = luaValue.asDouble();
                } else if (luaValue.isString()) {
                    unwrapped = luaValue.asString();
                } else if (luaValue.isBoolean()) {
                    unwrapped = luaValue.asBoolean();
                }
            }
            syncedData.put(key, unwrapped);
        }

        @Override
        public void openScreen(String screenId, String title) {
            screens.add("OPEN:" + screenId + ":" + title);
        }

        @Override
        public void closeScreen() {
            screens.add("CLOSE");
        }
    }
}
