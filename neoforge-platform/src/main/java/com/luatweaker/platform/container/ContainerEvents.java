package com.luatweaker.platform.container;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.service.LuaServiceRegistry;

import java.util.function.Function;

/** Fires Lua events from container Java code through the shared event bus. */
final class ContainerEvents {

    private ContainerEvents() {}

    static void post(String eventName, Function<ILuaEngine, ILuaTable> payloadBuilder) {
        Object service = LuaServiceRegistry.get("Events");
        if (service instanceof IEventService events) {
            try {
                events.post(eventName, payloadBuilder.apply(events.getEngine()));
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "Failed to fire container event '" + eventName + "': " + e.getMessage());
            }
        }
    }

    static ILuaTable basePayload(ILuaEngine engine, CustomContainerBlock container, int x, int y, int z) {
        ILuaTable payload = engine.createTable();
        payload.rawset("X", engine.wrapNumber(x));
        payload.rawset("Y", engine.wrapNumber(y));
        payload.rawset("Z", engine.wrapNumber(z));
        payload.rawset("Id", engine.wrapString(container.getContainerId()));
        return payload;
    }
}
