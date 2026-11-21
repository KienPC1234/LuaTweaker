package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.interaction.IInteractableBlock;
import com.luatweaker.api.interaction.IInteractableEntity;
import com.luatweaker.api.interaction.IInteractionService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.entities.EntitiesLuaBinding;

/** Binds block/entity lookup queries (GetBlock, GetEntity, FindNearest, GetItem, ...). */
final class WorldQueryBinding {

    private WorldQueryBinding() {
    }

    static void bindWorkspaceQueries(ILuaEngine engine, ILuaTable workspaceTable, IInteractionService service) {
        WorldBlockQueryBinding.bindGetBlock(engine, workspaceTable, service);
        bindGetEntity(engine, workspaceTable, service);
        bindGetEntitiesInRadius(engine, workspaceTable);
        bindFindNearest(engine, workspaceTable);
        bindGetItem(engine, workspaceTable, service);
    }

    private static void bindGetEntity(ILuaEngine engine, ILuaTable workspaceTable, IInteractionService service) {
        workspaceTable.rawset(InteractionBindingSupport.METHOD_GET_ENTITY, args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                return engine.nilValue();
            }
            ILuaValue entityVal = args[off];
            Object arg = entityVal.toJavaObject();
            if (entityVal.isTable()) {
                ILuaValue inner = entityVal.asTable().rawget(InteractionBindingSupport.ENTITY_MARKER);
                if (inner != null && !inner.isNil()) {
                    arg = inner.toJavaObject();
                }
            }
            IInteractableEntity entity = null;
            if (arg instanceof String uuid) {
                entity = service.GetEntity(uuid);
            } else if (arg != null) {
                entity = service.GetEntity(arg);
            }
            return entity != null ? InteractionLuaBinding.wrapEntity(engine, entity) : engine.nilValue();
        });
        workspaceTable.rawset("getEntity", workspaceTable.rawget(InteractionBindingSupport.METHOD_GET_ENTITY));
    }

    private static void bindGetEntitiesInRadius(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("GetEntitiesInRadius", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                return engine.nilValue();
            }
            IEntity center = EntitiesLuaBinding.getEntityFromTable(args[off]);
            double radius = args[off + 1].asDouble();
            if (center == null || radius <= 0) {
                return engine.nilValue();
            }
            java.util.List<IEntity> entities =
                    com.luatweaker.api.pal.Platform.getInteraction().getNearbyEntities(center, radius);
            ILuaTable result = engine.createTable();
            for (int i = 0; i < entities.size(); i++) {
                result.rawset(i + 1, EntitiesLuaBinding.createEntityLuaTable(engine, entities.get(i)));
            }
            return result;
        });
        workspaceTable.rawset("getEntitiesInRadius", workspaceTable.rawget("GetEntitiesInRadius"));
    }

    private static void bindFindNearest(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("FindNearest", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                return engine.nilValue();
            }
            IEntity center = EntitiesLuaBinding.getEntityFromTable(args[off]);
            if (center == null) {
                return engine.nilValue();
            }
            FindFilter filter = readFindFilter(args, off);
            if (filter.radius() <= 0) {
                return engine.nilValue();
            }
            java.util.List<IEntity> entities =
                    com.luatweaker.api.pal.Platform.getInteraction().getNearbyEntities(center, filter.radius());
            IEntity best = selectNearest(entities, center, filter.typeFilter());
            return best != null ? EntitiesLuaBinding.createEntityLuaTable(engine, best) : engine.nilValue();
        });
        workspaceTable.rawset("findNearest", workspaceTable.rawget("FindNearest"));
    }

    private record FindFilter(String typeFilter, double radius) {
    }

    private static FindFilter readFindFilter(ILuaValue[] args, int off) {
        String typeFilter = null;
        double radius = 32.0;
        if (InteractionBindingSupport.hasMinArgs(args, off, 2) && args[off + 1].isString()) {
            typeFilter = args[off + 1].asString();
            if (InteractionBindingSupport.hasMinArgs(args, off, 3) && args[off + 2].isNumber()) {
                radius = args[off + 2].asDouble();
            }
        } else if (InteractionBindingSupport.hasMinArgs(args, off, 2) && args[off + 1].isNumber()) {
            radius = args[off + 1].asDouble();
        }
        return new FindFilter(typeFilter, radius);
    }

    private static IEntity selectNearest(java.util.List<IEntity> entities, IEntity center, String typeFilter) {
        IEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (IEntity candidate : entities) {
            if (candidate == null || candidate.getUuid().equals(center.getUuid())) {
                continue;
            }
            if (typeFilter != null && !typeFilter.isBlank()
                    && !candidate.getType().equalsIgnoreCase(typeFilter)) {
                continue;
            }
            double dist = center.distanceTo(candidate);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private static void bindGetItem(ILuaEngine engine, ILuaTable workspaceTable, IInteractionService service) {
        // GetItem(posOrEntity, slot) or (x, y, z, slot) -> IInteractableItem | nil
        workspaceTable.rawset("GetItem", args -> {
            int off = LuaBinder.getOffset(args);
            if (!InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                return engine.nilValue();
            }
            ILuaValue target = args[off];
            com.luatweaker.api.interaction.IInteractableItem item = readGetItemTarget(service, target, args, off);
            return item != null ? InteractionLuaBinding.wrapItem(engine, item) : engine.nilValue();
        });
        workspaceTable.rawset("getItem", workspaceTable.rawget("GetItem"));
    }

    private static com.luatweaker.api.interaction.IInteractableItem readGetItemTarget(
            IInteractionService service, ILuaValue target, ILuaValue[] args, int off) {
        com.luatweaker.api.interaction.IInteractableItem fromBlock = readBlockItemTarget(service, target, args, off);
        if (fromBlock != null || isBlockTarget(target, args, off)) {
            return fromBlock;
        }
        Object raw = target.toJavaObject();
        if (target.isTable()) {
            ILuaValue inner = target.asTable().rawget(InteractionBindingSupport.ENTITY_MARKER);
            if (inner != null && !inner.isNil()) {
                raw = inner.toJavaObject();
            }
        }
        int slot = args[args.length - 1].asInt();
        if (raw != null) {
            return service.GetItem(raw, slot);
        }
        return null;
    }

    private static boolean isBlockTarget(ILuaValue target, ILuaValue[] args, int off) {
        return target.isTable() && hasPositionKeys(target.asTable())
                || InteractionBindingSupport.hasMinArgs(args, off, 4) && args[off].isNumber();
    }

    private static com.luatweaker.api.interaction.IInteractableItem readBlockItemTarget(
            IInteractionService service, ILuaValue target, ILuaValue[] args, int off) {
        if (target.isTable() && hasPositionKeys(target.asTable())) {
            ILuaTable posTable = target.asTable();
            int[] xyz = InteractionParse.tablePosition(posTable);
            if (xyz.length == 0) {
                return null;
            }
            int slot = args[args.length - 1].asInt();
            IInteractableBlock block =
                    service.GetBlock(WorldBlockQueryBinding.readTableDimension(posTable), xyz[0], xyz[1], xyz[2]);
            if (block != null) {
                return service.GetItem(block, slot);
            }
            return null;
        }
        if (InteractionBindingSupport.hasMinArgs(args, off, 4) && args[off].isNumber()) {
            int slot = args[args.length - 1].asInt();
            IInteractableBlock block = service.GetBlock(InteractionBindingSupport.DEFAULT_DIMENSION,
                    args[off].asInt(), args[off + 1].asInt(), args[off + 2].asInt());
            if (block != null) {
                return service.GetItem(block, slot);
            }
        }
        return null;
    }

    private static boolean hasPositionKeys(ILuaTable table) {
        return table.rawget(InteractionBindingSupport.KEY_X) != null
                || table.rawget(InteractionBindingSupport.KEY_X_LOWER) != null;
    }
}

