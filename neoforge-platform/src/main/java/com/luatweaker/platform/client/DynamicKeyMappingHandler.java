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

    private record KeyMappingRecord(KeyMapping mapping, String payload) {}

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        Object service = LuaServiceRegistry.get("KeyBindService");
        if (service instanceof IKeyBindService keyBindService) {
            for (IKeyBindService.KeyBindEntry entry : keyBindService.getRegisteredKeyBinds()) {
                String descKey = (entry.displayName() != null && !entry.displayName().isBlank()) ? entry.displayName() : "key.luatweaker." + entry.id();
                String categoryKey = "key.categories." + entry.category();
                KeyMapping keyMapping = new KeyMapping(
                        descKey,
                        InputConstants.Type.KEYSYM,
                        entry.defaultKey(),
                        categoryKey
                );
                event.register(keyMapping);
                REGISTERED_MAPPINGS.put(entry.id(), new KeyMappingRecord(keyMapping, entry.onPressPayload()));
                com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[KeyMapping] Registered Dynamic KeyBinding with Minecraft Controls: '" + descKey + "' (ID: " + entry.id() + ", Default Key: " + entry.defaultKey() + ")"
                );
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        for (Map.Entry<String, KeyMappingRecord> mapEntry : REGISTERED_MAPPINGS.entrySet()) {
            String keyBindId = mapEntry.getKey();
            KeyMappingRecord record = mapEntry.getValue();
            while (record.mapping().consumeClick()) {
                if (Platform.isInitialized()) {
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
                        Platform.get().sendPayloadPacketToServer(record.payload(), "[]");
                    }
                }
            }
        }
    }
}
