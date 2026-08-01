package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.vm.*;

public class EntitiesLuaBinding {

    public static ILuaTable createPlayerLuaTable(ILuaEngine engine, IPlayer player) {
        ILuaTable table = createEntityLuaTable(engine, player);

        bindMethod(table, "sendMessage", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                player.sendMessage(args[off].asString());
            }
            return engine.nilValue();
        });

        bindMethod(table, "sendActionBar", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                player.sendActionBar(args[off].asString());
            }
            return engine.nilValue();
        });

        bindMethod(table, "getName", args -> engine.wrapString(player.getName()));
        bindMethod(table, "getUuid", args -> engine.wrapString(player.getUuid()));
        bindMethod(table, "getUUID", args -> engine.wrapString(player.getUuid()));
        bindMethod(table, "isSneaking", args -> engine.wrapBoolean(player.isSneaking()));
        bindMethod(table, "isCreative", args -> engine.wrapBoolean(player.isCreative()));
        bindMethod(table, "getHealth", args -> engine.wrapNumber(player.getHealth()));
        bindMethod(table, "setHealth", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) player.setHealth((float) args[off].asDouble());
            return engine.nilValue();
        });

        bindMethod(table, "giveItem", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String id = args[off].asString();
                int count = args.length - off >= 2 ? args[off + 1].asInt() : 1;
                player.giveItem(id, count);
            }
            return engine.nilValue();
        });

        bindMethod(table, "giveExperience", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) player.giveExperience(args[off].asInt());
            return engine.nilValue();
        });

        bindMethod(table, "addEffect", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String effect = args[off].asString();
                int duration = args.length - off >= 2 ? args[off + 1].asInt() : 200;
                int amp = args.length - off >= 3 ? args[off + 2].asInt() : 0;
                player.addEffect(effect, duration, amp);
            }
            return engine.nilValue();
        });

        bindMethod(table, "playSound", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String soundId = args[off].asString();
                float volume = args.length - off >= 2 ? (float) args[off + 1].asDouble() : 1.0f;
                float pitch  = args.length - off >= 3 ? (float) args[off + 2].asDouble() : 1.0f;
                player.playSound(soundId, volume, pitch);
            }
            return engine.nilValue();
        });

        bindMethod(table, "getMainHandItem", args -> engine.wrapString(player.getMainHandItem()));
        bindMethod(table, "getOffHandItem", args -> engine.wrapString(player.getOffHandItem()));
        bindMethod(table, "setMainHandItem", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String itemId = args[off].asString();
                int count = args.length - off >= 2 ? args[off + 1].asInt() : 1;
                player.setMainHandItem(itemId, count);
            }
            return engine.nilValue();
        });
        bindMethod(table, "clearInventory", args -> { player.clearInventory(); return engine.nilValue(); });
        bindMethod(table, "dropItem", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String itemId = args[off].asString();
                int count = args.length - off >= 2 ? args[off + 1].asInt() : 1;
                player.dropItem(itemId, count);
            }
            return engine.nilValue();
        });
        bindMethod(table, "actionText", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                player.sendActionBar(args[off].asString());
            }
            return engine.nilValue();
        });

        bindMethod(table, "sendTitle", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String title = args[off].asString();
                String subtitle = args.length - off >= 2 ? args[off + 1].asString() : "";
                int fadeIn = args.length - off >= 3 ? args[off + 2].asInt() : 10;
                int stay = args.length - off >= 4 ? args[off + 3].asInt() : 70;
                int fadeOut = args.length - off >= 5 ? args[off + 4].asInt() : 20;
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            }
            return engine.nilValue();
        });

        bindMethod(table, "heal", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) player.heal((float) args[off].asDouble());
            return engine.nilValue();
        });

        bindMethod(table, "feed", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                int food = args[off].asInt();
                float sat = args.length - off >= 2 ? (float) args[off + 1].asDouble() : 1.0f;
                player.feed(food, sat);
            }
            return engine.nilValue();
        });

        bindMethod(table, "teleport", args -> {
            int off = getOffset(args);
            if (args.length - off >= 3) {
                double x = args[off].asDouble();
                double y = args[off + 1].asDouble();
                double z = args[off + 2].asDouble();
                player.teleport(x, y, z);
            }
            return engine.nilValue();
        });

        return table;
    }

    public static ILuaTable createEntityLuaTable(ILuaEngine engine, IEntity entity) {
        ILuaTable table = engine.createTable();
        table.rawset("__entity", engine.wrapUserdata(entity));

        bindMethod(table, "getType", args -> engine.wrapString(entity.getType()));
        bindMethod(table, "getName", args -> engine.wrapString(entity.getName()));
        bindMethod(table, "getHealth", args -> engine.wrapNumber(entity.getHealth()));
        bindMethod(table, "getMaxHealth", args -> engine.wrapNumber(entity.getMaxHealth()));

        bindMethod(table, "setHealth", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) entity.setHealth((float) args[off].asDouble());
            return engine.nilValue();
        });

        bindMethod(table, "heal", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) entity.heal((float) args[off].asDouble());
            return engine.nilValue();
        });

        bindMethod(table, "kill", args -> { entity.kill(); return engine.nilValue(); });

        bindMethod(table, "damage", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) entity.damage((float) args[off].asDouble());
            return engine.nilValue();
        });
        bindMethod(table, "hurt", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) entity.damage((float) args[off].asDouble());
            return engine.nilValue();
        });

        bindMethod(table, "addEffect", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String effect = args[off].asString();
                int duration = args.length - off >= 2 ? args[off + 1].asInt() : 200;
                int amp = args.length - off >= 3 ? args[off + 2].asInt() : 0;
                entity.addEffect(effect, duration, amp);
            }
            return engine.nilValue();
        });

        bindMethod(table, "removeEffect", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) entity.removeEffect(args[off].asString());
            return engine.nilValue();
        });
        bindMethod(table, "removeAllEffects", args -> { entity.removeAllEffects(); return engine.nilValue(); });

        bindMethod(table, "hasEffect", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) return engine.wrapBoolean(entity.hasEffect(args[off].asString()));
            return engine.wrapBoolean(false);
        });

        bindMethod(table, "setIgniteSeconds", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) entity.setIgniteSeconds(args[off].asInt());
            return engine.nilValue();
        });
        bindMethod(table, "extinguish", args -> { entity.extinguish(); return engine.nilValue(); });

        bindMethod(table, "playSound", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String soundId = args[off].asString();
                float volume = args.length - off >= 2 ? (float) args[off + 1].asDouble() : 1.0f;
                float pitch  = args.length - off >= 3 ? (float) args[off + 2].asDouble() : 1.0f;
                entity.playSound(soundId, volume, pitch);
            }
            return engine.nilValue();
        });

        bindMethod(table, "spawnParticle", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String particleId = args[off].asString();
                int count = args.length - off >= 2 ? args[off + 1].asInt() : 1;
                double speed = args.length - off >= 3 ? args[off + 2].asDouble() : 0.0;
                entity.spawnParticle(particleId, count, speed);
            }
            return engine.nilValue();
        });

        bindMethod(table, "spawnEntity", args -> {
            int off = getOffset(args);
            if (args.length - off >= 1) {
                String entityId = args[off].asString();
                double dx = args.length - off >= 2 ? args[off + 1].asDouble() : 0.0;
                double dy = args.length - off >= 3 ? args[off + 2].asDouble() : 0.0;
                double dz = args.length - off >= 4 ? args[off + 3].asDouble() : 0.0;
                com.luatweaker.api.entity.IEntity spawned = entity.spawnEntity(entityId, dx, dy, dz);
                if (spawned != null) {
                    return createEntityLuaTable(engine, spawned);
                }
            }
            return engine.nilValue();
        });

        ILuaTable attributes = engine.createTable();
        table.rawset("__attributes", attributes);

        ILuaFunction setAttr = args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 2) {
                attributes.rawset(args[off].asString(), args[off + 1]);
            }
            return engine.nilValue();
        };
        table.rawset("SetAttribute", setAttr);
        table.rawset("setAttribute", setAttr);

        ILuaFunction getAttr = args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 1) {
                ILuaValue val = attributes.rawget(args[off].asString());
                return val != null ? val : engine.nilValue();
            }
            return engine.nilValue();
        };
        table.rawset("GetAttribute", getAttr);
        table.rawset("getAttribute", getAttr);

        ILuaFunction sendMsg = args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 1) {
                if (entity instanceof IPlayer p) {
                    p.sendMessage(args[off].asString());
                }
            }
            return engine.nilValue();
        };
        table.rawset("SendMessage", sendMsg);
        table.rawset("sendMessage", sendMsg);

        ILuaFunction shootProj = args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 1) {
                String projectileType = args[off].asString();
                double speed = args.length - off >= 2 ? args[off + 1].asDouble() : 1.5;
                double inaccuracy = args.length - off >= 3 ? args[off + 2].asDouble() : 0.0;
                com.luatweaker.api.pal.Platform.getInteraction().shootProjectile(entity, projectileType, speed, inaccuracy);
            }
            return engine.nilValue();
        };
        table.rawset("shootProjectile", shootProj);
        table.rawset("ShootProjectile", shootProj);

        ILuaFunction shootProjAt = args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 2) {
                String projectileType = args[off].asString();
                com.luatweaker.api.entity.IEntity target = getEntityFromTable(args[off + 1]);
                double speed = args.length - off >= 3 ? args[off + 2].asDouble() : 1.5;
                if (target != null) {
                    com.luatweaker.api.pal.Platform.getInteraction().shootProjectileAt(entity, projectileType, target, speed);
                }
            }
            return engine.nilValue();
        };
        table.rawset("shootProjectileAt", shootProjAt);
        table.rawset("ShootProjectileAt", shootProjAt);

        ILuaFunction playAnim = args -> {
            int off = (args.length > 0 && args[0].isTable()) ? 1 : 0;
            if (args.length - off >= 1) {
                String animName = args[off].asString();
                double speed = args.length - off >= 2 ? args[off + 1].asDouble() : 1.0;
                double transition = args.length - off >= 3 ? args[off + 2].asDouble() : 0.1;
                com.luatweaker.api.pal.Platform.getInteraction().playAnimation(entity, animName, speed, transition);
            }
            return engine.nilValue();
        };
        table.rawset("playAnimation", playAnim);
        table.rawset("PlayAnimation", playAnim);

        table.rawset("teleport", args -> {
            if (args.length >= 4) entity.teleport(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            return engine.nilValue();
        });
        table.rawset("setPos", args -> {
            if (args.length >= 4) entity.teleport(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            return engine.nilValue();
        });
        table.rawset("getPos", args -> {
            ILuaTable pos = engine.createTable();
            pos.rawset("x", engine.wrapNumber(entity.getX()));
            pos.rawset("y", engine.wrapNumber(entity.getY()));
            pos.rawset("z", engine.wrapNumber(entity.getZ()));
            return pos;
        });

        table.rawset("setMotion", args -> {
            if (args.length >= 4) entity.setMotion(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            return engine.nilValue();
        });
        table.rawset("addVelocity", args -> {
            if (args.length >= 4) entity.addVelocity(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            return engine.nilValue();
        });

        table.rawset("getX", args -> engine.wrapNumber(entity.getX()));
        table.rawset("getY", args -> engine.wrapNumber(entity.getY()));
        table.rawset("getZ", args -> engine.wrapNumber(entity.getZ()));
        table.rawset("getYaw", args -> engine.wrapNumber(entity.getYaw()));
        table.rawset("getPitch", args -> engine.wrapNumber(entity.getPitch()));
        table.rawset("setYaw", args -> { if (args.length >= 2) entity.setYaw((float) args[1].asDouble()); return engine.nilValue(); });
        table.rawset("setPitch", args -> { if (args.length >= 2) entity.setPitch((float) args[1].asDouble()); return engine.nilValue(); });

        table.rawset("isSneaking", args -> engine.wrapBoolean(entity.isSneaking()));
        table.rawset("setSneaking", args -> { if (args.length >= 2) entity.setSneaking(args[1].asBoolean()); return engine.nilValue(); });
        table.rawset("isSprinting", args -> engine.wrapBoolean(entity.isSprinting()));
        table.rawset("setSprinting", args -> { if (args.length >= 2) entity.setSprinting(args[1].asBoolean()); return engine.nilValue(); });
        table.rawset("isGlowing", args -> engine.wrapBoolean(entity.isGlowing()));
        table.rawset("setGlowing", args -> { if (args.length >= 2) entity.setGlowing(args[1].asBoolean()); return engine.nilValue(); });
        table.rawset("isInvulnerable", args -> engine.wrapBoolean(entity.isInvulnerable()));
        table.rawset("setInvulnerable", args -> { if (args.length >= 2) entity.setInvulnerable(args[1].asBoolean()); return engine.nilValue(); });
        table.rawset("isInWater", args -> engine.wrapBoolean(entity.isInWater()));
        table.rawset("isInLava", args -> engine.wrapBoolean(entity.isInLava()));
        table.rawset("isOnGround", args -> engine.wrapBoolean(entity.isOnGround()));

        table.rawset("getCustomName", args -> engine.wrapString(entity.getCustomName()));
        table.rawset("setCustomName", args -> { if (args.length >= 2) entity.setCustomName(args[1].asString()); return engine.nilValue(); });
        table.rawset("isCustomNameVisible", args -> engine.wrapBoolean(entity.isCustomNameVisible()));
        table.rawset("setCustomNameVisible", args -> { if (args.length >= 2) entity.setCustomNameVisible(args[1].asBoolean()); return engine.nilValue(); });

        table.rawset("addTag", args -> { if (args.length >= 2) entity.addTag(args[1].asString()); return engine.nilValue(); });
        table.rawset("removeTag", args -> { if (args.length >= 2) entity.removeTag(args[1].asString()); return engine.nilValue(); });
        table.rawset("hasTag", args -> { if (args.length >= 2) return engine.wrapBoolean(entity.hasTag(args[1].asString())); return engine.wrapBoolean(false); });

        table.rawset("swingArm", args -> { entity.swingArm(); return engine.nilValue(); });

        table.rawset("isPlayer", args -> engine.wrapBoolean(entity.isPlayer()));
        table.rawset("isLiving", args -> engine.wrapBoolean(entity.isLiving()));
        table.rawset("isAlive", args -> engine.wrapBoolean(entity.isAlive()));
        table.rawset("remove", args -> { entity.remove(); return engine.nilValue(); });

        return table;
    }

    public static com.luatweaker.api.entity.IEntity getEntityFromTable(com.luatweaker.api.vm.ILuaValue tableVal) {
        if (tableVal.isTable()) {
            com.luatweaker.api.vm.ILuaValue entityVal = tableVal.asTable().rawget("__entity");
            if (entityVal != null && !entityVal.isNil()) {
                Object obj = entityVal.toJavaObject();
                if (obj instanceof com.luatweaker.api.entity.IEntity entity) {
                    return entity;
                }
            }
        }
        return null;
    }

    public static void registerBindings(ILuaEngine engine) {
        ILuaTable playersTable = engine.createTable();
        ILuaFunction getPlayersFn = args -> {
            java.util.List<com.luatweaker.api.entity.IPlayer> players = com.luatweaker.api.pal.Platform.getEntity().getAllPlayers();
            ILuaTable result = engine.createTable();
            for (int i = 0; i < players.size(); i++) {
                result.rawset(i + 1, createPlayerLuaTable(engine, players.get(i)));
            }
            return result;
        };
        playersTable.rawset("GetPlayers", getPlayersFn);
        playersTable.rawset("getPlayers", getPlayersFn);
        engine.registerService("Players", playersTable);
        engine.registerGlobal("Players", playersTable);
    }

    private static void bindMethod(ILuaTable table, String name, ILuaFunction func) {
        table.rawset(name, func);
        if (name != null && !name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
            String pascal = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            table.rawset(pascal, func);
        }
    }

    private static int getOffset(ILuaValue[] args) {
        if (args != null && args.length > 0 && args[0] != null && args[0].isTable()) {
            return 1;
        }
        return 0;
    }
}
