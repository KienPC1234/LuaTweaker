package com.luatweaker.interaction;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests Lua bindings for World environment primitives: time, weather, biome, light level,
 * sky visibility, highest block Y, chunk loading, difficulty, seed, world spawn, border, and game rules.
 */
class WorldEnvironmentBindingTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final long INITIAL_DAY_TIME = 6_000L;
    private static final long INITIAL_GAME_TIME = 48_000L;
    private static final long DEFAULT_SEED = 987_654_321L;

    private ILuaEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void initWithMock(MockPlatformInteraction mock) {
        Platform.setInteraction(mock);
        engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);
    }

    @Test
    void timeAndWeatherPrimitivesHandleDefaultAndExplicitDimensions() {
        MockTimeWeather mock = new MockTimeWeather();
        initWithMock(mock);

        engine.executeStringOrThrow("""
                assert(World:GetDayTime() == 6000)
                assert(World:GetGameTime('minecraft:the_nether') == 48000)
                assert(World:SetDayTime(13000) == true)
                assert(World:GetDayTime() == 13000)
                assert(World:SetDayTime('minecraft:the_nether', 18000) == true)
                assert(World:SetDayTime(-1) == false)

                assert(World:IsRaining() == false)
                assert(World:SetWeather('rain', 1200) == true)
                assert(World:IsRaining() == true)
                assert(World:IsThundering() == false)
                assert(World:SetWeather('minecraft:the_nether', 'thunder', 2400) == true)
                assert(World:IsThundering('minecraft:the_nether') == true)
                local w = World:GetWeather()
                assert(w.Raining == true and w.RainLevel == 1.0)
                assert(World:SetWeather('rain', -10) == false)
                """, "world_time_weather_test");

        assertAll("weather delegation",
                () -> assertEquals(NETHER, mock.lastWeatherDim, "explicit dimension must reach setWeather"),
                () -> assertEquals("thunder", mock.lastWeatherKind, "weather kind must match"),
                () -> assertEquals(2400, mock.lastWeatherTicks, "weather duration must match"));
    }

    @Test
    void spatialBiomeLightSkyAndHeightmapAcceptNumericAndTableCoords() {
        MockSpatialEnv mock = new MockSpatialEnv();
        initWithMock(mock);

        engine.executeStringOrThrow("""
                assert(World:GetBiome(10, 64, 20) == 'minecraft:plains')
                assert(World:GetBiome('minecraft:the_nether', { X = 1, Y = 64, Z = 2 }) == 'minecraft:nether_wastes')
                assert(World:GetLightLevel(10, 64, 20) == 15)
                assert(World:GetLightLevel(10, 64, 20, 'block') == 7)
                assert(World:CanSeeSky(10, 64, 20) == true)
                assert(World:GetHighestBlockY(10, 20) == 72)
                assert(World:GetHighestBlockY('minecraft:overworld', { X = 10, Z = 20 }, 'ocean_floor') == 54)
                assert(World:IsChunkLoaded(10, 20) == true)
                assert(World:IsChunkLoaded(9999, 9999) == false)

                assert(World:SetWorldSpawn(100, 70, -50, 90) == true)
                local spawn = World:GetWorldSpawn()
                assert(spawn.X == 100 and spawn.Y == 70 and spawn.Z == -50 and spawn.Angle == 90)
                """, "world_spatial_env_test");

        assertTrue(mock.setSpawnCalled, "SetWorldSpawn must invoke platform interaction");
    }

    @Test
    void metadataBorderAndGameRulePrimitivesValidateInputs() {
        MockMetaEnv mock = new MockMetaEnv();
        initWithMock(mock);

        engine.executeStringOrThrow("""
                assert(World:GetDifficulty() == 'normal')
                assert(World:GetSeed() == 987654321)

                assert(World:SetWorldBorder(10, -20, 500, 2500) == true)
                local border = World:GetWorldBorder()
                assert(border.CenterX == 10 and border.CenterZ == -20 and border.Size == 500)
                assert(World:SetWorldBorder('minecraft:the_nether', 0, 0, 128) == true)
                assert(World:SetWorldBorder(0, 0, 100, -5) == false)

                assert(World:GetGameRule('keepInventory') == false)
                assert(World:SetGameRule('keepInventory', true) == true)
                assert(World:GetGameRule('keepInventory') == true)
                assert(World:SetGameRule('randomTickSpeed', 6) == true)
                assert(World:GetGameRule('randomTickSpeed') == 6)
                assert(World:SetGameRule('keepInventory', { bad = 1 }) == false)
                assert(World:GetGameRule('nonExistentRule') == nil)
                """, "world_meta_border_gamerule_test");

        assertAll("border and gamerule state",
                () -> assertEquals(NETHER, mock.lastBorderDim, "explicit dimension passed to setWorldBorder"),
                () -> assertEquals(128.0, mock.borderSize, 1e-6, "border size updated"),
                () -> assertFalse(Boolean.FALSE.equals(mock.rules.get("keepInventory")), "game rule updated"));
    }

    private static final class MockTimeWeather implements MockPlatformInteraction {
        long dayTime = INITIAL_DAY_TIME;
        String lastWeatherDim = OVERWORLD;
        String lastWeatherKind = "clear";
        int lastWeatherTicks;
        boolean raining;
        boolean thundering;

        @Override public long getDayTime(String dimension) { return dayTime; }
        @Override public boolean setDayTime(String dimension, long value) { dayTime = value; return true; }
        @Override public long getGameTime(String dimension) { return INITIAL_GAME_TIME; }

        @Override
        public Map<String, Object> getWeather(String dimension) {
            return Map.of("Raining", raining, "Thundering", thundering,
                    "RainLevel", raining ? 1.0f : 0.0f, "ThunderLevel", thundering ? 1.0f : 0.0f);
        }

        @Override
        public boolean setWeather(String dimension, String kind, int durationTicks) {
            lastWeatherDim = dimension;
            lastWeatherKind = kind;
            lastWeatherTicks = durationTicks;
            raining = "rain".equals(kind) || "thunder".equals(kind);
            thundering = "thunder".equals(kind);
            return true;
        }
    }

    private static final class MockSpatialEnv implements MockPlatformInteraction {
        int spawnX;
        int spawnY = 64;
        int spawnZ;
        float spawnAngle;
        boolean setSpawnCalled;

        @Override public String getBiome(String dim, int x, int y, int z) { return NETHER.equals(dim) ? "minecraft:nether_wastes" : "minecraft:plains"; }
        @Override public int getLightLevel(String dim, int x, int y, int z, String kind) { return "block".equals(kind) ? 7 : 15; }
        @Override public boolean canSeeSky(String dim, int x, int y, int z) { return x == 10 && z == 20; }
        @Override public int getHighestBlockY(String dim, int x, int z, String kind) { return "ocean_floor".equals(kind) ? 54 : 72; }
        @Override public boolean isChunkLoaded(String dim, int x, int z) { return x == 10 && z == 20; }

        @Override
        public Map<String, Object> getWorldSpawn(String dimension) {
            return Map.of("X", spawnX, "Y", spawnY, "Z", spawnZ, "Angle", spawnAngle);
        }

        @Override
        public boolean setWorldSpawn(String dimension, int x, int y, int z, float angle) {
            spawnX = x;
            spawnY = y;
            spawnZ = z;
            spawnAngle = angle;
            setSpawnCalled = true;
            return true;
        }
    }

    private static final class MockMetaEnv implements MockPlatformInteraction {
        String lastBorderDim = OVERWORLD;
        double borderX;
        double borderZ;
        double borderSize = 60_000_000.0;
        final Map<String, Object> rules = new ConcurrentHashMap<>(Map.of("keepInventory", false, "randomTickSpeed", 3));

        @Override public String getDifficulty(String dimension) { return "normal"; }
        @Override public long getSeed(String dimension) { return DEFAULT_SEED; }

        @Override
        public Map<String, Object> getWorldBorder(String dimension) {
            return Map.of("CenterX", borderX, "CenterZ", borderZ, "Size", borderSize,
                    "DamagePerBlock", 0.2, "DamageSafeZone", 5.0, "WarningTime", 15, "WarningBlocks", 5);
        }

        @Override
        public boolean setWorldBorder(String dimension, double cx, double cz, double size, long lerpMillis) {
            lastBorderDim = dimension;
            borderX = cx;
            borderZ = cz;
            borderSize = size;
            return true;
        }

        @Override public Object getGameRule(String ruleName) { return rules.get(ruleName); }

        @Override
        public boolean setGameRule(String ruleName, Object value) {
            if (!rules.containsKey(ruleName)) {
                return false;
            }
            rules.put(ruleName, value);
            return true;
        }
    }
}
