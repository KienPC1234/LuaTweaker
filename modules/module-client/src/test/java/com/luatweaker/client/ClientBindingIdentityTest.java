package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the canonical client binding surface: one table per service, exposed both as a global and
 * through {@code Mod:GetService}, with the signal/tween plumbing still attached afterwards.
 */
class ClientBindingIdentityTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl());
    }

    @AfterEach
    void tearDown() {
        TweenServiceImpl.clearAll();
        engine.shutdown();
    }

    @Test
    void clientServicesUseOneCanonicalBindingPerService() {
        engine.executeString("""
                assert(Camera ~= nil, 'Camera must be bound')
                assert(type(Camera.Shake) == 'function', 'Camera.Shake must be available')
                assert(ClientEffects ~= nil, 'ClientEffects must be bound')
                assert(type(ClientEffects.PlaySound) == 'function',
                    'ClientEffects.PlaySound must be available')
                assert(TweenService ~= nil, 'TweenService must be bound')
                assert(type(TweenService.Create) == 'function',
                    'TweenService.Create must be available')
                assert(CameraService == nil, 'undocumented CameraService alias must be removed')
                assert(ClientEffectsService == nil,
                    'undocumented ClientEffectsService alias must be removed')
                assert(Mod:GetService('Camera') == Camera,
                    'Camera global and service must be one table')
                assert(Mod:GetService('ClientEffects') == ClientEffects,
                    'ClientEffects global and service must be one table')
                assert(Mod:GetService('TweenService') == TweenService,
                    'TweenService global and service must be one table')
                """, "ClientSingleBindingTest");
        assertNull(engine.getAndClearLastExecutionError(),
                "every canonical client service must resolve to the same table it is registered as");
    }

    @Test
    void keyBindPressedSignalSurvivesBindingRegistration() {
        engine.executeString("""
                assert(Client.OnKeyBindPressed ~= nil,
                    'Client.OnKeyBindPressed must survive Java binding registration')
                local capturedId = nil
                local capturedPayload = nil
                Client.OnKeyBindPressed:Connect(function(keyBindId, payload)
                    capturedId = keyBindId
                    capturedPayload = payload
                end)
                Client.OnKeyBindPressed:Fire('magic_staff_cast', 'StaffCastSkill')
                require('LuaTweaker.Task')._tick()
                assert(capturedId == 'magic_staff_cast',
                    'listener must receive the keybind id, got: ' .. tostring(capturedId))
                assert(capturedPayload == 'StaffCastSkill',
                    'listener must receive the payload, got: ' .. tostring(capturedPayload))
                """, "KeyBindSignalTest");
        assertNull(engine.getAndClearLastExecutionError(),
                "the OnKeyBindPressed signal must keep working after the Java bindings are registered");
    }

    @Test
    void canonicalTweenServiceStillTicksTweens() {
        engine.executeString("""
                tweenInstance = { Value = 0 }
                tweenHandle = TweenService:Create(tweenInstance, { Duration = 1.0 }, { Value = 10.0 })
                assert(type(tweenHandle.Play) == 'function', 'Play must exist')
                assert(type(tweenHandle.Pause) == 'function', 'Pause must exist')
                assert(type(tweenHandle.Cancel) == 'function', 'Cancel must exist')
                tweenHandle:Play()
                """, "TweenCreateTest");
        String createError = engine.getAndClearLastExecutionError();

        TweenServiceImpl.tickAll(0.5);

        engine.executeString("""
                assert(math.abs(tweenInstance.Value - 5.0) < 1e-6,
                    'tween must interpolate to 5.0, got ' .. tostring(tweenInstance.Value))
                """, "TweenTickTest");
        String tickError = engine.getAndClearLastExecutionError();
        String error = createError == null ? tickError : createError;
        assertNull(error,
                "the canonical TweenService must create and tick a tween without a Lua error");
    }
}
