package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.ai.IWorldActionService;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.vm.*;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class WorldActionLuaBinding {
    public static void registerBindings(@NotNull ILuaEngine engine) {
        IWorldActionService service = new WorldActionServiceImpl();
        ILuaTable table = engine.createTable();

        table.rawset("breakBlock", args -> {
            if (args.length < 5) {
                throw new IllegalArgumentException("WorldAction:breakBlock requires (actor, x, y, z)");
            }
            IEntity actor = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int x = args[2].asInt();
            int y = args[3].asInt();
            int z = args[4].asInt();
            if (actor != null) {
                return engine.wrapBoolean(service.breakBlock(actor, x, y, z));
            }
            return engine.wrapBoolean(false);
        });

        table.rawset("placeBlock", args -> {
            if (args.length < 6) {
                throw new IllegalArgumentException("WorldAction:placeBlock requires (actor, x, y, z, blockId)");
            }
            IEntity actor = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int x = args[2].asInt();
            int y = args[3].asInt();
            int z = args[4].asInt();
            String blockId = args[5].asString();
            if (actor != null) {
                return engine.wrapBoolean(service.placeBlock(actor, x, y, z, blockId));
            }
            return engine.wrapBoolean(false);
        });

        table.rawset("useBlock", args -> {
            if (args.length < 5) {
                throw new IllegalArgumentException("WorldAction:useBlock requires (actor, x, y, z)");
            }
            IEntity actor = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int x = args[2].asInt();
            int y = args[3].asInt();
            int z = args[4].asInt();
            if (actor != null) {
                return engine.wrapBoolean(service.useBlock(actor, x, y, z));
            }
            return engine.wrapBoolean(false);
        });

        table.rawset("useItem", args -> {
            if (args.length < 3) {
                throw new IllegalArgumentException("WorldAction:useItem requires (actor, slot)");
            }
            IEntity actor = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int slot = args[2].asInt();
            if (actor != null) {
                return engine.wrapBoolean(service.useItem(actor, slot));
            }
            return engine.wrapBoolean(false);
        });

        table.rawset("lookAt", args -> {
            if (args.length < 3) {
                throw new IllegalArgumentException("WorldAction:lookAt requires (actor, x/target, [y], [z])");
            }
            IEntity actor = EntitiesLuaBinding.getEntityFromTable(args[1]);
            if (actor != null) {
                if (args.length >= 5) {
                    double x = args[2].asDouble();
                    double y = args[3].asDouble();
                    double z = args[4].asDouble();
                    service.lookAt(actor, x, y, z);
                } else {
                    IEntity target = EntitiesLuaBinding.getEntityFromTable(args[2]);
                    if (target != null) {
                        service.lookAt(actor, target);
                    }
                }
            }
            return null;
        });

        table.rawset("moveInventoryItem", args -> {
            if (args.length < 4) {
                throw new IllegalArgumentException("WorldAction:moveInventoryItem requires (actor, fromSlot, toSlot)");
            }
            IEntity actor = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int fromSlot = args[2].asInt();
            int toSlot = args[3].asInt();
            if (actor != null) {
                return engine.wrapBoolean(service.moveInventoryItem(actor, fromSlot, toSlot));
            }
            return engine.wrapBoolean(false);
        });

        table.rawset("dropInventoryItem", args -> {
            if (args.length < 4) {
                throw new IllegalArgumentException("WorldAction:dropInventoryItem requires (actor, slot, count)");
            }
            IEntity actor = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int slot = args[2].asInt();
            int count = args[3].asInt();
            if (actor != null) {
                return engine.wrapBoolean(service.dropInventoryItem(actor, slot, count));
            }
            return engine.wrapBoolean(false);
        });

        table.rawset("getNearbyBlocks", args -> {
            if (args.length < 3) {
                throw new IllegalArgumentException("WorldAction:getNearbyBlocks requires (entity, radius)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            int radius = args[2].asInt();
            ILuaTable listTable = engine.createTable();
            if (entity != null) {
                List<IWorldBlock> blocks = service.getNearbyBlocks(entity, radius);
                for (int i = 0; i < blocks.size(); i++) {
                    listTable.rawset(i + 1, wrapWorldBlock(engine, blocks.get(i)));
                }
            }
            return listTable;
        });

        table.rawset("getInventoryItems", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("WorldAction:getInventoryItems requires (entity)");
            }
            IEntity entity = EntitiesLuaBinding.getEntityFromTable(args[1]);
            ILuaTable listTable = engine.createTable();
            if (entity != null) {
                List<ILocatedItem> items = service.getInventoryItems(entity);
                for (int i = 0; i < items.size(); i++) {
                    listTable.rawset(i + 1, wrapLocatedItem(engine, items.get(i)));
                }
            }
            return listTable;
        });

        engine.registerService("WorldAction", table);
    }

    @NotNull
    private static ILuaTable wrapWorldBlock(@NotNull ILuaEngine engine, @NotNull IWorldBlock block) {
        ILuaTable t = engine.createTable();
        t.rawset("id", block.getId());
        t.rawset("x", block.getX());
        t.rawset("y", block.getY());
        t.rawset("z", block.getZ());
        t.rawset("dimension", block.getDimension());
        t.rawset("__block", engine.wrapUserdata(block));
        return t;
    }

    @NotNull
    private static ILuaTable wrapLocatedItem(@NotNull ILuaEngine engine, @NotNull ILocatedItem item) {
        ILuaTable t = engine.createTable();
        t.rawset("id", item.getId());
        t.rawset("count", item.getCount());
        t.rawset("slot", item.getSlot());
        String uuid = item.getOwnerUuid();
        if (uuid != null) {
            t.rawset("ownerUuid", uuid);
        }
        Integer bx = item.getBlockX();
        if (bx != null) {
            t.rawset("blockX", bx);
            t.rawset("blockY", item.getBlockY());
            t.rawset("blockZ", item.getBlockZ());
        }
        t.rawset("__item", engine.wrapUserdata(item));
        return t;
    }
}
