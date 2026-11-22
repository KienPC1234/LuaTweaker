package com.luatweaker.common.client;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicKeyMappingHandler {
    private static final String KEYBIND_SERVICE_NAME = "KeyBindService";
    private static final String MODIFIER_SHIFT = "shift";
    private static final String MODIFIER_CONTROL = "ctrl";
    private static final String MODIFIER_ALT = "alt";

    private DynamicKeyMappingHandler() {
    }

    private static final Map<String, KeyMappingRecord> REGISTERED_MAPPINGS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LAST_DOWN_STATE = new ConcurrentHashMap<>();

    private record KeyMappingRecord(KeyMapping mapping, String payload, String modifier) {}

    private static KeyMapping managerKey;

    private static final int GLFW_LEFT_SHIFT = 340;
    private static final int GLFW_RIGHT_SHIFT = 344;
    private static final int GLFW_LEFT_CONTROL = 341;
    private static final int GLFW_RIGHT_CONTROL = 345;
    private static final int GLFW_LEFT_ALT = 342;
    private static final int GLFW_RIGHT_ALT = 347;
    private static final long NO_WINDOW = 0L;

    public static void init() {
        managerKey = new KeyMapping("key.luatweaker.manager", InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), "key.categories.luatweaker");
        KeyMappingRegistry.register(managerKey);

        Object service = LuaServiceRegistry.get(KEYBIND_SERVICE_NAME);
        if (service instanceof IKeyBindService keyBindService) {
            for (IKeyBindService.KeyBindEntry entry : keyBindService.getRegisteredKeyBinds()) {
                KeyMappingRecord record = createRecord(entry);
                KeyMappingRegistry.register(record.mapping());
                com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[KeyMapping] Registered Dynamic KeyBinding with Minecraft Controls: '"
                                + record.mapping().getName() + "' (ID: " + entry.id() + ")"
                );
            }
        }

        ClientTickEvent.CLIENT_POST.register(mc -> onClientTick());
    }

    private static KeyMappingRecord createRecord(IKeyBindService.KeyBindEntry entry) {
        String descKey = (entry.displayName() != null && !entry.displayName().isBlank())
                ? entry.displayName() : entry.id();
        String categoryKey = (entry.category() != null && !entry.category().isBlank())
                ? entry.category() : "LuaTweaker";
        InputConstants.Type type = "mouse".equals(entry.inputType())
                ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
        KeyMapping keyMapping = new KeyMapping(
                descKey,
                type,
                entry.defaultKey(),
                categoryKey
        );
        KeyMappingRecord record = new KeyMappingRecord(keyMapping, entry.onPressPayload(), entry.modifier());
        KeyMappingRecord previous = REGISTERED_MAPPINGS.put(entry.id(), record);
        if (previous != null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[KeyMapping] Duplicate keybind id '" + entry.id() + "' overwritten — mods must use unique ids to avoid collisions"
            );
        }
        return record;
    }

    static void syncLateKeyBinds() {
        Object service = LuaServiceRegistry.get(KEYBIND_SERVICE_NAME);
        if (!(service instanceof IKeyBindService keyBindService)) {
            return;
        }
        for (IKeyBindService.KeyBindEntry entry : collectUnsynced(
                keyBindService.getRegisteredKeyBinds(), REGISTERED_MAPPINGS.keySet())) {
            createRecord(entry);
            LAST_DOWN_STATE.remove(entry.id());
            com.luatweaker.api.log.LuaTweakerLog.get().info(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[KeyMapping] Late keybind '" + entry.id() + "' active with default key "
                            + entry.defaultKey() + " (Controls GUI listing + rebinding needs a restart)"
            );
        }
    }

    static java.util.List<IKeyBindService.KeyBindEntry> collectUnsynced(
            java.util.List<IKeyBindService.KeyBindEntry> all, java.util.Set<String> liveIds) {
        java.util.List<IKeyBindService.KeyBindEntry> out = new java.util.ArrayList<>();
        for (IKeyBindService.KeyBindEntry entry : all) {
            if (entry != null && entry.id() != null && !liveIds.contains(entry.id())) {
                out.add(entry);
            }
        }
        return out;
    }

    public static void onClientTick() {
        syncLateKeyBinds();
        pollManagerKey();
        long window = windowHandle();
        boolean hasScreen = Minecraft.getInstance().screen != null;
        for (Map.Entry<String, KeyMappingRecord> mapEntry : REGISTERED_MAPPINGS.entrySet()) {
            handleMapping(window, hasScreen, mapEntry.getKey(), mapEntry.getValue());
        }
    }

    private static long windowHandle() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow() != null ? mc.getWindow().getWindow() : NO_WINDOW;
    }

    private static void handleMapping(long window, boolean hasScreen, @NotNull String keyBindId,
                                      @NotNull KeyMappingRecord record) {
        if (window == NO_WINDOW) { return; }
        boolean physDown = isPhysicallyDown(record.mapping());
        boolean wasDown = Boolean.TRUE.equals(LAST_DOWN_STATE.getOrDefault(keyBindId, false));
        LAST_DOWN_STATE.put(keyBindId, physDown);
        if (!physDown) {
            if (wasDown) {
                triggerRelease(keyBindId, record);
            }
            return;
        }
        if (wasDown || hasScreen || !modifierHeld(window, record.modifier()) || !Platform.isInitialized()) {
            return;
        }
        triggerPress(keyBindId, record);
    }

    private static void triggerRelease(@NotNull String keyBindId,
                                       @NotNull KeyMappingRecord record) {
        Object service = LuaServiceRegistry.get(KEYBIND_SERVICE_NAME);
        if (service instanceof IKeyBindService keyBindService) {
            keyBindService.triggerKeyBindRelease(keyBindId, record.payload());
        }
    }

    private static void triggerPress(@NotNull String keyBindId,
                                     @NotNull KeyMappingRecord record) {
        com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[KeyMapping] KeyMapping Triggered: '" + keyBindId + "' (Payload: " + record.payload() + ")"
        );
        Object service = LuaServiceRegistry.get(KEYBIND_SERVICE_NAME);
        if (service instanceof IKeyBindService keyBindService) {
            keyBindService.triggerKeyBind(keyBindId, record.payload());
        }
        if (record.payload() != null && !record.payload().isEmpty()) {
            Platform.getNetwork().sendPayloadPacketToServer(record.payload(), "[]");
        }
    }

    private static void pollManagerKey() {
        if (managerKey == null) { return; }
        boolean clicked = false;
        while (managerKey.consumeClick()) {
            clicked = true;
        }
        if (clicked && Minecraft.getInstance().screen == null) {
            Minecraft.getInstance()
                    .setScreen(new com.luatweaker.common.client.luamods.LuamodListScreen(null));
        }
    }

    private static boolean isPhysicallyDown(KeyMapping mapping) {
        return mapping.isDown();
    }

    private static boolean modifierHeld(long window, String modifier) {
        if (MODIFIER_SHIFT.equalsIgnoreCase(modifier)) {
            return InputConstants.isKeyDown(window, GLFW_LEFT_SHIFT)
                    || InputConstants.isKeyDown(window, GLFW_RIGHT_SHIFT);
        }
        if (MODIFIER_CONTROL.equalsIgnoreCase(modifier) || "ctrl".equalsIgnoreCase(modifier)) {
            return InputConstants.isKeyDown(window, GLFW_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW_RIGHT_CONTROL);
        }
        if (MODIFIER_ALT.equalsIgnoreCase(modifier)) {
            return InputConstants.isKeyDown(window, GLFW_LEFT_ALT)
                    || InputConstants.isKeyDown(window, GLFW_RIGHT_ALT);
        }
        return true;
    }
}
