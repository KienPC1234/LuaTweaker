package com.luatweaker.events;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.*;
import org.jetbrains.annotations.NotNull;

public class EventLuaBinding {
    public static void registerBindings(@NotNull ILuaEngine engine) {
        IEventService service = new EventServiceImpl(engine);
        ILuaTable table = engine.createTable();

        table.rawset("listen", args -> {
            if (args.length < 3) {
                throw new IllegalArgumentException("events:listen requires (eventName, callback)");
            }
            String eventName = args[1].asString();
            ILuaValue callback = args[2];
            service.listen(eventName, callback);
            return null;
        });

        table.rawset("post", args -> {
            if (args.length < 3) {
                throw new IllegalArgumentException("events:post requires (eventName, payload)");
            }
            String eventName = args[1].asString();
            ILuaTable payload = args[2].asTable();
            service.post(eventName, payload);
            return null;
        });

        engine.registerService("Events", service);
        engine.registerGlobal("events", table);
    }

    public static ILuaTable wrapEventPayload(@NotNull ILuaEngine engine, @NotNull ILuaTable payload, @NotNull Runnable cancelAction) {
        payload.rawset("Cancel", args -> {
            cancelAction.run();
            payload.rawset("Cancelled", engine.wrapBoolean(true));
            return engine.nilValue();
        });
        payload.rawset("Cancelled", engine.wrapBoolean(false));
        return payload;
    }
}
