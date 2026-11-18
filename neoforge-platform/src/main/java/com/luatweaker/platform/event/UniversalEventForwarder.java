package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.LuaTweakerMod;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Replaces hardcoded @SubscribeEvent methods.
 * Dynamically hooks into the NeoForge event bus based on the EventAliasRegistry
 * and forwards each event as an engine-neutral dynamically wrapped Lua value.
 */
public class UniversalEventForwarder {

    public static void registerAll() {
        Map<String, Class<? extends Event>> aliases = EventAliasRegistry.getAllAliases();

        for (Map.Entry<String, Class<? extends Event>> entry : aliases.entrySet()) {
            String alias = entry.getKey();
            Class<? extends Event> eventClass = entry.getValue();

            // Register a dynamic listener on the NeoForge event bus
            registerListener(eventClass, alias);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerListener(Class<? extends Event> eventClass, String alias) {
        Class rawClass = eventClass;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, rawClass, (Consumer<Event>) event -> {
            handleEvent(alias, event);
        });
    }

    private static void handleEvent(String alias, Event event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) return;

        if (LuaServiceRegistry.get("Events") instanceof com.luatweaker.events.EventServiceImpl eventService) {
            ILuaValue wrapped = engine.wrapDynamicJavaObject(event);
            eventService.fireRawEvent(alias, wrapped);
        }
    }
}
