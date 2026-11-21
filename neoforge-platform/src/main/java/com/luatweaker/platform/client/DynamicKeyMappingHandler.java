package com.luatweaker.platform.client;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

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

    private record KeyMappingRecord(KeyMapping mapping, String payload) {}

    /**
     * Built-in shortcut for the in-game LuaMod manager. Unbound by default so
     * it never steals a key; users bind it in Controls. Polled in
     * {@link #onClientTick} when no screen is open (pause/title menus already
     * carry the LT shortcut button).
     */
    private static KeyMapping managerKey;

    /**
     * GLFW modifier codes (verified in LWJGL 3.3.3): left/right variants are
     * OR-ed so either Shift/Ctrl/Alt satisfies the combo. PRESS = 1 for
     * {@code glfwGetMouseButton}.
     */
    private static final int GLFW_LEFT_SHIFT = 340;
    private static final int GLFW_RIGHT_SHIFT = 344;
    private static final int GLFW_LEFT_CONTROL = 341;
    private static final int GLFW_RIGHT_CONTROL = 345;
    private static final int GLFW_LEFT_ALT = 342;
    private static final int GLFW_RIGHT_ALT = 347;
    private static final int GLFW_PRESS = 1;

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        managerKey = new KeyMapping("key.luatweaker.manager", KeyConflictContext.UNIVERSAL,
                KeyModifier.NONE, InputConstants.UNKNOWN, "key.categories.luatweaker");
        event.register(managerKey);
        Object service = LuaServiceRegistry.get(KEYBIND_SERVICE_NAME);
        if (service instanceof IKeyBindService keyBindService) {
            for (IKeyBindService.KeyBindEntry entry : keyBindService.getRegisteredKeyBinds()) {
                KeyMappingRecord record = createRecord(entry);
                event.register(record.mapping());
                com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[KeyMapping] Registered Dynamic KeyBinding with Minecraft Controls: '"
                                + record.mapping().getName() + "' (ID: " + entry.id() + ")"
                );
            }
        }
    }

    /**
     * Builds one tick-pollable mapping record. Single construction site shared by
     * the setup-time event path and the late-bind sync below (was copy-pasted).
     */
    private static KeyMappingRecord createRecord(IKeyBindService.KeyBindEntry entry) {
        // Display name and category are used VERBATIM: Minecraft's translatable
        // component falls back to the raw string when no lang entry exists, so a
        // mod can declare a plain label ("Ruby Mod Controls") and it shows as-is.
        // Lang entries are optional — they only override the label (i18n).
        String descKey = (entry.displayName() != null && !entry.displayName().isBlank())
                ? entry.displayName() : entry.id();
        String categoryKey = (entry.category() != null && !entry.category().isBlank())
                ? entry.category() : "LuaTweaker";
        InputConstants.Type type = "mouse".equals(entry.inputType())
                ? InputConstants.Type.MOUSE : InputConstants.Type.KEYSYM;
        KeyMapping keyMapping = new KeyMapping(
                descKey,
                KeyConflictContext.UNIVERSAL,
                toKeyModifier(entry.modifier()),
                type,
                entry.defaultKey(),
                categoryKey
        );
        KeyMappingRecord previous = REGISTERED_MAPPINGS.put(entry.id(),
                new KeyMappingRecord(keyMapping, entry.onPressPayload()));
        if (previous != null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[KeyMapping] Duplicate keybind id '" + entry.id() + "' overwritten — mods must use unique ids to avoid collisions"
            );
        }
        return REGISTERED_MAPPINGS.get(entry.id());
    }

    /**
     * Maps the validated service modifier to the vanilla enum. The service
     * normalizes unknown values at registration, so this is a total function.
     */
    private static KeyModifier toKeyModifier(String modifier) {
        if (MODIFIER_SHIFT.equals(modifier)) {
            return KeyModifier.SHIFT;
        }
        if (MODIFIER_CONTROL.equals(modifier)) {
            return KeyModifier.CONTROL;
        }
        if (MODIFIER_ALT.equals(modifier)) {
            return KeyModifier.ALT;
        }
        return KeyModifier.NONE;
    }

    /**
     * Picks up keybinds registered after {@link #onRegisterKeyMappings} ran
     * (that Forge event fires once at startup, so a {@code /lt reload} that
     * adds keybinds would otherwise leave them dead). Late binds are polled by
     * the same edge-trigger below using their default key; they do NOT appear
     * in the Controls GUI and cannot be rebound until a restart — this is
     * logged loudly per bind, once.
     *
     * <p>Runs on the client tick (same thread that polls the mappings), so no
     * cross-thread map hazards. Cost when stable: one service lookup plus a
     * contains-check per entry per tick.
     */
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

    /**
     * Pure diff: entries whose ids have no live mapping yet. Unit-testable seam
     * for the reload path (the mapping construction itself needs Minecraft).
     */
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

    public static void onClientTick(ClientTickEvent.Post event) {
        syncLateKeyBinds();
        pollManagerKey();
        long window = windowHandle();
        // Vanilla parity: physical keys never reach mappings while any screen is
        // open (KeyboardHandler/MouseHandler skip set+click unless screen == null).
        boolean hasScreen = net.minecraft.client.Minecraft.getInstance().screen != null;
        for (Map.Entry<String, KeyMappingRecord> mapEntry : REGISTERED_MAPPINGS.entrySet()) {
            handleMapping(window, hasScreen, mapEntry.getKey(), mapEntry.getValue());
        }
    }

    private static long windowHandle() {
        return net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
    }

    /**
     * Edge-triggers one key bind from its PHYSICAL state. Physical state (not
     * {@code consumeClick()}) is used because consumeClick would replay every queued
     * press while a key is HELD via OS auto-repeat, flooding the server with
     * duplicate packets.
     */
    private static void handleMapping(long window, boolean hasScreen, @org.jetbrains.annotations.NotNull String keyBindId,
                                      @org.jetbrains.annotations.NotNull KeyMappingRecord record) {
        boolean physDown = isPhysicallyDown(window, record.mapping());
        boolean wasDown = Boolean.TRUE.equals(LAST_DOWN_STATE.getOrDefault(keyBindId, false));
        LAST_DOWN_STATE.put(keyBindId, physDown);
        if (!physDown) {
            // Release is physical truth and fires even with a GUI open: the key genuinely came up.
            if (wasDown) {
                triggerRelease(keyBindId, record);
            }
            return;
        }
        // Combos only count when the modifier is already held as the main key goes down (same as
        // most games; holding X then Shift will not retro-fire). Missing modifiers are a
        // service-validated per-mapping field, read live so Controls-GUI rebinding just works.
        if (wasDown || hasScreen || !modifierHeld(window, record.mapping()) || !Platform.isInitialized()) {
            return;
        }
        triggerPress(keyBindId, record);
    }

    private static void triggerRelease(@org.jetbrains.annotations.NotNull String keyBindId,
                                       @org.jetbrains.annotations.NotNull KeyMappingRecord record) {
        Object service = LuaServiceRegistry.get(KEYBIND_SERVICE_NAME);
        if (service instanceof IKeyBindService keyBindService) {
            keyBindService.triggerKeyBindRelease(keyBindId, record.payload());
        }
    }

    private static void triggerPress(@org.jetbrains.annotations.NotNull String keyBindId,
                                     @org.jetbrains.annotations.NotNull KeyMappingRecord record) {
        com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[KeyMapping] KeyMapping Triggered: '" + keyBindId + "' (Payload: " + record.payload() + ")"
        );
        // 1. Trigger Client-side Lua Signal by KeyMapping ID
        Object service = LuaServiceRegistry.get(KEYBIND_SERVICE_NAME);
        if (service instanceof IKeyBindService keyBindService) {
            keyBindService.triggerKeyBind(keyBindId, record.payload());
        }
        // 2. If payload is present, also send payload packet to server
        if (record.payload() != null && !record.payload().isEmpty()) {
            Platform.getNetwork().sendPayloadPacketToServer(record.payload(), "[]");
        }
    }

    /**
     * Drains the manager shortcut every tick and opens the manager only when
     * no screen is open. Draining unconditionally (instead of only when
     * screenless) drops presses typed into chat/config boxes so they never
     * replay when the screen closes.
     */
    private static void pollManagerKey() {
        if (managerKey == null) { return; }
        boolean clicked = false;
        while (managerKey.consumeClick()) {
            clicked = true;
        }
        if (clicked && net.minecraft.client.Minecraft.getInstance().screen == null) {
            net.minecraft.client.Minecraft.getInstance()
                    .setScreen(new com.luatweaker.platform.client.luamods.LuamodListScreen(null));
        }
    }

    /**
     * Physical button state. Keyboard goes through GLFW directly (vanilla
     * {@code InputConstants.isKeyDown}); mouse buttons need
     * {@code glfwGetMouseButton} since {@code isKeyDown} is keysym-only.
     */
    private static boolean isPhysicallyDown(long window, KeyMapping mapping) {
        if (mapping.getKey().getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, mapping.getKey().getValue()) == GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, mapping.getKey().getValue());
    }

    /**
     * Whether the mapping's live modifier (rebindable in the Controls GUI) is
     * currently held. Either left or right variant satisfies the combo.
     */
    private static boolean modifierHeld(long window, KeyMapping mapping) {
        KeyModifier modifier = mapping.getKeyModifier();
        if (KeyModifier.SHIFT.equals(modifier)) {
            return InputConstants.isKeyDown(window, GLFW_LEFT_SHIFT)
                    || InputConstants.isKeyDown(window, GLFW_RIGHT_SHIFT);
        }
        if (KeyModifier.CONTROL.equals(modifier)) {
            return InputConstants.isKeyDown(window, GLFW_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW_RIGHT_CONTROL);
        }
        if (KeyModifier.ALT.equals(modifier)) {
            return InputConstants.isKeyDown(window, GLFW_LEFT_ALT)
                    || InputConstants.isKeyDown(window, GLFW_RIGHT_ALT);
        }
        return true;
    }
}
