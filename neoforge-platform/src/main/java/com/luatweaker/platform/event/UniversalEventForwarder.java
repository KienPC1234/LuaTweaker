package com.luatweaker.platform.event;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.DynamicJavaProxy;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.LuaTweakerMod;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import org.squiddev.cobalt.LuaState;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Replaces hardcoded @SubscribeEvent methods.
 * Dynamically hooks into the NeoForge event bus based on the EventAliasRegistry
 * and forwards the events as DynamicJavaProxy objects to Lua.
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
        
        IEventService eventService = (IEventService) LuaServiceRegistry.get("Events");
        if (eventService != null) {
            // Retrieve the underlying LuaState to create the proxy
            // CobaltLuaEngine wraps it. We can pass the raw object to CoerceJavaToLua if we override it,
            // or we use our DynamicJavaProxy directly.
            // Since EventService is expected to receive an ILuaTable payload in the old architecture,
            // we will modify EventService to accept ILuaValue (which DynamicJavaProxy implements).
            
            if (engine instanceof com.luatweaker.core.vm.CobaltLuaEngine cobaltEngine) {
                LuaState state = cobaltEngine.getCobaltState();
                
                // Wrap the Java event in our shiny new proxy
                org.squiddev.cobalt.LuaUserdata proxy = DynamicJavaProxy.create(state, event);
                
                // Fire it to Lua scripts listening to this alias
                // E.g. Events:Listen("EntityHurt", ...)
                ((com.luatweaker.events.EventServiceImpl)eventService).fireRawEvent(alias, new com.luatweaker.core.vm.CobaltLuaValue(proxy));
            }
        }
    }
}
