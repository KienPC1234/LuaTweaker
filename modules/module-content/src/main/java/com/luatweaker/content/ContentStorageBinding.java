package com.luatweaker.content;

import com.luatweaker.api.content.IStorageService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;

/** Registers the "storage"/"Storage" table backed by the storage service. */
final class ContentStorageBinding {

    private ContentStorageBinding() {
    }

    static ILuaTable registerStorageBindings(ILuaEngine engine, IStorageService storageService) {
        ILuaTable storageTable = engine.createTable();

        storageTable.rawset("set", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("storage:set requires (key, value)");
            }
            String key = args[off].asString();
            Object value = args[off + 1].toJavaObject();
            storageService.set(key, value);
            return null;
        });

        storageTable.rawset("get", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("storage:get requires (key, [defaultVal])");
            }
            String key = args[off].asString();
            Object defaultVal = ContentBindingSupport.hasMinArgs(args, off, 2) ? args[off + 1].toJavaObject() : null;
            Object result = storageService.get(key, defaultVal);
            return engine.toLuaValue(result);
        });

        storageTable.rawset("Set", storageTable.rawget("set"));
        storageTable.rawset("Get", storageTable.rawget("get"));
        return storageTable;
    }
}
