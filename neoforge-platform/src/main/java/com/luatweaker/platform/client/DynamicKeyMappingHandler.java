package com.luatweaker.platform.client;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicKeyMappingHandler {
    private static final Map<String, KeyMappingRecord> REGISTERED_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LAST_DOWN_STATE = new ConcurrentHashMap<>();

    private record KeyMappingRecord(KeyMapping mapping, String payload) {}

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        Object service = LuaServiceRegistry.get("KeyBindService");
        if (service instanceof IKeyBindService keyBindService) {
            for (IKeyBindService.KeyBindEntry entry : keyBindService.getRegisteredKeyBinds()) {
                // Display name and category are used VERBATIM: Minecraft's translatable
                // component falls back to the raw string when no lang entry exists, so a
                // mod can declare a plain label ("Ruby Mod Controls") and it shows as-is.
                // Lang entries are optional — they only override the label (i18n).
                String descKey = (entry.displayName() != null && !entry.displayName().isBlank())
                        ? entry.displayName() : entry.id();
                String categoryKey = (entry.category() != null && !entry.category().isBlank())
                        ? entry.category() : "LuaTweaker";
                KeyMapping keyMapping = new KeyMapping(
                        descKey,
                        InputConstants.Type.KEYSYM,
                        entry.defaultKey(),
                        categoryKey
                );
                event.register(keyMapping);
                KeyMappingRecord previous = REGISTERED_MAPPINGS.put(entry.id(), new KeyMappingRecord(keyMapping, entry.onPressPayload()));
                if (previous != null) {
                    com.luatweaker.api.log.LuaTweakerLog.get().warn(
                            com.luatweaker.api.log.LogStage.SYSTEM,
                            "[KeyMapping] Duplicate keybind id '" + entry.id() + "' overwritten — mods must use unique ids to avoid collisions"
                    );
                }
                com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[KeyMapping] Registered Dynamic KeyBinding with Minecraft Controls: '" + descKey + "' (ID: " + entry.id() + ", Category: " + categoryKey + ", Default Key: " + entry.defaultKey() + ")"
                );
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        for (Map.Entry<String, KeyMappingRecord> mapEntry : REGISTERED_MAPPINGS.entrySet()) {
            String keyBindId = mapEntry.getKey();
            KeyMappingRecord record = mapEntry.getValue();
            // Edge-trigger: fire exactly once per press. consumeClick() would replay
            // every queued press while a key is HELD (OS auto-repeat queues several
            // press events per tick), flooding the server with duplicate packets.
            boolean isDown = InputConstants.isKeyDown(
                    net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(),
                    record.mapping().getKey().getValue());
            boolean wasDown = Boolean.TRUE.equals(LAST_DOWN_STATE.getOrDefault(keyBindId, false));
            LAST_DOWN_STATE.put(keyBindId, isDown);
            if (!isDown || wasDown || !Platform.isInitialized()) {
                continue;
            }

            com.luatweaker.api.log.LuaTweakerLog.get().info(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[KeyMapping] KeyMapping Triggered: '" + keyBindId + "' (Payload: " + record.payload() + ")"
            );

            // 1. Trigger Client-side Lua Signal by KeyMapping ID
            Object service = LuaServiceRegistry.get("KeyBindService");
            if (service instanceof IKeyBindService keyBindService) {
                keyBindService.triggerKeyBind(keyBindId, record.payload());
            }

            // 2. If payload is present, also send payload packet to server
            if (record.payload() != null && !record.payload().isEmpty()) {
                Platform.getNetwork().sendPayloadPacketToServer(record.payload(), "[]");
            }
        }
    }
}
