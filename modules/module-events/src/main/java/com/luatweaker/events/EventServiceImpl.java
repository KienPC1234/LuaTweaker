package com.luatweaker.events;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventServiceImpl implements IEventService {
    private final ILuaEngine engine;
    private final Map<String, List<Object>> listeners = new ConcurrentHashMap<>();

    public EventServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void listen(@NotNull String eventName, @NotNull Object callback) {
        listeners.computeIfAbsent(eventName, k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    @Override
    public void post(@NotNull String eventName, @NotNull ILuaTable payload) {
        fireEvent(eventName, payload);
    }

    @Override
    public void fireEvent(@NotNull String eventName, @NotNull ILuaTable payload) {
        List<Object> callbacks = listeners.get(eventName);
        if (callbacks != null) {
            for (Object cb : callbacks) {
                if (cb instanceof ILuaValue lv) {
                    try {
                        engine.callFunction(lv, payload);
                    } catch (Exception e) {
                        com.luatweaker.api.log.LuaTweakerLog.get().error(
                            com.luatweaker.api.log.LogStage.SCRIPT_LOAD,
                            "Error invoking event listener for " + eventName + ": " + e.getMessage()
                        );
                    }
                }
            }
        }
    }
}
