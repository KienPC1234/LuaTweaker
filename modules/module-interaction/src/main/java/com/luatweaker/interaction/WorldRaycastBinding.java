package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.interaction.IInteractionService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.entities.EntitiesLuaBinding;

/** Binds raycasts plus the single shared RaycastResult Lua shape. */
final class WorldRaycastBinding {

    private WorldRaycastBinding() {
    }

    static void bindRaycast(ILuaEngine engine, ILuaTable workspaceTable, IInteractionService service) {
        // ===== Raycast API =====
        workspaceTable.rawset("Raycast", args -> {
            int off = LuaBinder.getOffset(args);
            RaycastPrefix prefix = readRaycastPrefix(args, off);
            off = prefix.off();
            if (InteractionBindingSupport.hasMinArgs(args, off, 2)
                    && args[off].isTable() && args[off + 1].isTable()) {
                return executeTableRaycast(engine, service, prefix.dimension(), args, off);
            }
            if (InteractionBindingSupport.hasMinArgs(args, off, 6)) {
                return executeNumbersRaycast(engine, service, prefix.dimension(), args, off);
            }
            return engine.nilValue();
        });
        workspaceTable.rawset("raycast", workspaceTable.rawget("Raycast"));

        // ===== RaycastParams Class =====
        ILuaTable raycastParamsClass = engine.createTable();
        raycastParamsClass.rawset(InteractionBindingSupport.NEW_METHOD, args -> {
            ILuaTable pt = engine.createTable();
            pt.rawset("MaxDistance", engine.wrapNumber(64.0));
            pt.rawset("IgnoreLiquids", engine.wrapBoolean(true));
            pt.rawset("IgnorePassableBlocks", engine.wrapBoolean(false));
            pt.rawset("IgnoreList", engine.createTable());
            return pt;
        });
        engine.registerGlobal("RaycastParams", raycastParamsClass);
    }

    private record RaycastPrefix(String dimension, int off) {
    }

    private static RaycastPrefix readRaycastPrefix(ILuaValue[] args, int start) {
        int off = start;
        int dimOff = -1;
        if (InteractionBindingSupport.hasMinArgs(args, off, 1)
                && args[off].toJavaObject() instanceof String
                && (!InteractionBindingSupport.hasMinArgs(args, off, 2)
                        || !args[off + 1].isTable())) {
            dimOff = off;
            off = off + 1;
        }
        String dimension = dimOff >= 0 ? args[dimOff].asString()
                : InteractionBindingSupport.DEFAULT_DIMENSION;
        return new RaycastPrefix(dimension, off);
    }

    private static ILuaValue executeTableRaycast(ILuaEngine engine, IInteractionService service,
            String dimension, ILuaValue[] args, int off) {
        double[] origin = InteractionVectors.readVector(args[off]);
        double[] direction = InteractionVectors.readVector(args[off + 1]);
        if (origin.length == 0 || direction.length == 0) {
            InteractionBindingSupport.warn("World:Raycast needs Vector3 tables with numeric X/Y/Z");
            return engine.nilValue();
        }
        double maxDist = 64.0;
        boolean ignoreLiquids = true;
        boolean ignorePassable = false;
        java.util.List<IEntity> ignoreEntities = null;
        if (InteractionBindingSupport.hasMinArgs(args, off, 3) && args[off + 2].isTable()) {
            RaycastOptions options = readRaycastOptions(args[off + 2].asTable());
            maxDist = options.maxDist();
            ignoreLiquids = options.ignoreLiquids();
            ignorePassable = options.ignorePassable();
            ignoreEntities = options.ignoreEntities();
        }
        return executeRaycast(engine, service, dimension,
                origin, direction, maxDist, ignoreLiquids, ignorePassable, ignoreEntities);
    }

    private static ILuaValue executeNumbersRaycast(ILuaEngine engine, IInteractionService service,
            String dimension, ILuaValue[] args, int off) {
        double[] numbers = InteractionVectors.readNumbers(args, off, 6);
        if (numbers.length == 0) {
            InteractionBindingSupport.warn("World:Raycast needs six numbers (origin xyz, direction xyz)");
            return engine.nilValue();
        }
        double maxDist = 64.0;
        boolean ignoreLiquids = true;
        if (InteractionBindingSupport.hasMinArgs(args, off, 7)) {
            maxDist = args[off + 6].asDouble();
        }
        if (InteractionBindingSupport.hasMinArgs(args, off, 8)) {
            ignoreLiquids = args[off + 7].asBoolean();
        }
        double[] origin = { numbers[0], numbers[1], numbers[2] };
        double[] direction = { numbers[3], numbers[4], numbers[5] };
        return executeRaycast(engine, service, dimension,
                origin, direction, maxDist, ignoreLiquids, false, null);
    }

    private static ILuaValue executeRaycast(ILuaEngine engine, IInteractionService service,
            String dimension, double[] origin, double[] direction,
            double maxDist, boolean ignoreLiquids, boolean ignorePassable,
            java.util.List<IEntity> ignoreEntities) {
        com.luatweaker.api.spatial.RaycastParams params = new com.luatweaker.api.spatial.RaycastParams(
                maxDist, ignoreLiquids, ignorePassable, ignoreEntities);
        com.luatweaker.api.spatial.RaycastResult res =
                com.luatweaker.api.pal.Platform.getInteraction()
                        .raycast(dimension, origin[0], origin[1], origin[2],
                                direction[0], direction[1], direction[2], params);
        return wrapRaycastResult(engine, service, dimension, res);
    }

    private record RaycastOptions(double maxDist, boolean ignoreLiquids, boolean ignorePassable,
            java.util.List<IEntity> ignoreEntities) {
    }

    private static RaycastOptions readRaycastOptions(ILuaTable paramsTable) {
        double maxDist = 64.0;
        boolean ignoreLiquids = true;
        boolean ignorePassable = false;
        ILuaValue md = paramsTable.rawget("MaxDistance");
        if (md != null && !md.isNil()) {
            maxDist = md.asDouble();
        }
        ILuaValue il = paramsTable.rawget("IgnoreLiquids");
        if (il != null && !il.isNil()) {
            ignoreLiquids = il.asBoolean();
        }
        ILuaValue ip = paramsTable.rawget("IgnorePassableBlocks");
        if (ip != null && !ip.isNil()) {
            ignorePassable = ip.asBoolean();
        }
        ILuaValue ilist = paramsTable.rawget("IgnoreList");
        java.util.List<IEntity> ignoreEntities = readIgnoreList(ilist);
        return new RaycastOptions(maxDist, ignoreLiquids, ignorePassable, ignoreEntities);
    }

    private static java.util.List<IEntity> readIgnoreList(ILuaValue ilist) {
        if (ilist == null || !ilist.isTable()) {
            return java.util.List.of();
        }
        java.util.List<IEntity> ignoreEntities = new java.util.ArrayList<>();
        for (int i = 1; i <= ilist.asTable().length(); i++) {
            IEntity ent = EntitiesLuaBinding.getEntityFromTable(ilist.asTable().rawget(i));
            if (ent != null) {
                ignoreEntities.add(ent);
            }
        }
        return ignoreEntities;
    }

    /**
     * Builds the single Lua shape for raycast results. Also registered as the return converter
     * for {@code RaycastResult}, so {@code entity:raycast()} and {@code World:Raycast()} hand
     * scripts exactly the same table instead of an opaque userdata.
     */
    static ILuaTable wrapRaycastResult(ILuaEngine engine, IInteractionService service,
            @org.jetbrains.annotations.Nullable String dimension,
            com.luatweaker.api.spatial.RaycastResult res) {
        ILuaTable table = engine.createTable();
        table.rawset("IsHit", engine.wrapBoolean(res.isHit()));
        table.rawset("isHit", engine.wrapBoolean(res.isHit()));
        table.rawset("HitType", engine.wrapString(res.hitType()));
        table.rawset("hitType", engine.wrapString(res.hitType()));
        table.rawset("Distance", engine.wrapNumber(res.distance()));
        table.rawset("distance", engine.wrapNumber(res.distance()));

        ILuaTable pos = engine.createTable();
        pos.rawset(InteractionBindingSupport.KEY_X, engine.wrapNumber(res.hitX()));
        pos.rawset(InteractionBindingSupport.KEY_Y, engine.wrapNumber(res.hitY()));
        pos.rawset(InteractionBindingSupport.KEY_Z, engine.wrapNumber(res.hitZ()));
        pos.rawset(InteractionBindingSupport.KEY_X_LOWER, engine.wrapNumber(res.hitX()));
        pos.rawset(InteractionBindingSupport.KEY_Y_LOWER, engine.wrapNumber(res.hitY()));
        pos.rawset(InteractionBindingSupport.KEY_Z_LOWER, engine.wrapNumber(res.hitZ()));
        table.rawset("Position", pos);
        table.rawset("position", pos);

        ILuaTable norm = engine.createTable();
        norm.rawset(InteractionBindingSupport.KEY_X, engine.wrapNumber(res.normalX()));
        norm.rawset(InteractionBindingSupport.KEY_Y, engine.wrapNumber(res.normalY()));
        norm.rawset(InteractionBindingSupport.KEY_Z, engine.wrapNumber(res.normalZ()));
        norm.rawset(InteractionBindingSupport.KEY_X_LOWER, engine.wrapNumber(res.normalX()));
        norm.rawset(InteractionBindingSupport.KEY_Y_LOWER, engine.wrapNumber(res.normalY()));
        norm.rawset(InteractionBindingSupport.KEY_Z_LOWER, engine.wrapNumber(res.normalZ()));
        table.rawset("Normal", norm);
        table.rawset("normal", norm);

        if (res.entity() != null) {
            table.rawset("Entity", EntitiesLuaBinding.createEntityLuaTable(engine, res.entity()));
            table.rawset("entity", table.rawget("Entity"));
        } else {
            table.rawset("Entity", engine.nilValue());
            table.rawset("entity", engine.nilValue());
        }

        if (res.block() != null) {
            String blockDimension = dimension != null ? dimension : res.block().getDimension();
            IInteractableBlock block = service.GetBlock(blockDimension,
                    res.block().getX(), res.block().getY(), res.block().getZ());
            table.rawset(InteractionBindingSupport.BLOCK_PROP,
                    block != null ? InteractionLuaBinding.wrapBlock(engine, block) : engine.nilValue());
            table.rawset(InteractionBindingSupport.BLOCK_KEY, table.rawget(InteractionBindingSupport.BLOCK_PROP));
        } else {
            table.rawset(InteractionBindingSupport.BLOCK_PROP, engine.nilValue());
            table.rawset(InteractionBindingSupport.BLOCK_KEY, engine.nilValue());
        }

        return table;
    }
}
