package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyBindServiceImpl implements IKeyBindService {
    private final Map<String, KeyBindEntry> keyBinds = new ConcurrentHashMap<>();

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
}
