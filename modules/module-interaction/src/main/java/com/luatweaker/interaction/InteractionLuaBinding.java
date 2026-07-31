package com.luatweaker.interaction;

import com.luatweaker.api.interaction.*;
import com.luatweaker.api.vm.*;
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

        // 2. EntityService Service
        ILuaTable entityServiceTable = engine.createTable();
        ILuaValue sigClass = engine.getGlobalEnvironment().rawget("Signal");
        if (sigClass != null && !sigClass.isNil()) {
            ILuaValue sigNew = sigClass.asTable().rawget("new");
            ILuaValue entitySpawnedSignal = engine.callFunction(sigNew, sigClass);
            entityServiceTable.rawset("EntitySpawned", entitySpawnedSignal);
        }

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
                    return engine.nilValue();
            }
        });

        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            if ("Id".equals(key) || "id".equals(key)) {
                block.setId(args[2].asString());
            } else if ("Nbt".equals(key) || "nbt".equals(key)) {
                block.setNbt(args[2].asString());
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
                    return engine.nilValue();
            }
        });

        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            if ("Count".equals(key) || "count".equals(key)) {
                item.setCount(args[2].asInt());
            } else if ("Slot".equals(key) || "slot".equals(key)) {
                item.setSlot(args[2].asInt());
            } else if ("Damage".equals(key) || "damage".equals(key)) {
                item.setDamage(args[2].asInt());
            } else if ("CustomName".equals(key) || "customName".equals(key)) {
                item.setCustomName(args[2].asString());
            } else if ("Nbt".equals(key) || "nbt".equals(key)) {
                item.setNbt(args[2].asString());
            }
            return engine.nilValue();
        });

        table.setMetatable(meta);
        return table;
    }

    @NotNull
    public static ILuaTable wrapEntity(@NotNull ILuaEngine engine, @NotNull IInteractableEntity entity) {
        ILuaTable table = engine.createTable();
        table.rawset("__entity", engine.wrapUserdata(entity));

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

        methods.rawset("SetPosition", args -> {
            if (args.length < 2) return null;
            ILuaValue posVal = args[1];
            if (posVal.isTable()) {
                ILuaTable pos = posVal.asTable();
                entity.setPosition(
                    pos.rawget("X").asDouble(),
                    pos.rawget("Y").asDouble(),
                    pos.rawget("Z").asDouble()
                );
            }
            return null;
        });

        methods.rawset("LookAt", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("Entity:LookAt requires (position/target)");
            }
            ILuaValue arg = args[1];
            if (arg.isTable()) {
                ILuaTable tbl = arg.asTable();
                ILuaValue inner = tbl.rawget("__entity");
                if (inner != null && !inner.isNil()) {
                    entity.lookAt(inner.toJavaObject());
                } else if (tbl.rawget("X") != null) {
                    entity.lookAt(
                        tbl.rawget("X").asDouble(),
                        tbl.rawget("Y").asDouble(),
                        tbl.rawget("Z").asDouble()
                    );
                }
            } else {
                Object target = arg.toJavaObject();
                if (target != null) {
                    entity.lookAt(target);
                }
            }
            return null;
        });

        methods.rawset("SendMessage", args -> {
            if (args.length >= 2) {
                entity.SendMessage(args[1].asString());
            }
            return null;
        });

        methods.rawset("SendTitle", args -> {
            if (args.length >= 3) {
                entity.SendTitle(args[1].asString(), args[2].asString());
            } else if (args.length >= 2) {
                entity.SendTitle(args[1].asString(), "");
            }
            return null;
        });

        methods.rawset("SendOverlayMessage", args -> {
            if (args.length >= 2) {
                entity.SendOverlayMessage(args[1].asString());
            }
            return null;
        });

        methods.rawset("GiveItem", args -> {
            if (args.length >= 3) {
                return engine.wrapBoolean(entity.GiveItem(args[1].asString(), args[2].asInt()));
            } else if (args.length >= 2) {
                return engine.wrapBoolean(entity.GiveItem(args[1].asString(), 1));
            }
            return engine.wrapBoolean(false);
        });

        methods.rawset("GetAttribute", args -> {
            if (args.length >= 2) {
                String val = entity.getAttribute(args[1].asString());
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
                entity.setAttribute(name, val);
                if (finalAttrSignal != null && !finalAttrSignal.isNil()) {
                    ILuaValue fireFn = finalAttrSignal.asTable().rawget("Fire");
                    if (fireFn != null && !fireFn.isNil()) {
                        engine.callFunction(fireFn, finalAttrSignal, engine.wrapString(name), engine.wrapString(val));
                    }
                }
            }
            return engine.nilValue();
        });

        methods.rawset("ShootProjectile", args -> {
            if (args.length >= 2) {
                String type = args[1].asString();
                double speed = args.length >= 3 ? args[2].asDouble() : 1.5;
                double inacc = args.length >= 4 ? args[3].asDouble() : 0.0;
                entity.ShootProjectile(type, speed, inacc);
            }
            return null;
        });

        methods.rawset("ShootProjectileAt", args -> {
            if (args.length >= 3) {
                String type = args[1].asString();
                Object target = args[2].toJavaObject();
                if (args[2].isTable()) {
                    ILuaValue inner = args[2].asTable().rawget("__entity");
                    if (inner != null && !inner.isNil()) {
                        target = inner.toJavaObject();
                    }
                }
                double speed = args.length >= 4 ? args[3].asDouble() : 1.5;
                if (target != null) {
                    entity.ShootProjectileAt(type, target, speed);
                }
            }
            return null;
        });

        methods.rawset("PlayAnimation", args -> {
            if (args.length >= 2) {
                String animName = args[1].asString();
                double speed = args.length >= 3 ? args[2].asDouble() : 1.0;
                double transition = args.length >= 4 ? args[3].asDouble() : 0.2;
                entity.PlayAnimation(animName, speed, transition);
            }
            return null;
        });

        methods.rawset("StopAnimation", args -> {
            if (args.length >= 2) {
                entity.StopAnimation(args[1].asString());
            }
            return null;
        });

        methods.rawset("Destroy", args -> {
            entity.Destroy();
            return null;
        });

        meta.rawset("__index", args -> {
            String key = args[1].asString();
            switch (key) {
                case "Id":
                case "id":
                    return engine.wrapString(entity.getId());
                case "Nbt":
                case "nbt":
                    return engine.wrapString(entity.getNbt());
                case "Name":
                case "name":
                    return engine.wrapString(entity.getName());
                case "Type":
                case "type":
                    return engine.wrapString(entity.getType());
                case "Position":
                case "position":
                    ILuaValue v3Class = engine.getGlobalEnvironment().rawget("Vector3");
                    if (v3Class != null && !v3Class.isNil()) {
                        ILuaValue v3New = v3Class.asTable().rawget("new");
                        return engine.callFunction(v3New, v3Class,
                            engine.wrapNumber(entity.getX()),
                            engine.wrapNumber(entity.getY()),
                            engine.wrapNumber(entity.getZ())
                        );
                    }
                    return engine.nilValue();
                case "Health":
                case "health":
                    return engine.wrapNumber(entity.getHealth());
                case "MaxHealth":
                case "maxHealth":
                    return engine.wrapNumber(entity.getMaxHealth());
                case "IsAlive":
                case "isAlive":
                    return engine.wrapBoolean(entity.isAlive());
                case "IsOnFire":
                case "isOnFire":
                    return engine.wrapBoolean(entity.isOnFire());
                case "IsSneaking":
                case "isSneaking":
                    return engine.wrapBoolean(entity.isSneaking());
                case "IsSprinting":
                case "isSprinting":
                    return engine.wrapBoolean(entity.isSprinting());
                case "CustomName":
                case "customName":
                    return engine.wrapString(entity.getCustomName());
                case "Velocity":
                case "velocity":
                    ILuaValue v3Cls = engine.getGlobalEnvironment().rawget("Vector3");
                    if (v3Cls != null && !v3Cls.isNil()) {
                        ILuaValue v3New = v3Cls.asTable().rawget("new");
                        return engine.callFunction(v3New, v3Cls,
                            engine.wrapNumber(entity.getVx()),
                            engine.wrapNumber(entity.getVy()),
                            engine.wrapNumber(entity.getVz())
                        );
                    }
                    return engine.nilValue();
                default:
                    ILuaValue method = methods.rawget(key);
                    if (method != null && !method.isNil()) {
                        return method;
                    }
                    return engine.nilValue();
            }
        });

        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            if ("Health".equals(key) || "health".equals(key)) {
                entity.setHealth((float) args[2].asDouble());
            } else if ("MaxHealth".equals(key) || "maxHealth".equals(key)) {
                entity.setMaxHealth((float) args[2].asDouble());
            } else if ("IsOnFire".equals(key) || "isOnFire".equals(key)) {
                entity.setOnFire(args[2].asBoolean());
            } else if ("IsSneaking".equals(key) || "isSneaking".equals(key)) {
                entity.setSneaking(args[2].asBoolean());
            } else if ("IsSprinting".equals(key) || "isSprinting".equals(key)) {
                entity.setSprinting(args[2].asBoolean());
            } else if ("CustomName".equals(key) || "customName".equals(key)) {
                entity.setCustomName(args[2].asString());
            } else if ("Nbt".equals(key) || "nbt".equals(key)) {
                entity.setNbt(args[2].asString());
            } else if ("Velocity".equals(key) || "velocity".equals(key)) {
                if (args[2].isTable()) {
                    ILuaTable vel = args[2].asTable();
                    entity.setVelocity(
                        vel.rawget("X").asDouble(),
                        vel.rawget("Y").asDouble(),
                        vel.rawget("Z").asDouble()
                    );
                }
            } else if ("Position".equals(key) || "position".equals(key)) {
                if (args[2].isTable()) {
                    ILuaTable pos = args[2].asTable();
                    entity.setPosition(
                        pos.rawget("X").asDouble(),
                        pos.rawget("Y").asDouble(),
                        pos.rawget("Z").asDouble()
                    );
                }
            }
            return null;
        });

        table.setMetatable(meta);
        return table;
    }
}
