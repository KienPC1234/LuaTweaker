package com.luatweaker.storage;

import com.luatweaker.api.storage.IRobloxStorageService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

public class StorageLuaBinding {
    
    private static ILuaValue wrapDataStore(ILuaEngine engine, IRobloxStorageService.IDataStore store) {
        ILuaTable table = engine.createTable();
        table.rawset("GetAsync", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) return engine.nilValue();
            String key = args[off].asString();
            Object res = store.GetAsync(key);
            if (res instanceof ILuaValue lv) return lv;
            if (res instanceof String s) return engine.wrapString(s);
            if (res instanceof Number n) return engine.wrapNumber(n.doubleValue());
            if (res instanceof Boolean b) return engine.wrapBoolean(b);
            if (res == null) return engine.nilValue();
            return engine.wrapUserdata(res);
        });
        table.rawset("SetAsync", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) return engine.nilValue();
            String key = args[off].asString();
            ILuaValue val = args[off + 1];
            store.SetAsync(key, val);
            return engine.nilValue();
        });
        return table;
    }

    public static void registerBindings(ILuaEngine engine, IRobloxStorageService storageService) {
        ILuaTable worldStorage = (ILuaTable) wrapDataStore(engine, storageService.GetWorldStorage());
        ILuaTable sessionStorage = (ILuaTable) wrapDataStore(engine, storageService.GetSessionStorage());

        ILuaTable playerStorageLookup = engine.createTable();
        playerStorageLookup.rawset("GetPlayerStorage", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) return engine.nilValue();
            String playerUuid = args[off].asString();
            return wrapDataStore(engine, storageService.GetPlayerStorage(playerUuid));
        });

        ILuaTable storageTable = engine.createTable();
        storageTable.rawset("GetPlayerStorage", playerStorageLookup.rawget("GetPlayerStorage"));
        storageTable.rawset("GetWorldStorage", args -> worldStorage);
        storageTable.rawset("GetSessionStorage", args -> sessionStorage);
        storageTable.rawset("get", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) return engine.nilValue();
            String key = args[off].asString();
            ILuaValue def = (args.length - off >= 2) ? args[off + 1] : engine.nilValue();
            Object res = storageService.GetSessionStorage().GetAsync(key);
            if (res == null) res = storageService.GetWorldStorage().GetAsync(key);
            if (res == null) return def;
            if (res instanceof ILuaValue lv) return lv;
            if (res instanceof String s) return engine.wrapString(s);
            if (res instanceof Number n) return engine.wrapNumber(n.doubleValue());
            if (res instanceof Boolean b) return engine.wrapBoolean(b);
            return engine.wrapUserdata(res);
        });
        storageTable.rawset("set", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) return engine.nilValue();
            String key = args[off].asString();
            ILuaValue val = args[off + 1];
            storageService.GetSessionStorage().SetAsync(key, val);
            return engine.nilValue();
        });

        engine.registerService("Storage", storageTable);
        engine.registerGlobal("Storage", storageTable);

        engine.registerService("WorldStorage", worldStorage);
        engine.registerService("PlayerStorage", playerStorageLookup);
        engine.registerService("SessionStorage", sessionStorage);

        engine.registerGlobal("WorldStorage", worldStorage);
        engine.registerGlobal("PlayerStorage", playerStorageLookup);
        engine.registerGlobal("SessionStorage", sessionStorage);
    }
}
