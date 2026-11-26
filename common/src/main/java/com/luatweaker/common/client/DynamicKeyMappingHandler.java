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
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicKeyMappingHandler {
    private static final String KEYBIND_SERVICE_NAME = "KeyBindService";

    private static final Map<String, KeyMappingState> REGISTERED_MAPPINGS = new ConcurrentHashMap<>();

    enum KeyModifier {
        NONE, SHIFT, CTRL, ALT
    }

    private static final class KeyMappingState {
        final KeyMapping mapping;
        String payload;
        KeyModifier modifier;
        boolean wasDown;

        KeyMappingState(KeyMapping mapping, String payload, KeyModifier modifier) {
            this.mapping = mapping;
            this.payload = payload;
            this.modifier = modifier;
        }
    }

    private static KeyMapping managerKey;
    private static IKeyBindService syncedService;
    private static long syncedVersion = -1L;

    private static final int GLFW_LEFT_SHIFT = 340;
    private static final int GLFW_RIGHT_SHIFT = 344;
    private static final int GLFW_LEFT_CONTROL = 341;
    private static final int GLFW_RIGHT_CONTROL = 345;
    private static final int GLFW_LEFT_ALT = 342;
    private static final int GLFW_RIGHT_ALT = 347;
    private static final long NO_WINDOW = 0L;

    private DynamicKeyMappingHandler() {
    }

    public static void init() {
        managerKey = new KeyMapping("key.luatweaker.manager", InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(), "key.categories.luatweaker");
        KeyMappingRegistry.register(managerKey);

        Object service = LuaServiceRegistry.get(KEYBIND_SERVICE_NAME);
        if (service instanceof IKeyBindService keyBindService) {
            for (IKeyBindService.KeyBindEntry entry : keyBindService.getRegisteredKeyBinds()) {
                KeyMappingState state = createState(entry);
                KeyMappingRegistry.register(state.mapping);
                com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[KeyMapping] Registered Dynamic KeyBinding with Minecraft Controls: '"
                                + state.mapping.getName() + "' (ID: " + entry.id() + ")"
                );
            }
            syncedService = keyBindService;
            syncedVersion = keyBindService.getRegistrationVersion();
        }

        ClientTickEvent.CLIENT_POST.register(mc -> onClientTick());
    }

    private static KeyMappingState createState(IKeyBindService.KeyBindEntry entry) {
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
        KeyMappingState state = new KeyMappingState(
                keyMapping, entry.onPressPayload(), parseModifier(entry.modifier()));
        KeyMappingState previous = REGISTERED_MAPPINGS.put(entry.id(), state);
        if (previous != null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[KeyMapping] Duplicate keybind id '" + entry.id()
                            + "' overwritten - mods must use unique ids to avoid collisions"
            );
        }
        return state;
    }

    static KeyModifier parseModifier(@Nullable String modifier) {
        if (modifier == null || modifier.isBlank()) {
            return KeyModifier.NONE;
        }
        return switch (modifier.strip().toLowerCase(Locale.ROOT)) {
            case "shift" -> KeyModifier.SHIFT;
            case "ctrl" -> KeyModifier.CTRL;
            case "alt" -> KeyModifier.ALT;
            default -> KeyModifier.NONE;
        };
    }

    static void syncLateKeyBinds(@NotNull IKeyBindService keyBindService) {
        long version = keyBindService.getRegistrationVersion();
        if (keyBindService.equals(syncedService) && version >= 0 && version == syncedVersion) {
            return;
        }
        java.util.List<IKeyBindService.KeyBindEntry> registered = keyBindService.getRegisteredKeyBinds();
        refreshExistingMappings(registered);
        for (IKeyBindService.KeyBindEntry entry : collectUnsynced(registered, REGISTERED_MAPPINGS.keySet())) {
            createState(entry);
            com.luatweaker.api.log.LuaTweakerLog.get().info(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[KeyMapping] Late keybind '" + entry.id() + "' active with default key "
                            + entry.defaultKey() + " (Controls GUI listing + rebinding needs a restart)"
            );
        }
        syncedService = keyBindService;
        syncedVersion = version;
    }

    private static void refreshExistingMappings(java.util.List<IKeyBindService.KeyBindEntry> entries) {
        for (IKeyBindService.KeyBindEntry entry : entries) {
            if (entry == null || entry.id() == null) {
                continue;
            }
            KeyMappingState existing = REGISTERED_MAPPINGS.get(entry.id());
            if (existing == null) {
                continue;
            }
            KeyModifier updatedModifier = parseModifier(entry.modifier());
            if (shouldRefreshExisting(existing.payload, existing.modifier, entry.onPressPayload(), updatedModifier)) {
                existing.payload = entry.onPressPayload();
                existing.modifier = updatedModifier;
            }
        }
    }

    static boolean shouldRefreshExisting(@Nullable String oldPayload, @NotNull KeyModifier oldModifier,
                                         @Nullable String newPayload, @NotNull KeyModifier newModifier) {
        return !java.util.Objects.equals(oldPayload, newPayload) || oldModifier != newModifier;
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
        Object service = LuaServiceRegistry.get(KEYBIND_SERVICE_NAME);
        IKeyBindService keyBindService = service instanceof IKeyBindService kbs ? kbs : null;
        if (keyBindService != null) {
            syncLateKeyBinds(keyBindService);
        }
        pollManagerKey();
        if (REGISTERED_MAPPINGS.isEmpty()) {
            return;
        }
        long window = windowHandle();
        if (window == NO_WINDOW) {
            return;
        }
        boolean hasScreen = Minecraft.getInstance().screen != null;
        for (Map.Entry<String, KeyMappingState> mapEntry : REGISTERED_MAPPINGS.entrySet()) {
            handleMapping(window, hasScreen, keyBindService, mapEntry.getKey(), mapEntry.getValue());
        }
    }

    private static long windowHandle() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow() != null ? mc.getWindow().getWindow() : NO_WINDOW;
    }

    private static void handleMapping(long window, boolean hasScreen,
                                      @Nullable IKeyBindService keyBindService,
                                      @NotNull String keyBindId,
                                      @NotNull KeyMappingState state) {
        boolean physDown = state.mapping.isDown();
        boolean wasDown = state.wasDown;
        state.wasDown = physDown;
        if (!physDown) {
            if (wasDown && keyBindService != null) {
                keyBindService.triggerKeyBindRelease(keyBindId, state.payload);
            }
            return;
        }
        if (wasDown || hasScreen || !modifierHeld(window, state.modifier) || !Platform.isInitialized()) {
            return;
        }
        triggerPress(keyBindService, keyBindId, state);
    }

    private static void triggerPress(@Nullable IKeyBindService keyBindService,
                                     @NotNull String keyBindId,
                                     @NotNull KeyMappingState state) {
        if (keyBindService != null) {
            keyBindService.triggerKeyBind(keyBindId, state.payload);
        }
        if (state.payload != null && !state.payload.isEmpty()) {
            Platform.getNetwork().sendPayloadPacketToServer(state.payload, "[]");
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

    private static boolean modifierHeld(long window, KeyModifier modifier) {
        return switch (modifier) {
            case SHIFT -> InputConstants.isKeyDown(window, GLFW_LEFT_SHIFT)
                    || InputConstants.isKeyDown(window, GLFW_RIGHT_SHIFT);
            case CTRL -> InputConstants.isKeyDown(window, GLFW_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW_RIGHT_CONTROL);
            case ALT -> InputConstants.isKeyDown(window, GLFW_LEFT_ALT)
                    || InputConstants.isKeyDown(window, GLFW_RIGHT_ALT);
            case NONE -> true;
        };
    }
}
