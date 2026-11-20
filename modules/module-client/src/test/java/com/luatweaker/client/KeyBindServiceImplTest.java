package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class KeyBindServiceImplTest {

    @Test
    void registerKeyBind_FillsKeyAndNoneDefaults() {
        KeyBindServiceImpl service = new KeyBindServiceImpl();
        service.registerKeyBind("cast", "Cast", "Cat", 71, "P");

        IKeyBindService.KeyBindEntry entry = service.getRegisteredKeyBinds().get(0);
        assertEquals("key", entry.inputType());
        assertEquals("none", entry.modifier());
    }

    @Test
    void registerKeyBind_NormalizesTypeAndModifier() {
        KeyBindServiceImpl service = new KeyBindServiceImpl();
        service.registerKeyBind("click", "Click", "Cat", 0, "P", "MOUSE", "Shift");

        IKeyBindService.KeyBindEntry entry = service.getRegisteredKeyBinds().get(0);
        assertEquals("mouse", entry.inputType());
        assertEquals("shift", entry.modifier());
    }

    @Test
    void registerKeyBind_RejectsBlankIdBadTypeBadModifier() {
        KeyBindServiceImpl service = new KeyBindServiceImpl();
        assertThrows(IllegalArgumentException.class,
            () -> service.registerKeyBind("", "x", "y", 71, "P"));
        assertThrows(IllegalArgumentException.class,
            () -> service.registerKeyBind("x", "x", "y", 71, "P", "touch", "none"));
        assertThrows(IllegalArgumentException.class,
            () -> service.registerKeyBind("x", "x", "y", 71, "P", "key", "hyper"));
    }

    @Test
    void releaseListener_DispatchesReleases() {
        KeyBindServiceImpl service = new KeyBindServiceImpl();
        List<String> released = new ArrayList<>();
        service.setKeyBindReleaseListener((id, payload) -> released.add(id + "=" + payload));
        service.registerKeyBind("cast", "Cast", "Cat", 71, "P");

        service.triggerKeyBindRelease("cast", "P");
        assertEquals(List.of("cast=P"), released);
    }
}
