package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Registers the EntityService table (spawnEntity plus shared GetEntity lookup). */
final class EntityServiceBinding {

    private EntityServiceBinding() {
    }

    static ILuaTable bindEntityService(ILuaEngine engine, ILuaTable workspaceTable) {
        // 2. EntityService Service
        ILuaTable entityServiceTable = engine.createTable();
        ILuaValue sigClass = engine.getGlobalEnvironment().rawget("Signal");
        ILuaValue entitySpawnedSignal = engine.nilValue();
        if (sigClass != null && !sigClass.isNil()) {
            ILuaValue sigNew = sigClass.asTable().rawget(InteractionBindingSupport.NEW_METHOD);
            entitySpawnedSignal = engine.callFunction(sigNew, sigClass);
            entityServiceTable.rawset("EntitySpawned", entitySpawnedSignal);
        }
        final ILuaValue finalSpawnedSignal = entitySpawnedSignal;
        entityServiceTable.rawset("spawnEntity", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 4)) {
                return engine.nilValue();
            }
            String type = args[off].asString();
            double x = args[off + 1].asDouble();
            double y = args[off + 2].asDouble();
            double z = args[off + 3].asDouble();
            Object spawned = com.luatweaker.api.pal.Platform.getEntity().spawnEntity(type, x, y, z);
            if (spawned == null) {
                return engine.nilValue();
            }
            ILuaValue wrapped = InteractionLuaBinding.getWrappedEntity(engine, spawned);
            if (finalSpawnedSignal != null && !finalSpawnedSignal.isNil()) {
                ILuaValue fireFn = finalSpawnedSignal.asTable().rawget("Fire");
                if (fireFn != null && !fireFn.isNil()) {
                    engine.callFunction(fireFn, finalSpawnedSignal, wrapped);
                }
            }
            return wrapped;
        });
        entityServiceTable.rawset("GetEntity", workspaceTable.rawget(InteractionBindingSupport.METHOD_GET_ENTITY));
        entityServiceTable.rawset("getEntity", workspaceTable.rawget(InteractionBindingSupport.METHOD_GET_ENTITY));
        return entityServiceTable;
    }
}
