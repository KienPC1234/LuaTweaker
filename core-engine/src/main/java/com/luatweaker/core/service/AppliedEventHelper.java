package com.luatweaker.core.service;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

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
        } catch (LuaException | IllegalStateException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                "[AppliedEvent] Failed to fire '" + eventName + "': " + e.getMessage());
        }
    }

    private static void setField(@NotNull ILuaEngine engine, @NotNull ILuaTable payload,
                                 @NotNull String key, @Nullable Object value) {
        if (value == null) {
            return;
        }
        ILuaValue encoded = encodeScalar(engine, value);
        if (encoded != null) {
            payload.rawset(key, encoded);
            return;
        }
        if (value instanceof List<?> list) {
            payload.rawset(key, encodeList(engine, list));
            return;
        }
        LuaTweakerLog.get().warn(LogStage.SYSTEM,
            "[AppliedEvent] Unsupported payload type for '" + key + "': "
                + value.getClass().getSimpleName() + " (skipped).");
    }

    /**
     * @return the Lua scalar for a String/Boolean/Number, or {@code null} when
     *         the value is not a scalar (lists are encoded separately).
     */
    private static @Nullable ILuaValue encodeScalar(@NotNull ILuaEngine engine, @NotNull Object value) {
        if (value instanceof String text) {
            return engine.wrapString(text);
        }
        if (value instanceof Boolean flag) {
            return engine.wrapBoolean(flag);
        }
        if (value instanceof Number number) {
            return engine.wrapNumber(number.doubleValue());
        }
        return null;
    }

    private static @NotNull ILuaTable encodeList(@NotNull ILuaEngine engine, @NotNull List<?> list) {
        ILuaTable array = engine.createTable();
        int index = 1;
        for (Object item : list) {
            ILuaValue encoded = item == null ? null : encodeScalar(engine, item);
            if (encoded != null) {
                array.rawset(index++, encoded);
            }
        }
        return array;
    }
}
