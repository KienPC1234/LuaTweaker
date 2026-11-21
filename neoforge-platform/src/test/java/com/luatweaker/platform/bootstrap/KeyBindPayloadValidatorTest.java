package com.luatweaker.platform.bootstrap;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.client.KeyBindServiceImpl;
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
class KeyBindPayloadValidatorTest {

    private static final String CAST_PAYLOAD = "StaffCastSkill";
    private static final String SWAP_PAYLOAD = "StaffSwapSkill";
    private static final String MARK_PAYLOAD = "TargetMark";
    private static final String TYPO_SWAP_PAYLOAD = "StaffSwappSkill";
    private static final String TYPO_MARK_PAYLOAD = "TargetMArk";
    private static final String CONTROLS_CATEGORY = "Ruby Mod Controls";

    private static List<String> collectWarnings(String... payloads) {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);
        networkService.GetOrCreateRemoteEvent(CAST_PAYLOAD);
        networkService.GetOrCreateRemoteEvent(SWAP_PAYLOAD);
        networkService.GetOrCreateRemoteEvent(MARK_PAYLOAD);

        KeyBindServiceImpl keyBindService = new KeyBindServiceImpl();
        keyBindService.registerKeyBind("magic_staff_cast", "Cast", CONTROLS_CATEGORY, 71, CAST_PAYLOAD);
        keyBindService.registerKeyBind("magic_staff_switch", "Switch", CONTROLS_CATEGORY, 82,
                payloads.length > 0 ? payloads[0] : SWAP_PAYLOAD);
        keyBindService.registerKeyBind("magic_staff_mark_target", "Mark", CONTROLS_CATEGORY, 88,
                payloads.length > 1 ? payloads[1] : MARK_PAYLOAD);

        return KeyBindPayloadValidator.collectWarnings(keyBindService, networkService);
    }

    @Test
    void matchedPayloadsProduceNoWarnings() {
        assertTrue(collectWarnings().isEmpty(),
                "payloads with matching server remote events must stay silent");
    }

    @Test
    void typoPayloadProducesTwoWarnings() {
        assertEquals(2, collectWarnings(TYPO_SWAP_PAYLOAD, TYPO_MARK_PAYLOAD).size(),
                "each payload without a server remote event must be reported");
    }

    @Test
    void typoWarningNamesTheOffendingKeybind() {
        List<String> warnings = collectWarnings(TYPO_SWAP_PAYLOAD, TYPO_MARK_PAYLOAD);

        assertTrue(warnings.get(0).contains("magic_staff_switch"),
                "the warning must name the keybind id so the author can find it: " + warnings);
    }

    @Test
    void typoWarningNamesTheMismatchedPayload() {
        List<String> warnings = collectWarnings(TYPO_SWAP_PAYLOAD, TYPO_MARK_PAYLOAD);

        assertTrue(warnings.get(0).contains(TYPO_SWAP_PAYLOAD),
                "the warning must name the mismatched payload string: " + warnings);
    }

    @Test
    void secondTypoWarningNamesTheMarkKeybind() {
        List<String> warnings = collectWarnings(TYPO_SWAP_PAYLOAD, TYPO_MARK_PAYLOAD);

        assertTrue(warnings.get(1).contains("magic_staff_mark_target"),
                "every mismatched keybind must be reported, not just the first: " + warnings);
    }

    @Test
    void blankPayloadIsSkipped() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NetworkServiceImpl networkService = new NetworkServiceImpl(engine);

        KeyBindServiceImpl keyBindService = new KeyBindServiceImpl();
        // A keybind without onPress is client-side only (no server payload).
        keyBindService.registerKeyBind("client_only", "No Payload", "Cat", 90, "");

        assertTrue(KeyBindPayloadValidator.collectWarnings(keyBindService, networkService).isEmpty(),
                "a client-only keybind has no server payload to validate");
    }
}
