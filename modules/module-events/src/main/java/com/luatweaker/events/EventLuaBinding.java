package com.luatweaker.events;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;

public final class EventLuaBinding {

    private EventLuaBinding() {}

    public static void registerBindings(@NotNull ILuaEngine engine) {
        IEventService service = new EventServiceImpl(engine);
        ILuaTable table = LuaBinder.bind(engine, "Events", service, IEventService.class, "events");
        StubCatalog.register("Events", IEventService.class);

        // Java-side listeners lookup the service object, not the Lua table.
        engine.registerService("Events", service);

        table.rawset("Connect", table.rawget("Listen"));
        table.rawset("connect", table.rawget("Listen"));
        table.rawset("Fire", table.rawget("Post"));
        table.rawset("fire", table.rawget("Post"));
    }

    public static ILuaTable wrapEventPayload(@NotNull ILuaEngine engine, @NotNull ILuaTable payload, @NotNull Runnable cancelAction) {
        ILuaFunction cancelFn = args -> {
            cancelAction.run();
            payload.rawset("Cancelled", engine.wrapBoolean(true));
            payload.rawset("cancelled", engine.wrapBoolean(true));
            return engine.nilValue();
        };
        payload.rawset("Cancel", cancelFn);
        payload.rawset("cancel", cancelFn);
        payload.rawset("Cancelled", engine.wrapBoolean(false));
        return payload;
    }
}
