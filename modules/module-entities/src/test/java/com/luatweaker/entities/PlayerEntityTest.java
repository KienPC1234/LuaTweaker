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

    @Test
    public void testShootProjectileDispatchesToPlatform() throws IOException {
        java.util.concurrent.atomic.AtomicReference<String> firedType = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Double> firedSpeed = new java.util.concurrent.atomic.AtomicReference<>();
        com.luatweaker.api.pal.Platform.setInteraction(new com.luatweaker.api.pal.IPlatformInteraction() {
            @Override
            public void shootProjectile(com.luatweaker.api.entity.IEntity shooter, String projectileType, double speed, double inaccuracy) {
                firedType.set(projectileType);
                firedSpeed.set(speed);
            }

            @Override
            public void shootProjectileAt(com.luatweaker.api.entity.IEntity shooter, String projectileType, com.luatweaker.api.entity.IEntity target, double speed) {}

            @Override
            public void playAnimation(com.luatweaker.api.entity.IEntity entity, String animName, double speed, double transition) {}

            @Override
            public boolean performBlockBreak(com.luatweaker.api.entity.IEntity actor, int x, int y, int z) { return false; }

            @Override
            public boolean performBlockPlace(com.luatweaker.api.entity.IEntity actor, int x, int y, int z, String blockId) { return false; }

            @Override
            public boolean performBlockUse(com.luatweaker.api.entity.IEntity actor, int x, int y, int z) { return false; }

            @Override
            public boolean performItemUse(com.luatweaker.api.entity.IEntity actor, int slot) { return false; }

            @Override
            public void lookAt(com.luatweaker.api.entity.IEntity actor, double x, double y, double z) {}

            @Override
            public void lookAt(com.luatweaker.api.entity.IEntity actor, com.luatweaker.api.entity.IEntity target) {}

            @Override
            public boolean moveInventoryItem(com.luatweaker.api.entity.IEntity actor, int fromSlot, int toSlot) { return false; }

            @Override
            public boolean dropInventoryItem(com.luatweaker.api.entity.IEntity actor, int slot, int count) { return false; }

            @Override
            public java.util.List<com.luatweaker.api.objects.IWorldBlock> getNearbyBlocks(com.luatweaker.api.entity.IEntity entity, int radius) { return java.util.List.of(); }

            @Override
            public java.util.List<com.luatweaker.api.objects.ILocatedItem> getInventoryItems(com.luatweaker.api.entity.IEntity entity) { return java.util.List.of(); }

            @Override
            public com.luatweaker.api.interaction.IInteractableBlock getInteractableBlock(String dimension, int x, int y, int z) { return null; }

            @Override
            public com.luatweaker.api.interaction.IInteractableItem getInteractableItem(Object entityOrBlock, int slot) { return null; }

            @Override
            public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(String uuid) { return null; }

            @Override
            public com.luatweaker.api.interaction.IInteractableEntity getInteractableEntity(Object rawEntity) { return null; }
        });

        var playerTable = EntitiesLuaBinding.createPlayerLuaTable(engine, mockPlayer);
        engine.getGlobalEnvironment().rawset("player", playerTable);

        File file = File.createTempFile("test_shoot", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "player:shootProjectile('luatweaker:ruby_orb', 1.8)");
        engine.executeScript(file, "TEST");

        assertEquals("luatweaker:ruby_orb", firedType.get());
        assertEquals(1.8, firedSpeed.get());
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
}
