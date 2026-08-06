package com.luatweaker.platform.bootstrap;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.network.NetworkServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Load-time contract check between keybind payloads and server remote events.
 *
 * <p>A keybind declared with {@code Content.NewKeyMapping(...):onPress("Name")}
 * sends "Name" over the network on press; the server must have registered a
 * RemoteEvent with the exact same name ({@code Network:GetOrCreateRemoteEvent("Name")})
 * or the press is silently dropped. A typo on either side used to be invisible -
 * this validator logs a loud warning instead.</p>
 */
public final class KeyBindPayloadValidator {

    private KeyBindPayloadValidator() {}

    /** Runs after every mod-load pass (startup + reload). No-op when services are absent. */
    public static void validate() {
        Object keyBindObj = LuaServiceRegistry.get("KeyBindService");
        Object networkObj = LuaServiceRegistry.get("NetworkServiceImpl");
        if (keyBindObj instanceof IKeyBindService keyBindService
                && networkObj instanceof NetworkServiceImpl networkService) {
            for (String warning : collectWarnings(keyBindService, networkService)) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM, warning);
            }
        }
    }

    /** One warning per keybind payload that has no matching server RemoteEvent. */
    static @NotNull List<String> collectWarnings(
            @NotNull IKeyBindService keyBindService, @NotNull NetworkServiceImpl networkService) {
        List<String> warnings = new ArrayList<>();
        Set<String> remoteEvents = networkService.getRegisteredRemoteEventNames();
        for (IKeyBindService.KeyBindEntry entry : keyBindService.getRegisteredKeyBinds()) {
            String payload = entry.onPressPayload();
            if (payload == null || payload.isBlank()) continue;
            if (!remoteEvents.contains(payload)) {
                warnings.add("[KeyMapping] Keybind '" + entry.id() + "' sends payload '" + payload
                        + "' but no server RemoteEvent with that name is registered. The key press "
                        + "will be silently ignored. Registered remote events: " + remoteEvents);
            }
        }
        return warnings;
    }
}
