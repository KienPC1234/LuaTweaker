package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

import java.util.List;

public final class StorageLuaBinding {

    private static final int MIN_ONE_ARG = 1;
    private static final int MIN_TWO_ARGS = 2;

    private StorageLuaBinding() {}

    private static ILuaValue wrapDataStore(ILuaEngine engine, IRobloxStorageService.IDataStore store) {
        ILuaTable table = engine.createTable();
        table.rawset("GetAsync", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.nilValue();
            }
            return wrapStoredValue(engine, store.GetAsync(args[off].asString()));
        });
        table.rawset("SetAsync", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_TWO_ARGS) {
                return engine.nilValue();
            }
            String key = args[off].asString();
            ILuaValue val = args[off + 1];
            store.SetAsync(key, val);
            return engine.nilValue();
        });
        table.rawset("RemoveAsync", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(store.RemoveAsync(args[off].asString()));
        });
        table.rawset("GetKeys", args -> {
            ILuaTable keys = engine.createTable();
            List<String> stored = store.GetKeys();
            for (int i = 0; i < stored.size(); i++) {
                keys.rawset(i + 1, engine.wrapString(stored.get(i)));
            }
            return keys;
        });
        table.rawset("IncrementAsync", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_TWO_ARGS) {
                throw new IllegalArgumentException("IncrementAsync requires (key, delta)");
            }
            return engine.wrapNumber(store.IncrementAsync(args[off].asString(), args[off + 1].asDouble()));
        });
        table.rawset("Flush", args -> {
            store.Flush();
            return engine.nilValue();
        });
        table.rawset("flush", args -> {
            store.Flush();
            return engine.nilValue();
        });
        return table;
    }

    private static ILuaValue wrapStoredValue(ILuaEngine engine, Object res) {
        if (res instanceof ILuaValue lv) {
            return lv;
        }
        if (res instanceof String s) {
            return engine.wrapString(s);
        }
        if (res instanceof Number n) {
            return engine.wrapNumber(n.doubleValue());
        }
        if (res instanceof Boolean b) {
            return engine.wrapBoolean(b);
        }
        if (res == null) {
            return engine.nilValue();
        }
        return engine.wrapUserdata(res);
    }

    public static void registerBindings(ILuaEngine engine, IRobloxStorageService storageService) {
        ILuaTable playerStorageLookup = registerValueStores(engine, storageService);
        registerStorageTable(engine, storageService, playerStorageLookup);
    }

    private static ILuaTable registerValueStores(ILuaEngine engine, IRobloxStorageService storageService) {
        ILuaTable worldStorage = (ILuaTable) wrapDataStore(engine, storageService.GetWorldStorage());
        ILuaTable sessionStorage = (ILuaTable) wrapDataStore(engine, storageService.GetSessionStorage());

        ILuaTable playerStorageLookup = engine.createTable();
        playerStorageLookup.rawset("GetPlayerStorage", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.nilValue();
            }
            String playerUuid = args[off].asString();
            return wrapDataStore(engine, storageService.GetPlayerStorage(playerUuid));
        });

        engine.registerService("WorldStorage", worldStorage);
        engine.registerService("PlayerStorage", playerStorageLookup);
        engine.registerService("SessionStorage", sessionStorage);

        engine.registerGlobal("WorldStorage", worldStorage);
        engine.registerGlobal("PlayerStorage", playerStorageLookup);
        engine.registerGlobal("SessionStorage", sessionStorage);
        return playerStorageLookup;
    }

    private static void registerStorageTable(ILuaEngine engine, IRobloxStorageService storageService,
                                             ILuaTable playerStorageLookup) {
        ILuaTable storageTable = engine.createTable();
        storageTable.rawset("GetPlayerStorage", playerStorageLookup.rawget("GetPlayerStorage"));
        storageTable.rawset("GetWorldStorage", args ->
                wrapDataStore(engine, storageService.GetWorldStorage()));
        storageTable.rawset("GetSessionStorage", args ->
                wrapDataStore(engine, storageService.GetSessionStorage()));
        storageTable.rawset("get", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.nilValue();
            }
            String key = args[off].asString();
            ILuaValue def = (args.length - off >= 2) ? args[off + 1] : engine.nilValue();
            Object res = storageService.GetSessionStorage().GetAsync(key);
            if (res == null) {
                res = storageService.GetWorldStorage().GetAsync(key);
            }
            if (res == null) {
                return def;
            }
            return wrapStoredValue(engine, res);
        });
        storageTable.rawset("set", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_TWO_ARGS) {
                return engine.nilValue();
            }
            String key = args[off].asString();
            ILuaValue val = args[off + 1];
            storageService.GetSessionStorage().SetAsync(key, val);
            return engine.nilValue();
        });
        storageTable.rawset("Flush", args -> {
            storageService.Flush();
            return engine.nilValue();
        });
        storageTable.rawset("flush", args -> {
            storageService.Flush();
            return engine.nilValue();
        });

        engine.registerService("Storage", storageTable);
        engine.registerGlobal("Storage", storageTable);
    }
}
