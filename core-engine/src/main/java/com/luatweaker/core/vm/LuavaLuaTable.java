package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaFunction;
import org.luava.runtime.LuaNil;
import org.luava.runtime.LuaTable;
import org.luava.runtime.LuaUserdata;
import org.luava.runtime.LuaValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Adapts a luava {@link LuaTable} to the engine-neutral {@link ILuaTable} SPI.
 */
public class LuavaLuaTable extends LuavaLuaValue implements ILuaTable {
    private static final ILuaValue[] EMPTY_ARGS = new ILuaValue[0];
    private final LuaTable table;

    /**
     * Creates an adapter around a luava table.
     *
     * @param table underlying luava table; must not be {@code null}
     */
    public LuavaLuaTable(@NotNull LuaTable table) {
        super(table);
        this.table = table;
    }

    /**
     * Returns the underlying luava table.
     *
     * @return the wrapped luava table
     */
    public @NotNull LuaTable getLuavaTable() {
        return table;
    }

    /**
     * Converts an SPI value back into a luava value. A {@code null} reference maps to nil;
     * a foreign implementation is rejected loudly instead of being silently discarded.
     */
    private static @NotNull LuaValue unwrap(@Nullable ILuaValue value) {
        if (value == null) {
            return LuaNil.NIL;
        }
        if (value instanceof LuavaLuaValue wrapper) {
            return wrapper.getLuavaValue();
        }
        throw new IllegalArgumentException(
                "Expected a LuavaLuaValue but got: " + value.getClass().getName());
    }

    @Override
    public void rawset(@NotNull String key, @NotNull ILuaValue value) {
        table.rawset(LuaValue.valueOf(key), unwrap(value));
    }

    @Override
    public void rawset(@NotNull String key, @NotNull String value) {
        table.rawset(LuaValue.valueOf(key), LuaValue.valueOf(value));
    }

    @Override
    public void rawset(@NotNull String key, double value) {
        table.rawset(LuaValue.valueOf(key), LuaValue.valueOf(value));
    }

    @Override
    public void rawset(@NotNull String key, boolean value) {
        table.rawset(LuaValue.valueOf(key), LuaValue.valueOf(value));
    }

    @Override
    public void rawset(@NotNull String key, @NotNull ILuaFunction function) {
        table.rawset(LuaValue.valueOf(key), LuaFunction.of(args -> {
            ILuaValue[] wrappedArgs;
            if (args.length == 0) {
                wrappedArgs = EMPTY_ARGS;
            } else {
                wrappedArgs = new ILuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    wrappedArgs[i] = wrap(args[i]);
                }
            }
            try {
                ILuaValue result = function.invoke(wrappedArgs);
                return result == null ? LuaNil.NIL : unwrap(result);
            } catch (IllegalArgumentException e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                throw LuaFunction.hostError(message, e);
            }
        }));
    }

    @Override
    public void rawset(@NotNull String key, @Nullable Object userdata) {
        table.rawset(LuaValue.valueOf(key), new LuaUserdata(userdata));
    }

    @Override
    public void rawset(int index, @NotNull ILuaValue value) {
        table.rawsetInt(index, unwrap(value));
    }

    @Override
    public void rawset(int index, @NotNull String value) {
        table.rawsetInt(index, LuaValue.valueOf(value));
    }

    @Override
    public void rawset(int index, double value) {
        table.rawsetInt(index, LuaValue.valueOf(value));
    }

    @Override
    public void rawset(int index, boolean value) {
        table.rawsetInt(index, LuaValue.valueOf(value));
    }

    @Override
    public @NotNull ILuaValue rawget(@NotNull String key) {
        return wrap(table.rawget(LuaValue.valueOf(key)));
    }

    @Override
    public @NotNull ILuaValue rawget(int index) {
        return wrap(table.rawgetInt(index));
    }

    @Override
    public int length() {
        return table.rawlen();
    }

    @Override
    public void forEach(@NotNull BiConsumer<ILuaValue, ILuaValue> action) {
        table.forEach((key, value) -> action.accept(wrap(key), wrap(value)));
    }

    @Override
    public void setMetatable(@Nullable ILuaTable meta) {
        if (meta instanceof LuavaLuaTable wrapper) {
            table.setMetatable(wrapper.getLuavaTable());
            return;
        }
        String actual = meta == null ? "null" : meta.getClass().getName();
        throw new IllegalArgumentException("Expected a LuavaLuaTable metatable but got: " + actual);
    }

    @Override
    public @NotNull Map<ILuaValue, ILuaValue> asMap() {
        Map<ILuaValue, ILuaValue> map = new ConcurrentHashMap<>();
        table.forEach((key, value) -> map.put(wrap(key), wrap(value)));
        return map;
    }
}
