package com.luatweaker.events;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.*;
import org.jetbrains.annotations.NotNull;

public class EventLuaBinding {
    public static void registerBindings(@NotNull ILuaEngine engine) {
        IEventService service = new EventServiceImpl(engine);
        ILuaTable table = engine.createTable();

        table.rawset("listen", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 2) {
                throw new IllegalArgumentException("events:listen requires (eventName, callback)");
            }
            String eventName = args[off].asString();
            ILuaValue callback = args[off + 1];
            service.listen(eventName, callback);
            return engine.nilValue();
        });
        table.rawset("Listen", table.rawget("listen"));
        table.rawset("Connect", table.rawget("listen"));
        table.rawset("connect", table.rawget("listen"));

        table.rawset("post", args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off < 2) {
                throw new IllegalArgumentException("events:post requires (eventName, payload)");
            }
            String eventName = args[off].asString();
            ILuaTable payload = args[off + 1].asTable();
            service.post(eventName, payload);
            return engine.nilValue();
        });
        table.rawset("Post", table.rawget("post"));
        table.rawset("Fire", table.rawget("post"));
        table.rawset("fire", table.rawget("post"));

        engine.registerService("Events", service);
        engine.registerGlobal("events", table);
        engine.registerGlobal("Events", table);
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
