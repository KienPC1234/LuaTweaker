package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClientServiceImplTest {

    private ILuaEngine engine;
    private ClientServiceImpl clientService;
    private RecordingClient client;
    private final List<String> playedSounds = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        // The client bindings delegate to the PAL; without a helper every call fails.
        client = new RecordingClient(playedSounds);
        Platform.setClient(client);
        engine = LuaEngineFactory.create(true);
        clientService = new ClientServiceImpl();
        ClientLuaBinding.registerBindings(engine, clientService);
    }

    @AfterEach
    public void tearDown() {
        // Platform is process-wide static state; leaking a fake client would change
        // every later client test in this JVM.
        Platform.setClient(null);
    }

    @Test
    public void testClientServiceBindings() {
        engine.executeStringOrThrow(
            "local camera = Mod:GetService('Camera')\n" +
            "assert(camera ~= nil, 'Camera service should exist')\n" +
            "camera:Shake(1.5, 0.5)\n" +
            "local effects = Mod:GetService('ClientEffects')\n" +
            "assert(effects ~= nil, 'ClientEffects service should exist')\n" +
            "effects:PlaySound('minecraft:block.note_block.harp', 1.0, 1.0)",
            "ClientTest"
        );
        assertEquals(List.of("minecraft:block.note_block.harp"), playedSounds,
                "the binding must forward the sound id to the platform helper");
    }

    @Test
    public void soundDefaultsReachThePlatform() {
        engine.executeStringOrThrow(
            "ClientEffects:PlaySound('minecraft:block.note_block.harp')",
            "SoundDefaults");

        assertEquals("minecraft:block.note_block.harp", client.lastSoundId);
        assertEquals(1.0, client.lastVolume, 1e-9, "volume must default to 1.0");
        assertEquals(1.0, client.lastPitch, 1e-9, "pitch must default to 1.0");
        assertEquals("MASTER", client.lastSource, "UI sounds default to the MASTER category");
        assertFalse(client.lastLoop, "one-shot playback is the default");
    }

    @Test
    public void positionalLoopingSoundReturnsTheHandleAndStops() {
        engine.executeStringOrThrow(
            "local handle = ClientEffects:PlaySoundAt('minecraft:ambient.cave', 10, 20, 30, " +
            "0.5, 0.8, 'AMBIENT', true)\n" +
            "assert(handle == 41, 'the platform handle must reach Lua, got ' .. tostring(handle))\n" +
            "assert(ClientEffects:StopSound(handle) == true)\n" +
            "assert(ClientEffects:StopSound(handle) == false, 'a stale handle must report false')\n" +
            "assert(ClientEffects:GetActiveSoundCount() == 0)\n" +
            "ClientEffects:StopSounds('minecraft:ambient.cave', 'AMBIENT')\n" +
            "assert(ClientEffects:StopAllSounds() == 3)",
            "SoundHandle");

        assertEquals(10.0, client.lastX, 1e-9);
        assertEquals(20.0, client.lastY, 1e-9);
        assertEquals(30.0, client.lastZ, 1e-9);
        assertEquals("AMBIENT", client.lastSource);
        assertTrue(client.lastLoop, "loop=true must reach the platform");
        assertEquals("minecraft:ambient.cave", client.lastStoppedSounds);
        assertEquals("AMBIENT", client.lastStoppedSource);
        assertEquals(1, client.stopAllCalls);
    }

    @Test
    public void flashScreenRejectsMalformedColors() {
        engine.executeStringOrThrow(
            "ClientEffects:FlashScreen('#FF0000', 0.2)\n" +
            "ClientEffects:FlashScreen('not-a-color', 0.2)\n" +
            "ClientEffects:FlashScreen('#FFF', 0.2)",
            "FlashScreen");
        // No assertion on the flash state itself (time-based); the point is that malformed
        // input is reported instead of throwing into the Lua script.
    }

    /** Minimal PAL double: records sound calls so the binding path is actually exercised. */
    private static final class RecordingClient implements IPlatformClient {
        private final List<String> sounds;
        String lastSoundId;
        double lastVolume;
        double lastPitch;
        String lastSource;
        boolean lastLoop;
        double lastX;
        double lastY;
        double lastZ;
        String lastStoppedSounds;
        String lastStoppedSource;
        int stopAllCalls;

        private RecordingClient(List<String> sounds) {
            this.sounds = sounds;
        }

        @Override
        public void spawnParticle(String particleId, double x, double y, double z,
                                  double vx, double vy, double vz) {
        }

        @Override
        public long playSound(String soundId, double volume, double pitch, String source, boolean loop) {
            sounds.add(soundId);
            lastSoundId = soundId;
            lastVolume = volume;
            lastPitch = pitch;
            lastSource = source;
            lastLoop = loop;
            return loop ? 41L : 0L;
        }

        @Override
        public long playSoundAt(String soundId, double x, double y, double z,
                                double volume, double pitch, String source, boolean loop) {
            sounds.add(soundId);
            lastSoundId = soundId;
            lastVolume = volume;
            lastPitch = pitch;
            lastSource = source;
            lastLoop = loop;
            lastX = x;
            lastY = y;
            lastZ = z;
            return loop ? 41L : 0L;
        }

        private boolean handleStopped;

        @Override
        public boolean stopSound(long handle) {
            if (handle != 41L || handleStopped) {
                return false;
            }
            handleStopped = true;
            return true;
        }

        @Override
        public void stopSounds(String soundId, String source) {
            lastStoppedSounds = soundId;
            lastStoppedSource = source;
        }

        @Override
        public int stopAllSounds() {
            stopAllCalls++;
            return 3;
        }

        @Override
        public int getActiveSoundCount() {
            return 0;
        }

        @Override
        public int getGuiScaledWidth() {
            return 640;
        }

        @Override
        public int getGuiScaledHeight() {
            return 360;
        }
    }
}
