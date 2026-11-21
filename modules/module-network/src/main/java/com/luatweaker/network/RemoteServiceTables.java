package com.luatweaker.network;

import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the per-channel Lua service table (FireServer/FireClient/.../SetInboundEnabled) that every
 * RemoteEvent/RemoteFunction instance receives. Extracted from {@link RemoteRegistry} so the
 * registry keeps only lookup/ownership duties.
 */
final class RemoteServiceTables {

    private static final int MIN_TWO_ARGS = 2;
    private static final int MIN_THREE_ARGS = 3;

    private RemoteServiceTables() {
    }

    static ILuaTable create(ILuaEngine engine, IRocketNetworkService sender, InboundPolicy policy) {
        ILuaTable table = engine.createTable();
        table.rawset("FireServer", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_TWO_ARGS) {
                String name = args[off].asString();
                sender.FireServer(name, fireArgs(args, off, 1));
            }
            return engine.nilValue();
        });
        table.rawset("FireClient", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_THREE_ARGS) {
                String name = args[off].asString();
                String uuid = args[off + 1].asString();
                sender.FireClient(name, uuid, fireArgs(args, off, 2));
            }
            return engine.nilValue();
        });
        table.rawset("FireAllClients", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_TWO_ARGS) {
                String name = args[off].asString();
                sender.FireAllClients(name, fireArgs(args, off, 1));
            }
            return engine.nilValue();
        });
        table.rawset("FireClients", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_THREE_ARGS && args[off + 1].isTable()) {
                String name = args[off].asString();
                List<String> uuids = uuidList(args[off + 1].asTable());
                sender.FireClients(name, uuids, fireArgs(args, off, 2));
            }
            return engine.nilValue();
        });
        table.rawset("FireAllClientsExcept", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_THREE_ARGS) {
                String name = args[off].asString();
                String excluded = args[off + 1].asString();
                sender.FireAllClientsExcept(name, excluded, fireArgs(args, off, 2));
            }
            return engine.nilValue();
        });
        table.rawset("SetInboundEnabled", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_TWO_ARGS) {
                String name = args[off].asString();
                boolean enabled = isEnabledArg(args[off + 1]);
                RemoteRegistry.requireScriptChannelName(name, "RemoteEvent/RemoteFunction");
                policy.setEnabled(name, enabled);
            }
            return engine.nilValue();
        });
        return table;
    }

    private static boolean isEnabledArg(ILuaValue value) {
        if (value == null || value.isNil()) {
            return true;
        }
        return !value.isBoolean() || value.asBoolean();
    }

    private static ILuaValue[] fireArgs(ILuaValue[] args, int off, int firstValueIndex) {
        return args[off + firstValueIndex].isTable()
                ? unpackTable(args[off + firstValueIndex].asTable())
                : new ILuaValue[0];
    }

    /**
     * Unpacks a Lua vararg-style table into a positional array, preserving order.
     * (asMap() is a HashMap whose iteration order scrambles positional args.)
     */
    static ILuaValue[] unpackTable(ILuaTable tbl) {
        List<ILuaValue> list = new ArrayList<>();
        for (int i = 1; i <= tbl.length(); i++) {
            ILuaValue val = tbl.rawget(i);
            if (val == null || val.isNil()) {
                break;
            }
            list.add(val);
        }
        return list.toArray(new ILuaValue[0]);
    }

    private static List<String> uuidList(ILuaTable tbl) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= tbl.length(); i++) {
            ILuaValue val = tbl.rawget(i);
            if (val == null || val.isNil()) {
                break;
            }
            String uuid = val.asString();
            if (uuid != null && !uuid.isBlank()) {
                out.add(uuid);
            }
        }
        return out;
    }
}
