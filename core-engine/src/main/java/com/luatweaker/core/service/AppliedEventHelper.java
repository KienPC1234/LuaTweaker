package com.luatweaker.core.service;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Fires engine-agnostic notification events (e.g. {@code WorldgenApplied})
 * from platform providers and services without duplicating the
 * registry-lookup + payload-building glue per caller.
 *
 * <p>Never throws: a missing event bus (unit tests, early startup) only logs,
 * and a failing listener path is reported with the event name.
 */
public final class AppliedEventHelper {
    private AppliedEventHelper() {}

    /** Fires {@code eventName} with a flat payload built from {@code fields}. */
    public static void fire(@NotNull String eventName, @Nullable Map<String, ?> fields) {
        if (eventName == null || eventName.isBlank()) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                "[AppliedEvent] Refused event with blank name.");
            return;
        }
        Object service = LuaServiceRegistry.get("Events");
        if (!(service instanceof IEventService events)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "[AppliedEvent] No event bus registered; skipped '" + eventName + "'.");
            return;
        }
        try {
            ILuaEngine engine = events.getEngine();
            ILuaTable payload = engine.createTable();
            if (fields != null) {
                for (Map.Entry<String, ?> field : fields.entrySet()) {
                    if (field.getKey() != null) {
                        setField(engine, payload, field.getKey(), field.getValue());
                    }
                }
            }
            events.fireEvent(eventName, payload);
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                "[AppliedEvent] Failed to fire '" + eventName + "': " + e.getMessage());
        }
    }

    private static void setField(@NotNull ILuaEngine engine, @NotNull ILuaTable payload,
                                 @NotNull String key, @Nullable Object value) {
        if (value == null) {
            return;
        } else if (value instanceof String text) {
            payload.rawset(key, engine.wrapString(text));
        } else if (value instanceof Boolean flag) {
            payload.rawset(key, engine.wrapBoolean(flag));
        } else if (value instanceof Number number) {
            payload.rawset(key, engine.wrapNumber(number.doubleValue()));
        } else if (value instanceof List<?> list) {
            ILuaTable array = engine.createTable();
            int index = 1;
            for (Object item : list) {
                if (item instanceof String text) {
                    array.rawset(index++, engine.wrapString(text));
                } else if (item instanceof Number number) {
                    array.rawset(index++, engine.wrapNumber(number.doubleValue()));
                } else if (item instanceof Boolean flag) {
                    array.rawset(index++, engine.wrapBoolean(flag));
                }
            }
            payload.rawset(key, array);
        } else {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                "[AppliedEvent] Unsupported payload type for '" + key + "': "
                    + value.getClass().getSimpleName() + " (skipped).");
        }
    }
}
