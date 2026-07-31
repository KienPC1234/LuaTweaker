package com.luatweaker.math;

import com.luatweaker.api.vm.ILuaValue;
import java.util.ArrayList;
import java.util.List;

public class LuaSignal {
    private final List<ILuaValue> connections = new ArrayList<>();

    public void connect(ILuaValue callback) {
        connections.add(callback);
    }

    public void disconnect(ILuaValue callback) {
        connections.remove(callback);
    }

    public List<ILuaValue> getConnections() {
        return new ArrayList<>(connections);
    }
}
