package com.luatweaker.common.event;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class EventDispatcher {

    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("LivingHurt", List.of("EntityHurt")),
            Map.entry("LivingDeath", List.of("EntityDeath")),
            Map.entry("PlayerQuit", List.of("PlayerLeave")),
            Map.entry("PlayerChangedDimension", List.of("EntityChangeDimension")),
            Map.entry("RightClickBlock", List.of("BlockInteract", "PlayerInteractBlock")),
            Map.entry("InteractEntity", List.of("PlayerInteractEntity", "EntityInteract")),
            Map.entry("AttackEntity", List.of("PlayerAttackEntity")),
            Map.entry("Explosion", List.of("ExplosionStart")),
            Map.entry("ExplosionDetonate", List.of("ExplosionPost")),
            Map.entry("LevelLoad", List.of("ServerLevelLoad")),
            Map.entry("LevelUnload", List.of("ServerLevelUnload")),
            Map.entry("ContainerOpen", List.of("PlayerContainerOpen")),
            Map.entry("ContainerClose", List.of("PlayerContainerClose")),
            Map.entry("EntitySpawn", List.of("EntityJoinLevel")),
            Map.entry("ItemPickup", List.of("ItemPickupPost")),
            Map.entry("ItemToss", List.of("ItemDrop"))
    );

    private EventDispatcher() {
    }

    public static void post(@NotNull String eventName, @Nullable ILuaTable payload) {
        Object rawEvents = LuaServiceRegistry.get("Events");
        if (!(rawEvents instanceof IEventService events)) {
            return;
        }
        ILuaTable data = resolvePayload(payload);
        if (data == null) {
            return;
        }
        events.post(eventName, data);
        for (String alias : ALIASES.getOrDefault(eventName, Collections.emptyList())) {
            events.post(alias, data);
        }
    }

    private static @Nullable ILuaTable resolvePayload(@Nullable ILuaTable payload) {
        if (payload != null) {
            return payload;
        }
        ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
        return engine != null ? engine.createTable() : null;
    }
}
