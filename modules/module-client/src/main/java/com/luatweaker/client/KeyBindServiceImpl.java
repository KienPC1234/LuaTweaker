package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class KeyBindServiceImpl implements IKeyBindService {
    private final Map<String, KeyBindEntry> keyBinds = new ConcurrentHashMap<>();
    private volatile BiConsumer<String, String> listener;
    private volatile BiConsumer<String, String> releaseListener;

    @Override
    public void registerKeyBind(String id, String displayName, String category, int defaultKey, String onPressPayload) {
        registerKeyBind(id, displayName, category, defaultKey, onPressPayload, "key", "none");
    }

    @Override
    public void registerKeyBind(String id, String displayName, String category, int defaultKey,
                                String onPressPayload, String inputType, String modifier) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("keybind id must not be blank");
        }
        String name = (displayName == null || displayName.isBlank()) ? id : displayName;
        String cat = (category == null || category.isBlank()) ? "LuaTweaker" : category;
        String payload = (onPressPayload == null) ? "" : onPressPayload;
        String type = (inputType == null || inputType.isBlank()) ? "key" : inputType.strip().toLowerCase();
        if (!type.equals("key") && !type.equals("mouse")) {
            throw new IllegalArgumentException("keybind '" + id + "' inputType must be 'key' or 'mouse', got: " + inputType);
        }
        String mod = com.luatweaker.api.client.KeyNames.parseModifier(modifier);
        keyBinds.put(id, new KeyBindEntry(id, name, cat, defaultKey, payload, type, mod));
    }

    @Override
    public List<KeyBindEntry> getRegisteredKeyBinds() {
        return new ArrayList<>(keyBinds.values());
    }

    @Override
    public void triggerKeyBind(String id, String payload) {
        BiConsumer<String, String> l = this.listener;
        if (l != null && id != null) {
            l.accept(id, payload != null ? payload : "");
        }
    }

    @Override
    public void triggerKeyBindRelease(String id, String payload) {
        BiConsumer<String, String> l = this.releaseListener;
        if (l != null && id != null) {
            l.accept(id, payload != null ? payload : "");
        }
    }

    @Override
    public void setKeyBindListener(BiConsumer<String, String> listener) {
        this.listener = listener;
    }

    @Override
    public void setKeyBindReleaseListener(BiConsumer<String, String> listener) {
        this.releaseListener = listener;
    }
}
