package com.luatweaker.entities;

import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PlayerEntityTest {

    private CobaltLuaEngine engine;
    private MockPlayer mockPlayer;

    private static class MockPlayer implements IPlayer {
        final List<String> messages = new ArrayList<>();
        final List<String> actionBars = new ArrayList<>();
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
    }

    @BeforeEach
    public void setUp() {
        engine = new CobaltLuaEngine();
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
}
