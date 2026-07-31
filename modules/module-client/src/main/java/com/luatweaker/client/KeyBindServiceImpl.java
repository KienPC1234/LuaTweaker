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

    @Override
    public void registerKeyBind(String id, String displayName, String category, int defaultKey, String onPressPayload) {
        if (id == null || id.isBlank()) return;
        String name = (displayName == null || displayName.isBlank()) ? id : displayName;
        String cat = (category == null || category.isBlank()) ? "LuaTweaker" : category;
        String payload = (onPressPayload == null) ? "" : onPressPayload;
        keyBinds.put(id, new KeyBindEntry(id, name, cat, defaultKey, payload));
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
    public void setKeyBindListener(BiConsumer<String, String> listener) {
        this.listener = listener;
    }
}
