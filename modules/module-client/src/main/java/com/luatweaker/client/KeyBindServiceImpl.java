package com.luatweaker.client;

import com.luatweaker.api.client.IKeyBindService;
import com.luatweaker.api.client.KeyNames;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public class KeyBindServiceImpl implements IKeyBindService {

    private static final String DEFAULT_CATEGORY = "LuaTweaker";
    private static final String INPUT_KEY = "key";
    private static final String INPUT_MOUSE = "mouse";

    private final Map<String, KeyBindEntry> keyBinds = new ConcurrentHashMap<>();
    private final AtomicReference<BiConsumer<String, String>> listener = new AtomicReference<>();
    private final AtomicReference<BiConsumer<String, String>> releaseListener = new AtomicReference<>();
    private final AtomicLong registrationVersion = new AtomicLong();

    @Override
    public void registerKeyBind(String id, String displayName, String category, int defaultKey,
                                String onPressPayload) {
        registerKeyBind(id, displayName, category, defaultKey, onPressPayload, INPUT_KEY, "none");
    }

    @Override
    public void registerKeyBind(String id, String displayName, String category, int defaultKey,
                                String onPressPayload, String inputType, String modifier) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("keybind id must not be blank");
        }
        String type = normalizeType(id, inputType);
        String name = defaultName(id, displayName);
        String cat = defaultCategory(category);
        String payload = onPressPayload == null ? "" : onPressPayload;
        String mod = KeyNames.parseModifier(modifier);
        keyBinds.put(id, new KeyBindEntry(id, name, cat, defaultKey, payload, type, mod));
        registrationVersion.incrementAndGet();
    }

    @Override
    public long getRegistrationVersion() {
        return registrationVersion.get();
    }

    private static String defaultName(String id, String displayName) {
        return displayName == null || displayName.isBlank() ? id : displayName;
    }

    private static String defaultCategory(String category) {
        return category == null || category.isBlank() ? DEFAULT_CATEGORY : category;
    }

    private static String normalizeType(String id, String inputType) {
        String type = inputType == null || inputType.isBlank()
                ? INPUT_KEY : inputType.strip().toLowerCase(Locale.ROOT);
        if (!INPUT_KEY.equals(type) && !INPUT_MOUSE.equals(type)) {
            throw new IllegalArgumentException("keybind '" + id
                    + "' inputType must be 'key' or 'mouse', got: " + inputType);
        }
        return type;
    }

    @Override
    public List<KeyBindEntry> getRegisteredKeyBinds() {
        return new ArrayList<>(keyBinds.values());
    }

    @Override
    public void triggerKeyBind(String id, String payload) {
        BiConsumer<String, String> l = listener.get();
        if (l != null && id != null) {
            l.accept(id, payload != null ? payload : "");
        }
    }

    @Override
    public void triggerKeyBindRelease(String id, String payload) {
        BiConsumer<String, String> l = releaseListener.get();
        if (l != null && id != null) {
            l.accept(id, payload != null ? payload : "");
        }
    }

    @Override
    public void setKeyBindListener(BiConsumer<String, String> listener) {
        this.listener.set(listener);
    }

    @Override
    public void setKeyBindReleaseListener(BiConsumer<String, String> listener) {
        this.releaseListener.set(listener);
    }
}
