package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@code Mod:GetService} lookup and the "malformed input is reported, never thrown into
 * the script" contract of {@code ClientEffects:FlashScreen}.
 */
class ClientServiceBindingTest extends ClientServiceFixture {

    @Test
    void cameraAndEffectsServicesAreReachableThroughModGetService() {
        engine.executeStringOrThrow("""
                local camera = Mod:GetService('Camera')
                assert(camera ~= nil, 'Camera service should exist')
                camera:Shake(1.5, 0.5)
                local effects = Mod:GetService('ClientEffects')
                assert(effects ~= nil, 'ClientEffects service should exist')
                effects:PlaySound('minecraft:block.note_block.harp', 1.0, 1.0)
                """, "ClientTest");
        assertEquals(List.of("minecraft:block.note_block.harp"), client.playedSounds(),
                "the binding must forward the sound id to the platform helper");
    }

    @Test
    void flashScreenRejectsMalformedColorsWithoutThrowing() {
        assertDoesNotThrow(() -> engine.executeStringOrThrow("""
                ClientEffects:FlashScreen('#FF0000', 0.2)
                ClientEffects:FlashScreen('not-a-color', 0.2)
                ClientEffects:FlashScreen('#FFF', 0.2)
                """, "FlashScreen"),
                "malformed colours must be reported without throwing into the Lua script");
    }
}
