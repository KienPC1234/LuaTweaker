package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.interaction.*;
import com.luatweaker.api.vm.*;
import com.luatweaker.entities.EntitiesLuaBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class InteractionLuaBinding {

    private InteractionLuaBinding() {
    }

    public static void registerBindings(@NotNull ILuaEngine engine) {
        IInteractionService service = new InteractionServiceImpl();

        // One Lua shape for every raycast source: World:Raycast and IEntity:Raycast both
        // return this table (the raw record would otherwise arrive as opaque userdata).
        com.luatweaker.core.bind.LuaBinder.registerReturnConverter(
                com.luatweaker.api.spatial.RaycastResult.class,
                (returnEngine, value) -> value instanceof com.luatweaker.api.spatial.RaycastResult result
                        ? WorldRaycastBinding.wrapRaycastResult(returnEngine, service, null, result)
                        : returnEngine.nilValue());

        // 1. Workspace Service
        ILuaTable workspaceTable = engine.createTable();
        WorldQueryBinding.bindWorkspaceQueries(engine, workspaceTable, service);
        WorldStateBinding.bindWorkspaceState(engine, workspaceTable);
        WorldBulkBinding.bindWorkspaceBulk(engine, workspaceTable);
        WorldValidateBinding.bindWorkspaceValidation(engine, workspaceTable);
        WorldRaycastBinding.bindRaycast(engine, workspaceTable, service);
        WorldEffectsBinding.bindWorkspaceEffects(engine, workspaceTable);
        WorldCapabilityBinding.bindWorkspaceCapabilities(engine, workspaceTable);
        WorldUtilityBinding.bindWorkspaceUtility(engine, workspaceTable);
        WorldAnimationBinding.bindWorkspaceAnimation(engine, workspaceTable);

        // 2. EntityService Service
        ILuaTable entityServiceTable = EntityServiceBinding.bindEntityService(engine, workspaceTable);

        // Register services & globals
        engine.registerService("Workspace", workspaceTable);
        engine.registerService("World", workspaceTable);
        engine.registerService("EntityService", entityServiceTable);
        engine.registerService("Entities", entityServiceTable);

        engine.registerGlobal("Workspace", workspaceTable);
        engine.registerGlobal("World", workspaceTable);
        engine.registerGlobal("EntityService", entityServiceTable);
        engine.registerGlobal("Entities", entityServiceTable);
    }

    public static ILuaValue getWrappedEntity(@NotNull ILuaEngine engine, @NotNull Object rawEntity) {
        IInteractionService service = new InteractionServiceImpl();
        IInteractableEntity entity = service.GetEntity(rawEntity);
        return entity != null ? wrapEntity(engine, entity) : engine.nilValue();
    }

    @NotNull
    public static ILuaTable wrapBlock(@NotNull ILuaEngine engine, @NotNull IInteractableBlock block) {
        ILuaTable table = engine.createTable();
        table.rawset(InteractionBindingSupport.BLOCK_MARKER, engine.wrapUserdata(block));

        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        ILuaValue attrSignal = engine.nilValue();
        if (signalClass != null && !signalClass.isNil()) {
            ILuaValue signalNew = signalClass.asTable().rawget(InteractionBindingSupport.NEW_METHOD);
            attrSignal = engine.callFunction(signalNew, signalClass);
            table.rawset("AttributeChanged", attrSignal);
        }
        final ILuaValue finalAttrSignal = attrSignal;

        ILuaTable meta = engine.createTable();
        ILuaTable methods = engine.createTable();

        BlockTableBinding.bindBlockCore(methods, engine, block, finalAttrSignal);
        BlockTableBinding.bindBlockGeo(methods, engine, block);
        BlockCapabilityBinding.bindBlockEnergyCaps(methods, engine, block);
        BlockCapabilityBinding.bindBlockItemCaps(methods, engine, block);
        BlockCapabilityBinding.bindBlockFluidCaps(methods, engine, block);
        BlockTableBinding.bindBlockIndex(engine, meta, methods, block);
        BlockTableBinding.bindBlockNewIndex(engine, meta, block);

        table.setMetatable(meta);
        return table;
    }

    @NotNull
    public static ILuaTable wrapItem(@NotNull ILuaEngine engine, @NotNull IInteractableItem item) {
        ILuaTable table = engine.createTable();
        table.rawset(InteractionBindingSupport.ITEM_MARKER, engine.wrapUserdata(item));

        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        ILuaValue attrSignal = engine.nilValue();
        if (signalClass != null && !signalClass.isNil()) {
            ILuaValue signalNew = signalClass.asTable().rawget(InteractionBindingSupport.NEW_METHOD);
            attrSignal = engine.callFunction(signalNew, signalClass);
            table.rawset("AttributeChanged", attrSignal);
        }
        final ILuaValue finalAttrSignal = attrSignal;

        ILuaTable meta = engine.createTable();
        ILuaTable methods = engine.createTable();

        ItemTableBinding.bindItemCore(methods, engine, item, finalAttrSignal);
        ItemTableBinding.bindItemData(methods, engine, item);
        ItemTableBinding.bindItemIndex(engine, meta, methods, item);
        ItemTableBinding.bindItemNewIndex(engine, meta, item);

        table.setMetatable(meta);
        return table;
    }

    /**
     * Wraps an entity as the unified Lua entity table (method-style API + Roblox
     * property aliases). IInteractableEntity implementations also implement IEntity,
     * so both interaction events and entity bindings operate on the same shape.
     */
    @NotNull
    public static ILuaTable wrapEntity(@NotNull ILuaEngine engine, @NotNull IInteractableEntity entity) {
        if (entity instanceof IEntity ie) {
            return EntitiesLuaBinding.createEntityLuaTable(engine, ie);
        }
        ILuaTable table = engine.createTable();
        table.rawset(InteractionBindingSupport.ENTITY_MARKER, engine.wrapUserdata(entity));
        return table;
    }

    /** Recursively converts a Java Map/List/primitive (NbtCodec shape) to a Lua table/value. */
    @NotNull
    public static ILuaValue wrapValue(@NotNull ILuaEngine engine, @Nullable Object value) {
        return InteractionConvert.wrapValue(engine, value);
    }

    /** Recursively converts a Lua table/value to a Java Map (NbtCodec shape). */
    @NotNull
    public static java.util.Map<String, Object> toJavaMap(@NotNull ILuaTable table) {
        return InteractionConvert.toJavaMap(table);
    }
}

