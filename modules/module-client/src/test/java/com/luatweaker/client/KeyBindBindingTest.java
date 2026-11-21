package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code Client.RegisterKeyBinding}: key-name/int/mouse defaults, modifier normalization, the
 * loud rejection of malformed input and the release signal.
 */
class KeyBindBindingTest {

    private ILuaEngine engine;
    private KeyBindServiceImpl service;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        service = new KeyBindServiceImpl();
        ClientLuaBinding.registerBindings(engine, new ClientServiceImpl(), service,
                new MockGuiService());
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
    }

    private IKeyBindService.KeyBindEntry entry(String id) {
        return service.getRegisteredKeyBinds().stream()
                .filter(bind -> bind.id().equals(id))
                .findFirst().orElseThrow();
    }

    @Test
    void acceptsKeyNamesAndRawInts() {
        engine.executeStringOrThrow("""
                Client.registerKeyBinding('staff_cast', 'Cast', 'Ruby Mod', 'G', 'StaffCastSkill')
                Client.registerKeyBinding('staff_swap', 'Ruby Mod', 82, 'StaffSwapSkill')
                """, "KeyNameTest");
        assertEquals(List.of(71, 82),
                List.of(entry("staff_cast").defaultKey(), entry("staff_swap").defaultKey()),
                "the 'G' name must resolve to 71 and a raw 82 must pass through unchanged");
    }

    @Test
    void rejectsTooFewArguments() {
        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                        "Client.registerKeyBinding('only_id')\n", "KeyArityTest"),
                "a keybind registration without a key must fail instead of silently no-oping");
    }

    @Test
    void rejectsUnknownKeyName() {
        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                        "Client.registerKeyBinding('x', 'Cat', 'NOT_A_KEY', 'P')\n", "KeyNameTypoTest"),
                "an unknown key name must fail instead of binding an arbitrary keysym");
    }

    @Test
    void rejectsBlankId() {
        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                        "Client.registerKeyBinding('', 'Cat', 'G', 'P')\n", "KeyBlankIdTest"),
                "a blank keybind id must be rejected");
    }

    @Test
    void acceptsMouseButtonsAndModifiers() {
        engine.executeStringOrThrow("""
                Client.registerKeyBinding('aoe', 'AoE', 'Cat', 'MOUSE_RIGHT', 'AoePayload', 'shift')
                """, "KeyMouseModTest");
        assertEquals(List.of("mouse", 1, "shift"),
                List.of(entry("aoe").inputType(), entry("aoe").defaultKey(),
                        entry("aoe").modifier()),
                "MOUSE_RIGHT must bind as mouse button 1 and 'shift' must be normalized");
    }

    @Test
    void rejectsUnknownModifier() {
        assertThrows(RuntimeException.class, () -> engine.executeStringOrThrow(
                        "Client.registerKeyBinding('bad', 'Disp', 'Cat', 'G', 'P', 'hyper')\n",
                        "KeyModTypoTest"),
                "an unknown modifier must be rejected instead of registering a bind that never fires");
    }

    @Test
    void releaseSignalDispatchesThroughService() {
        engine.executeStringOrThrow("""
                releasedId = nil
                Client.OnKeyBindReleased:Connect(function(keyBindId, payload)
                    releasedId = keyBindId .. '=' .. payload
                end)
                """, "ReleaseConnectTest");
        service.registerKeyBind("cast", "Cast", "Cat", 71, "P");
        service.triggerKeyBindRelease("cast", "P");

        engine.executeStringOrThrow("""
                assert(releasedId == 'cast=P', 'release listener must fire, got: '
                    .. tostring(releasedId))
                """, "ReleaseFireTest");
        assertTrue(engine.getAndClearLastExecutionError() == null,
                "triggerKeyBindRelease must reach the Lua OnKeyBindReleased listeners");
    }
}
