package com.luatweaker.platform.bootstrap;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.client.KeyBindServiceImpl;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.network.NetworkServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keybind payloads are a Lua-to-Lua string contract (keybind onPress name vs
 * server RemoteEvent name). The validator must flag every payload that has no
 * matching remote event, and stay silent when the contract is intact.
 */
public class KeyBindPayloadValidatorTest {

    @Test
    public void matchedPayloadsProduceNoWarnings() {
        CobaltLuaEngine engine = new CobaltLuaEngine(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);
        networkService.GetOrCreateRemoteEvent("StaffCastSkill");
        networkService.GetOrCreateRemoteEvent("StaffSwapSkill");
        networkService.GetOrCreateRemoteEvent("TargetMark");

        KeyBindServiceImpl keyBindService = new KeyBindServiceImpl();
        keyBindService.registerKeyBind("magic_staff_cast", "Cast", "Ruby Mod Controls", 71, "StaffCastSkill");
        keyBindService.registerKeyBind("magic_staff_switch", "Switch", "Ruby Mod Controls", 82, "StaffSwapSkill");
        keyBindService.registerKeyBind("magic_staff_mark_target", "Mark", "Ruby Mod Controls", 88, "TargetMark");

        assertTrue(KeyBindPayloadValidator.collectWarnings(keyBindService, networkService).isEmpty());
    }

    @Test
    public void typoPayloadProducesWarningNamingTheKeybind() {
        CobaltLuaEngine engine = new CobaltLuaEngine(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);
        networkService.GetOrCreateRemoteEvent("StaffCastSkill");

        KeyBindServiceImpl keyBindService = new KeyBindServiceImpl();
        keyBindService.registerKeyBind("magic_staff_cast", "Cast", "Ruby Mod Controls", 71, "StaffCastSkill");
        // Typos: neither of these remote events exists server-side.
        keyBindService.registerKeyBind("magic_staff_switch", "Switch", "Ruby Mod Controls", 82, "StaffSwappSkill");
        keyBindService.registerKeyBind("magic_staff_mark_target", "Mark", "Ruby Mod Controls", 88, "TargetMArk");

        List<String> warnings = KeyBindPayloadValidator.collectWarnings(keyBindService, networkService);
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("magic_staff_switch"), "warning must name the keybind id");
        assertTrue(warnings.get(0).contains("StaffSwappSkill"), "warning must name the mismatched payload");
        assertTrue(warnings.get(1).contains("magic_staff_mark_target"));
    }

    @Test
    public void blankPayloadIsSkipped() {
        CobaltLuaEngine engine = new CobaltLuaEngine(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);

        KeyBindServiceImpl keyBindService = new KeyBindServiceImpl();
        // A keybind without onPress is client-side only (no server payload).
        keyBindService.registerKeyBind("client_only", "No Payload", "Cat", 90, "");

        assertTrue(KeyBindPayloadValidator.collectWarnings(keyBindService, networkService).isEmpty());
    }
}
