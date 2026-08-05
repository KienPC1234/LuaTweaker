package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.interaction.*;
import com.luatweaker.api.vm.*;
import com.luatweaker.entities.EntitiesLuaBinding;
import org.jetbrains.annotations.NotNull;

public class InteractionLuaBinding {

    public static void registerBindings(@NotNull ILuaEngine engine) {
        IInteractionService service = new InteractionServiceImpl();
        
        // 1. Workspace Service
        ILuaTable workspaceTable = engine.createTable();
        workspaceTable.rawset("GetBlock", args -> {
            ILuaValue posVal = engine.nilValue();
            if (args.length >= 2) {
                if (args[1].isTable()) {
                    posVal = args[1];
                } else if (args.length >= 3 && args[2].isTable()) {
                    posVal = args[2];
                }
            }
            if (posVal.isTable()) {
                ILuaTable pos = posVal.asTable();
                double x = pos.rawget("X").asDouble();
                double y = pos.rawget("Y").asDouble();
                double z = pos.rawget("Z").asDouble();
                IInteractableBlock block = service.GetBlock("minecraft:overworld", (int) x, (int) y, (int) z);
                return block != null ? wrapBlock(engine, block) : engine.nilValue();
            }
            return engine.nilValue();
        });

        workspaceTable.rawset("GetEntity", args -> {
            ILuaValue entityVal = engine.nilValue();
            if (args.length >= 2) {
                if (!args[1].isTable() || args[1].asTable().rawget("__entity") != null) {
                    entityVal = args[1];
                } else if (args.length >= 3) {
                    entityVal = args[2];
                }
            }
            Object arg = entityVal.toJavaObject();
            if (entityVal.isTable()) {
                ILuaValue inner = entityVal.asTable().rawget("__entity");
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
            return entity != null ? wrapEntity(engine, entity) : engine.nilValue();
        });

        workspaceTable.rawset("GetEntitiesInRadius", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) return engine.nilValue();
            IEntity center = EntitiesLuaBinding.getEntityFromTable(args[off]);
            double radius = args[off + 1].asDouble();
            if (center == null || radius <= 0) return engine.nilValue();
            java.util.List<IEntity> entities = com.luatweaker.api.pal.Platform.getInteraction().getNearbyEntities(center, radius);
            ILuaTable result = engine.createTable();
            for (int i = 0; i < entities.size(); i++) {
                result.rawset(i + 1, EntitiesLuaBinding.createEntityLuaTable(engine, entities.get(i)));
            }
            return result;
        });
        workspaceTable.rawset("getEntitiesInRadius", workspaceTable.rawget("GetEntitiesInRadius"));

        // ===== BlockState API (GetBlockState(x,y,z) or GetBlockState(dim,x,y,z)) =====
        workspaceTable.rawset("GetBlockState", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 3) return engine.nilValue();
            String dimension = dimOff >= 0 ? args[dimOff].asString() : "minecraft:overworld";
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            java.util.Map<String, Object> state = com.luatweaker.api.pal.Platform.getInteraction()
                    .getBlockState(dimension, x, y, z);
            return state != null ? wrapValue(engine, state) : engine.nilValue();
        });
        workspaceTable.rawset("getBlockState", workspaceTable.rawget("GetBlockState"));

        workspaceTable.rawset("SetBlockState", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 4) return engine.nilValue();
            String dimension = dimOff >= 0 ? args[dimOff].asString() : "minecraft:overworld";
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            String blockId = args[off + 3].asString();
            java.util.Map<String, Object> properties = null;
            if (args.length - off >= 5 && args[off + 4].isTable()) {
                properties = toJavaMap(args[off + 4].asTable());
            }
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setBlockState(dimension, x, y, z, blockId, properties));
        });
        workspaceTable.rawset("setBlockState", workspaceTable.rawget("SetBlockState"));

        // ===== BlockEntity NBT API (GetBlockEntityData / SetBlockEntityData) =====
        workspaceTable.rawset("GetBlockEntityData", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 3) return engine.nilValue();
            String dimension = dimOff >= 0 ? args[dimOff].asString() : "minecraft:overworld";
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            java.util.Map<String, Object> data = com.luatweaker.api.pal.Platform.getInteraction()
                    .getBlockEntityData(dimension, x, y, z);
            return data != null ? wrapValue(engine, data) : engine.nilValue();
        });
        workspaceTable.rawset("getBlockEntityData", workspaceTable.rawget("GetBlockEntityData"));

        workspaceTable.rawset("SetBlockEntityData", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 4 || !args[off + 3].isTable()) return engine.wrapBoolean(false);
            String dimension = dimOff >= 0 ? args[dimOff].asString() : "minecraft:overworld";
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            java.util.Map<String, Object> data = toJavaMap(args[off + 3].asTable());
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setBlockEntityData(dimension, x, y, z, data));
        });
        workspaceTable.rawset("setBlockEntityData", workspaceTable.rawget("SetBlockEntityData"));

        // ===== Container ejection (EjectContainerItem(x,y,z,slot,[count])) =====
        workspaceTable.rawset("EjectContainerItem", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 4) return engine.wrapBoolean(false);
            String dimension = dimOff >= 0 ? args[dimOff].asString() : "minecraft:overworld";
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            int slot = args[off + 3].asInt();
            int count = args.length - off >= 5 ? args[off + 4].asInt() : 1;
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .ejectContainerItem(dimension, x, y, z, slot, count));
        });
        workspaceTable.rawset("ejectContainerItem", workspaceTable.rawget("EjectContainerItem"));

        // ===== Bulk world editing (FillBlocks / ReplaceBlocks) =====
        // FillBlocks(x1,y1,z1,x2,y2,z2,blockId,[properties]) or with a dimension prefix.
        // Returns the number of blocks set, or -1 when the platform rejected the
        // operation (unknown block, invalid region, or over the platform cap).
        workspaceTable.rawset("FillBlocks", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 7) return engine.wrapNumber(-1);
            String dimension = dimOff >= 0 ? args[dimOff].asString() : "minecraft:overworld";
            int x1 = args[off].asInt();
            int y1 = args[off + 1].asInt();
            int z1 = args[off + 2].asInt();
            int x2 = args[off + 3].asInt();
            int y2 = args[off + 4].asInt();
            int z2 = args[off + 5].asInt();
            String blockId = args[off + 6].asString();
            java.util.Map<String, Object> properties = null;
            if (args.length - off >= 8 && args[off + 7].isTable()) {
                properties = toJavaMap(args[off + 7].asTable());
            }
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .fillBlocks(dimension, x1, y1, z1, x2, y2, z2, blockId, properties));
        });
        workspaceTable.rawset("fillBlocks", workspaceTable.rawget("FillBlocks"));

        // ReplaceBlocks(x1,y1,z1,x2,y2,z2,fromId,toId) or with a dimension prefix.
        workspaceTable.rawset("ReplaceBlocks", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 8) return engine.wrapNumber(-1);
            String dimension = dimOff >= 0 ? args[dimOff].asString() : "minecraft:overworld";
            int x1 = args[off].asInt();
            int y1 = args[off + 1].asInt();
            int z1 = args[off + 2].asInt();
            int x2 = args[off + 3].asInt();
            int y2 = args[off + 4].asInt();
            int z2 = args[off + 5].asInt();
            String fromId = args[off + 6].asString();
            String toId = args[off + 7].asString();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .replaceBlocks(dimension, x1, y1, z1, x2, y2, z2, fromId, toId));
        });
        workspaceTable.rawset("replaceBlocks", workspaceTable.rawget("ReplaceBlocks"));

        // ===== Server console command (ExecuteCommand("give ...")) =====
        workspaceTable.rawset("ExecuteCommand", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) return engine.wrapBoolean(false);
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .executeCommand(args[off].asString()));
        });
        workspaceTable.rawset("executeCommand", workspaceTable.rawget("ExecuteCommand"));

        // 2. EntityService Service
        ILuaTable entityServiceTable = engine.createTable();
        ILuaValue sigClass = engine.getGlobalEnvironment().rawget("Signal");
        if (sigClass != null && !sigClass.isNil()) {
            ILuaValue sigNew = sigClass.asTable().rawget("new");
            ILuaValue entitySpawnedSignal = engine.callFunction(sigNew, sigClass);
            entityServiceTable.rawset("EntitySpawned", entitySpawnedSignal);
        }
        entityServiceTable.rawset("spawnEntity", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 4) return engine.nilValue();
            String type = args[off].asString();
            double x = args[off + 1].asDouble();
            double y = args[off + 2].asDouble();
            double z = args[off + 3].asDouble();
            Object spawned = com.luatweaker.api.pal.Platform.getEntity().spawnEntity(type, x, y, z);
            return spawned != null ? getWrappedEntity(engine, spawned) : engine.nilValue();
        });
        entityServiceTable.rawset("GetEntity", workspaceTable.rawget("GetEntity"));
        entityServiceTable.rawset("getEntity", workspaceTable.rawget("GetEntity"));

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
        table.rawset("__block", engine.wrapUserdata(block));

        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        ILuaValue attrSignal = engine.nilValue();
        if (signalClass != null && !signalClass.isNil()) {
            ILuaValue signalNew = signalClass.asTable().rawget("new");
            attrSignal = engine.callFunction(signalNew, signalClass);
            table.rawset("AttributeChanged", attrSignal);
        }
        final ILuaValue finalAttrSignal = attrSignal;

        ILuaTable meta = engine.createTable();
        ILuaTable methods = engine.createTable();

        methods.rawset("Destroy", args -> engine.wrapBoolean(block.Destroy()));
        methods.rawset("GetAttribute", args -> {
            if (args.length >= 2) {
                String val = block.getAttribute(args[1].asString());
                return val != null ? engine.wrapString(val) : engine.nilValue();
            }
            return engine.nilValue();
        });
        methods.rawset("GetAttributes", args -> {
            ILuaTable attrs = engine.createTable();
            return attrs;
        });
        methods.rawset("SetAttribute", args -> {
            if (args.length >= 3) {
                String name = args[1].asString();
                String val = args[2].asString();
                block.setAttribute(name, val);
                if (finalAttrSignal != null && !finalAttrSignal.isNil()) {
                    ILuaValue fireFn = finalAttrSignal.asTable().rawget("Fire");
                    if (fireFn != null && !fireFn.isNil()) {
                        engine.callFunction(fireFn, finalAttrSignal, engine.wrapString(name), engine.wrapString(val));
                    }
                }
            }
            return engine.nilValue();
        });
        methods.rawset("UseBlock", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("Block:UseBlock requires (actor)");
            }
            Object actor = args[1].toJavaObject();
            if (args[1].isTable()) {
                ILuaValue inner = args[1].asTable().rawget("__entity");
                if (inner != null && !inner.isNil()) {
                    actor = inner.toJavaObject();
                }
            }
            return engine.wrapBoolean(actor != null && block.useBlock(actor));
        });

        meta.rawset("__index", args -> {
            String key = args[1].asString();
            switch (key) {
                case "Id":
                case "id":
                    return engine.wrapString(block.getId());
                case "Nbt":
                case "nbt":
                    return engine.wrapString(block.getNbt());
                case "Position":
                case "position":
                    ILuaValue v3Class = engine.getGlobalEnvironment().rawget("Vector3");
                    if (v3Class != null && !v3Class.isNil()) {
                        ILuaValue v3New = v3Class.asTable().rawget("new");
                        return engine.callFunction(v3New, v3Class,
                            engine.wrapNumber(block.getX()),
                            engine.wrapNumber(block.getY()),
                            engine.wrapNumber(block.getZ())
                        );
                    }
                    return engine.nilValue();
                case "Dimension":
                case "dimension":
                    return engine.wrapString(block.getDimension());
                case "Hardness":
                case "hardness":
                    return engine.wrapNumber(block.getHardness());
                case "LightLevel":
                case "lightLevel":
                    return engine.wrapNumber(block.getLightLevel());
                case "IsAir":
                case "isAir":
                    return engine.wrapBoolean(block.isAir());
                case "IsSolid":
                case "isSolid":
                    return engine.wrapBoolean(block.isSolid());
                case "IsLiquid":
                case "isLiquid":
                    return engine.wrapBoolean(block.isLiquid());
                default:
                    ILuaValue method = methods.rawget(key);
                    if (method != null && !method.isNil()) {
                        return method;
                    }
                    Object rawBlock = block.getRawBlockState();
                    if (rawBlock != null) {
                        ILuaValue fallback = getDynamicFallback(engine, rawBlock, key);
                        if (fallback != null) {
                            return fallback;
                        }
                    }
                    return engine.nilValue();
            }
        });

        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            ILuaValue val = args[2];
            if ("Id".equals(key) || "id".equals(key)) {
                block.setId(val.asString());
            } else if ("Nbt".equals(key) || "nbt".equals(key)) {
                block.setNbt(val.asString());
            } else {
                Object rawBlock = block.getRawBlockState();
                if (rawBlock != null) {
                    setDynamicFallback(engine, rawBlock, key, val);
                }
            }
            return engine.nilValue();
        });

        table.setMetatable(meta);
        return table;
    }

    @NotNull
    public static ILuaTable wrapItem(@NotNull ILuaEngine engine, @NotNull IInteractableItem item) {
        ILuaTable table = engine.createTable();
        table.rawset("__item", engine.wrapUserdata(item));

        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        ILuaValue attrSignal = engine.nilValue();
        if (signalClass != null && !signalClass.isNil()) {
            ILuaValue signalNew = signalClass.asTable().rawget("new");
            attrSignal = engine.callFunction(signalNew, signalClass);
            table.rawset("AttributeChanged", attrSignal);
        }
        final ILuaValue finalAttrSignal = attrSignal;

        ILuaTable meta = engine.createTable();
        ILuaTable methods = engine.createTable();

        methods.rawset("UseItem", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("Item:UseItem requires (actor)");
            }
            Object actor = args[1].toJavaObject();
            if (args[1].isTable()) {
                ILuaValue inner = args[1].asTable().rawget("__entity");
                if (inner != null && !inner.isNil()) {
                    actor = inner.toJavaObject();
                }
            }
            return engine.wrapBoolean(actor != null && item.useItem(actor));
        });

        methods.rawset("Drop", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("Item:Drop requires (actor, [count])");
            }
            Object actor = args[1].toJavaObject();
            if (args[1].isTable()) {
                ILuaValue inner = args[1].asTable().rawget("__entity");
                if (inner != null && !inner.isNil()) {
                    actor = inner.toJavaObject();
                }
            }
            int count = args.length >= 3 ? args[2].asInt() : item.getCount();
            return engine.wrapBoolean(actor != null && item.drop(actor, count));
        });

        methods.rawset("GetAttribute", args -> {
            if (args.length >= 2) {
                String val = item.getAttribute(args[1].asString());
                return val != null ? engine.wrapString(val) : engine.nilValue();
            }
            return engine.nilValue();
        });
        methods.rawset("GetAttributes", args -> {
            ILuaTable attrs = engine.createTable();
            return attrs;
        });
        methods.rawset("SetAttribute", args -> {
            if (args.length >= 3) {
                String name = args[1].asString();
                String val = args[2].asString();
                item.setAttribute(name, val);
                if (finalAttrSignal != null && !finalAttrSignal.isNil()) {
                    ILuaValue fireFn = finalAttrSignal.asTable().rawget("Fire");
                    if (fireFn != null && !fireFn.isNil()) {
                        engine.callFunction(fireFn, finalAttrSignal, engine.wrapString(name), engine.wrapString(val));
                    }
                }
            }
            return engine.nilValue();
        });

        meta.rawset("__index", args -> {
            String key = args[1].asString();
            switch (key) {
                case "Id":
                case "id":
                    return engine.wrapString(item.getId());
                case "Nbt":
                case "nbt":
                    return engine.wrapString(item.getNbt());
                case "Count":
                case "count":
                    return engine.wrapNumber(item.getCount());
                case "Slot":
                case "slot":
                    return engine.wrapNumber(item.getSlot());
                case "OwnerUuid":
                case "ownerUuid":
                    return item.getOwnerUuid() != null ? engine.wrapString(item.getOwnerUuid()) : engine.nilValue();
                case "Damage":
                case "damage":
                    return engine.wrapNumber(item.getDamage());
                case "MaxDamage":
                case "maxDamage":
                    return engine.wrapNumber(item.getMaxDamage());
                case "CustomName":
                case "customName":
                    return item.getCustomName() != null ? engine.wrapString(item.getCustomName()) : engine.nilValue();
                case "IsDamageable":
                case "isDamageable":
                    return engine.wrapBoolean(item.isDamageable());
                case "IsEnchanted":
                case "isEnchanted":
                    return engine.wrapBoolean(item.isEnchanted());
                default:
                    ILuaValue method = methods.rawget(key);
                    if (method != null && !method.isNil()) {
                        return method;
                    }
                    Object rawItem = item.getRawItemStack();
                    if (rawItem != null) {
                        ILuaValue fallback = getDynamicFallback(engine, rawItem, key);
                        if (fallback != null) {
                            return fallback;
                        }
                    }
                    return engine.nilValue();
            }
        });

        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            ILuaValue val = args[2];
            if ("Count".equals(key) || "count".equals(key)) {
                item.setCount(val.asInt());
            } else if ("Slot".equals(key) || "slot".equals(key)) {
                item.setSlot(val.asInt());
            } else if ("Damage".equals(key) || "damage".equals(key)) {
                item.setDamage(val.asInt());
            } else if ("CustomName".equals(key) || "customName".equals(key)) {
                item.setCustomName(val.asString());
            } else if ("Nbt".equals(key) || "nbt".equals(key)) {
                item.setNbt(val.asString());
            } else {
                Object rawItem = item.getRawItemStack();
                if (rawItem != null) {
                    setDynamicFallback(engine, rawItem, key, val);
                }
            }
            return engine.nilValue();
        });

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
            return com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, ie);
        }
        ILuaTable table = engine.createTable();
        table.rawset("__entity", engine.wrapUserdata(entity));
        return table;
    }

    /** Recursively converts a Java Map/List/primitive (NbtCodec shape) to a Lua table/value. */
    @NotNull
    public static ILuaValue wrapValue(@NotNull ILuaEngine engine, @NotNull Object value) {
        if (value instanceof String s) return engine.wrapString(s);
        if (value instanceof Boolean b) return engine.wrapBoolean(b);
        if (value instanceof Number n) return engine.wrapNumber(n.doubleValue());
        if (value instanceof byte[] arr) {
            ILuaTable t = engine.createTable();
            for (int i = 0; i < arr.length; i++) t.rawset(i + 1, engine.wrapNumber(arr[i] & 0xFF));
            return t;
        }
        if (value instanceof int[] arr) {
            ILuaTable t = engine.createTable();
            for (int i = 0; i < arr.length; i++) t.rawset(i + 1, engine.wrapNumber(arr[i]));
            return t;
        }
        if (value instanceof long[] arr) {
            ILuaTable t = engine.createTable();
            for (int i = 0; i < arr.length; i++) t.rawset(i + 1, engine.wrapNumber(arr[i]));
            return t;
        }
        if (value instanceof java.util.List<?> list) {
            ILuaTable t = engine.createTable();
            for (int i = 0; i < list.size(); i++) {
                t.rawset(i + 1, wrapValue(engine, list.get(i)));
            }
            return t;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            ILuaTable t = engine.createTable();
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                t.rawset(String.valueOf(entry.getKey()), wrapValue(engine, entry.getValue()));
            }
            return t;
        }
        return engine.nilValue();
    }

    /** Recursively converts a Lua table/value to a Java Map (NbtCodec shape). */
    @NotNull
    public static java.util.Map<String, Object> toJavaMap(@NotNull ILuaTable table) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<ILuaValue, ILuaValue> entry : table.asMap().entrySet()) {
            map.put(String.valueOf(entry.getKey().toJavaObject()), toJavaValue(entry.getValue()));
        }
        return map;
    }

    private static Object toJavaValue(ILuaValue value) {
        if (value.isTable()) return toJavaTableValue(value.asTable());
        Object raw = value.toJavaObject();
        if (raw instanceof String || raw instanceof Boolean || raw instanceof Number) {
            return raw;
        }
        if (value.isNil()) return null;
        return raw;
    }

    /**
     * Lua tables come in two shapes that must map to different NBT forms:
     * array-like tables (integer keys 1..n, e.g. NBT lists such as {@code Items})
     * become {@code List}s, everything else becomes a {@code Map}.
     */
    private static Object toJavaTableValue(ILuaTable table) {
        java.util.Map<ILuaValue, ILuaValue> entries = table.asMap();
        if (entries.isEmpty()) return new java.util.LinkedHashMap<>();
        boolean arrayLike = true;
        for (ILuaValue key : entries.keySet()) {
            if (!(key.toJavaObject() instanceof Number num) || num.intValue() < 1) {
                arrayLike = false;
                break;
            }
        }
        if (!arrayLike) return toJavaMap(table);
        Object[] ordered = new Object[entries.size()];
        boolean placedAny = false;
        for (java.util.Map.Entry<ILuaValue, ILuaValue> entry : entries.entrySet()) {
            int index = ((Number) entry.getKey().toJavaObject()).intValue();
            if (index >= 1 && index <= ordered.length) {
                ordered[index - 1] = toJavaValue(entry.getValue());
                placedAny = true;
            }
        }
        if (!placedAny) return toJavaMap(table);
        java.util.List<Object> list = new java.util.ArrayList<>(ordered.length);
        for (Object item : ordered) {
            list.add(item);
        }
        return list;
    }

    @org.jetbrains.annotations.Nullable
    private static ILuaValue getDynamicFallback(@NotNull ILuaEngine engine, @NotNull Object rawJavaObj, @NotNull String key) {
        if (engine instanceof com.luatweaker.core.vm.CobaltLuaEngine cobaltEngine) {
            org.squiddev.cobalt.LuaUserdata proxy = com.luatweaker.core.bind.DynamicJavaProxy.create(
                    cobaltEngine.getCobaltState(), rawJavaObj);
            try {
                org.squiddev.cobalt.LuaValue result = org.squiddev.cobalt.OperationHelper.getTable(
                        cobaltEngine.getCobaltState(), proxy, org.squiddev.cobalt.ValueFactory.valueOf(key));
                if (!result.isNil()) {
                    return new com.luatweaker.core.vm.CobaltLuaValue(result);
                }
            } catch (org.squiddev.cobalt.LuaError | org.squiddev.cobalt.UnwindThrowable e) {
                // Fall through to nil
            }
        }
        return null;
    }

    private static boolean setDynamicFallback(@NotNull ILuaEngine engine, @NotNull Object rawJavaObj, @NotNull String key, @NotNull ILuaValue val) {
        if (engine instanceof com.luatweaker.core.vm.CobaltLuaEngine cobaltEngine) {
            org.squiddev.cobalt.LuaUserdata proxy = com.luatweaker.core.bind.DynamicJavaProxy.create(
                    cobaltEngine.getCobaltState(), rawJavaObj);
            try {
                org.squiddev.cobalt.LuaValue cobaltVal = org.squiddev.cobalt.Constants.NIL;
                if (val instanceof com.luatweaker.core.vm.CobaltLuaValue cobaltWrapper) {
                    cobaltVal = cobaltWrapper.getCobaltValue();
                }
                org.squiddev.cobalt.OperationHelper.setTable(
                        cobaltEngine.getCobaltState(), proxy, org.squiddev.cobalt.ValueFactory.valueOf(key), cobaltVal);
                return true;
            } catch (org.squiddev.cobalt.LuaError | org.squiddev.cobalt.UnwindThrowable e) {
                // Fall through
            }
        }
        return false;
    }
}
