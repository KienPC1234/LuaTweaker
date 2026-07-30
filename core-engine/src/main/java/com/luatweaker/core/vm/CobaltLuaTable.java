package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.VarArgFunction;

import java.util.HashMap;
import java.util.Map;

public class CobaltLuaTable extends CobaltLuaValue implements ILuaTable {
    private final LuaTable table;

    public CobaltLuaTable(LuaTable table) {
        super(table);
        this.table = table;
    }

    private LuaValue unwrap(ILuaValue val) {
        if (val instanceof CobaltLuaValue wrapper) {
            return wrapper.getCobaltValue();
        }
        return Constants.NIL;
    }

    @Override
    public void rawset(String key, ILuaValue value) {
        table.rawset(key, unwrap(value));
    }

    @Override
    public void rawset(String key, String value) {
        table.rawset(key, ValueFactory.valueOf(value));
    }

    @Override
    public void rawset(String key, double value) {
        table.rawset(key, ValueFactory.valueOf(value));
    }

    @Override
    public void rawset(String key, boolean value) {
        table.rawset(key, ValueFactory.valueOf(value));
    }

    @Override
    public void rawset(String key, ILuaFunction function) {
        table.rawset(key, new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                int count = args.count();
                ILuaValue[] wrappedArgs = new ILuaValue[count];
                for (int i = 0; i < count; i++) {
                    wrappedArgs[i] = new CobaltLuaValue(args.arg(i + 1));
                }
                try {
                    ILuaValue result = function.invoke(wrappedArgs);
                    return result == null ? Constants.NIL : unwrap(result);
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError(e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
        });
    }

    @Override
    public void rawset(String key, Object userdata) {
        table.rawset(key, new LuaUserdata(userdata));
    }

    @Override
    public void rawset(int index, ILuaValue value) {
        table.rawset(index, unwrap(value));
    }

    @Override
    public void rawset(int index, String value) {
        table.rawset(index, ValueFactory.valueOf(value));
    }

    @Override
    public void rawset(int index, double value) {
        table.rawset(index, ValueFactory.valueOf(value));
    }

    @Override
    public void rawset(int index, boolean value) {
        table.rawset(index, ValueFactory.valueOf(value));
    }

    @Override
    public ILuaValue rawget(String key) {
        return new CobaltLuaValue(table.rawget(key));
    }

    @Override
    public ILuaValue rawget(int index) {
        return new CobaltLuaValue(table.rawget(index));
    }

    @Override
    public int length() {
        return table.length();
    }

    @Override
    public Map<ILuaValue, ILuaValue> asMap() {
        Map<ILuaValue, ILuaValue> map = new HashMap<>();
        LuaValue k = Constants.NIL;
        while (true) {
            try {
                Varargs n = table.next(k);
                if ((k = n.arg(1)).isNil()) break;
                LuaValue v = n.arg(2);
                map.put(new CobaltLuaValue(k), new CobaltLuaValue(v));
            } catch (LuaError e) {
                throw new RuntimeException(e);
            }
        }
        return map;
    }
}
