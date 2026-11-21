package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the Lua payload for a forwarded NeoForge event. The per-domain builders live in
 * small collaborators registered into {@link PayloadRegistry}; this class only owns the wiring.
 */
public final class EventPayloadBuilder {

    private static final PayloadRegistry REGISTRY = createRegistry();

    private EventPayloadBuilder() {
    }

    public static @Nullable ILuaTable buildPayload(@NotNull ILuaEngine engine, @NotNull Event event) {
        return REGISTRY.build(engine, event);
    }

    private static PayloadRegistry createRegistry() {
        PayloadRegistry registry = new PayloadRegistry();
        CombatPayloads.register(registry);
        EntityPayloads.register(registry);
        PlayerPayloads.register(registry);
        MiningPayloads.register(registry);
        LevelPayloads.register(registry);
        ExplosionPayloads.register(registry);
        return registry;
    }
}
