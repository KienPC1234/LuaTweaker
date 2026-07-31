package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.vm.*;

public class EntitiesLuaBinding {

    public static ILuaTable createPlayerLuaTable(ILuaEngine engine, IPlayer player) {
        ILuaTable table = createEntityLuaTable(engine, player);

        table.rawset("sendMessage", args -> {
            if (args.length >= 2) {
                player.sendMessage(args[1].asString());
            }
            return null;
        });

        table.rawset("sendActionBar", args -> {
            if (args.length >= 2) {
                player.sendActionBar(args[1].asString());
            }
            return null;
        });

        table.rawset("getName", args -> engine.wrapString(player.getName()));
        table.rawset("getUuid", args -> engine.wrapString(player.getUuid()));
        table.rawset("isSneaking", args -> engine.wrapBoolean(player.isSneaking()));
        table.rawset("isCreative", args -> engine.wrapBoolean(player.isCreative()));
        table.rawset("getHealth", args -> engine.wrapNumber(player.getHealth()));
        table.rawset("setHealth", args -> {
            if (args.length >= 2) player.setHealth((float) args[1].asDouble());
            return null;
        });

        table.rawset("giveItem", args -> {
            if (args.length >= 2) {
                String id = args[1].asString();
                int count = args.length >= 3 ? args[2].asInt() : 1;
                player.giveItem(id, count);
            }
            return null;
        });

        table.rawset("giveExperience", args -> {
            if (args.length >= 2) player.giveExperience(args[1].asInt());
            return null;
        });

        table.rawset("addEffect", args -> {
            if (args.length >= 2) {
                String effect = args[1].asString();
                int duration = args.length >= 3 ? args[2].asInt() : 200;
                int amp = args.length >= 4 ? args[3].asInt() : 0;
                player.addEffect(effect, duration, amp);
            }
            return null;
        });

        table.rawset("playSound", args -> {
            if (args.length >= 2) {
                String soundId = args[1].asString();
                float volume = args.length >= 3 ? (float) args[2].asDouble() : 1.0f;
                float pitch  = args.length >= 4 ? (float) args[3].asDouble() : 1.0f;
                player.playSound(soundId, volume, pitch);
            }
            return null;
        });

        table.rawset("getMainHandItem", args -> engine.wrapString(player.getMainHandItem()));
        table.rawset("getOffHandItem", args -> engine.wrapString(player.getOffHandItem()));
        table.rawset("setMainHandItem", args -> {
            if (args.length >= 2) {
                String itemId = args[1].asString();
                int count = args.length >= 3 ? args[2].asInt() : 1;
                player.setMainHandItem(itemId, count);
            }
            return null;
        });
        table.rawset("clearInventory", args -> { player.clearInventory(); return null; });
        table.rawset("dropItem", args -> {
            if (args.length >= 2) {
                String itemId = args[1].asString();
                int count = args.length >= 3 ? args[2].asInt() : 1;
                player.dropItem(itemId, count);
            }
            return null;
        });
        table.rawset("actionText", args -> {
            if (args.length >= 2) {
                player.sendActionBar(args[1].asString());
            }
            return null;
        });

        table.rawset("sendTitle", args -> {
            if (args.length >= 2) {
                String title = args[1].asString();
                String subtitle = args.length >= 3 ? args[2].asString() : "";
                int fadeIn = args.length >= 4 ? args[3].asInt() : 10;
                int stay = args.length >= 5 ? args[4].asInt() : 70;
                int fadeOut = args.length >= 6 ? args[5].asInt() : 20;
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            }
            return null;
        });

        table.rawset("heal", args -> {
            if (args.length >= 2) player.heal((float) args[1].asDouble());
            return null;
        });

        table.rawset("feed", args -> {
            if (args.length >= 2) {
                int food = args[1].asInt();
                float sat = args.length >= 3 ? (float) args[2].asDouble() : 1.0f;
                player.feed(food, sat);
            }
            return null;
        });

        table.rawset("teleport", args -> {
            if (args.length >= 4) {
                double x = args[1].asDouble();
                double y = args[2].asDouble();
                double z = args[3].asDouble();
                player.teleport(x, y, z);
            }
            return null;
        });

        return table;
    }

    public static ILuaTable createEntityLuaTable(ILuaEngine engine, IEntity entity) {
        ILuaTable table = engine.createTable();
        table.rawset("__entity", engine.wrapUserdata(entity));
        table.rawset("getType", args -> engine.wrapString(entity.getType()));
        table.rawset("getName", args -> engine.wrapString(entity.getName()));
        table.rawset("getHealth", args -> engine.wrapNumber(entity.getHealth()));
        table.rawset("getMaxHealth", args -> engine.wrapNumber(entity.getMaxHealth()));
        table.rawset("setHealth", args -> {
            if (args.length >= 2) entity.setHealth((float) args[1].asDouble());
            return null;
        });

        table.rawset("heal", args -> {
            if (args.length >= 2) entity.heal((float) args[1].asDouble());
            return null;
        });
        table.rawset("kill", args -> { entity.kill(); return null; });

        table.rawset("damage", args -> {
            if (args.length >= 2) entity.damage((float) args[1].asDouble());
            return null;
        });
        table.rawset("hurt", args -> {
            if (args.length >= 2) entity.damage((float) args[1].asDouble());
            return null;
        });

        table.rawset("addEffect", args -> {
            if (args.length >= 2) {
                String effect = args[1].asString();
                int duration = args.length >= 3 ? args[2].asInt() : 200;
                int amp = args.length >= 4 ? args[3].asInt() : 0;
                entity.addEffect(effect, duration, amp);
            }
            return null;
        });
        table.rawset("removeEffect", args -> {
            if (args.length >= 2) entity.removeEffect(args[1].asString());
            return null;
        });
        table.rawset("removeAllEffects", args -> { entity.removeAllEffects(); return null; });
        table.rawset("hasEffect", args -> {
            if (args.length >= 2) return engine.wrapBoolean(entity.hasEffect(args[1].asString()));
            return engine.wrapBoolean(false);
        });

        table.rawset("setIgniteSeconds", args -> {
            if (args.length >= 2) entity.setIgniteSeconds(args[1].asInt());
            return null;
        });
        table.rawset("extinguish", args -> { entity.extinguish(); return null; });

        table.rawset("playSound", args -> {
            if (args.length >= 2) {
                String soundId = args[1].asString();
                float volume = args.length >= 3 ? (float) args[2].asDouble() : 1.0f;
                float pitch  = args.length >= 4 ? (float) args[3].asDouble() : 1.0f;
                entity.playSound(soundId, volume, pitch);
            }
            return null;
        });

        table.rawset("spawnParticle", args -> {
            if (args.length >= 2) {
                String particleId = args[1].asString();
                int count = args.length >= 3 ? args[2].asInt() : 1;
                double speed = args.length >= 4 ? args[3].asDouble() : 0.0;
                entity.spawnParticle(particleId, count, speed);
            }
            return null;
        });

        table.rawset("shootProjectile", args -> {
            if (args.length >= 2) {
                String projectileType = args[1].asString();
                double speed = args.length >= 3 ? args[2].asDouble() : 1.5;
                double inaccuracy = args.length >= 4 ? args[3].asDouble() : 0.0;
                com.luatweaker.api.pal.Platform.get().shootProjectile(entity, projectileType, speed, inaccuracy);
            }
            return null;
        });

        table.rawset("shootProjectileAt", args -> {
            if (args.length >= 3) {
                String projectileType = args[1].asString();
                com.luatweaker.api.entity.IEntity target = getEntityFromTable(args[2]);
                double speed = args.length >= 4 ? args[3].asDouble() : 1.5;
                if (target != null) {
                    com.luatweaker.api.pal.Platform.get().shootProjectileAt(entity, projectileType, target, speed);
                }
            }
            return null;
        });

        table.rawset("playAnimation", args -> {
            if (args.length >= 2) {
                String animName = args[1].asString();
                double speed = args.length >= 3 ? args[2].asDouble() : 1.0;
                double transition = args.length >= 4 ? args[3].asDouble() : 0.1;
                com.luatweaker.api.pal.Platform.get().playAnimation(entity, animName, speed, transition);
            }
            return null;
        });

        table.rawset("teleport", args -> {
            if (args.length >= 4) entity.teleport(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            return null;
        });
        table.rawset("setPos", args -> {
            if (args.length >= 4) entity.teleport(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            return null;
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
            return null;
        });
        table.rawset("addVelocity", args -> {
            if (args.length >= 4) entity.addVelocity(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            return null;
        });

        table.rawset("getX", args -> engine.wrapNumber(entity.getX()));
        table.rawset("getY", args -> engine.wrapNumber(entity.getY()));
        table.rawset("getZ", args -> engine.wrapNumber(entity.getZ()));
        table.rawset("getYaw", args -> engine.wrapNumber(entity.getYaw()));
        table.rawset("getPitch", args -> engine.wrapNumber(entity.getPitch()));
        table.rawset("setYaw", args -> { if (args.length >= 2) entity.setYaw((float) args[1].asDouble()); return null; });
        table.rawset("setPitch", args -> { if (args.length >= 2) entity.setPitch((float) args[1].asDouble()); return null; });

        table.rawset("isSneaking", args -> engine.wrapBoolean(entity.isSneaking()));
        table.rawset("setSneaking", args -> { if (args.length >= 2) entity.setSneaking(args[1].asBoolean()); return null; });
        table.rawset("isSprinting", args -> engine.wrapBoolean(entity.isSprinting()));
        table.rawset("setSprinting", args -> { if (args.length >= 2) entity.setSprinting(args[1].asBoolean()); return null; });
        table.rawset("isGlowing", args -> engine.wrapBoolean(entity.isGlowing()));
        table.rawset("setGlowing", args -> { if (args.length >= 2) entity.setGlowing(args[1].asBoolean()); return null; });
        table.rawset("isInvulnerable", args -> engine.wrapBoolean(entity.isInvulnerable()));
        table.rawset("setInvulnerable", args -> { if (args.length >= 2) entity.setInvulnerable(args[1].asBoolean()); return null; });
        table.rawset("isInWater", args -> engine.wrapBoolean(entity.isInWater()));
        table.rawset("isInLava", args -> engine.wrapBoolean(entity.isInLava()));
        table.rawset("isOnGround", args -> engine.wrapBoolean(entity.isOnGround()));

        table.rawset("getCustomName", args -> engine.wrapString(entity.getCustomName()));
        table.rawset("setCustomName", args -> { if (args.length >= 2) entity.setCustomName(args[1].asString()); return null; });
        table.rawset("isCustomNameVisible", args -> engine.wrapBoolean(entity.isCustomNameVisible()));
        table.rawset("setCustomNameVisible", args -> { if (args.length >= 2) entity.setCustomNameVisible(args[1].asBoolean()); return null; });

        table.rawset("addTag", args -> { if (args.length >= 2) entity.addTag(args[1].asString()); return null; });
        table.rawset("removeTag", args -> { if (args.length >= 2) entity.removeTag(args[1].asString()); return null; });
        table.rawset("hasTag", args -> { if (args.length >= 2) return engine.wrapBoolean(entity.hasTag(args[1].asString())); return engine.wrapBoolean(false); });

        table.rawset("swingArm", args -> { entity.swingArm(); return null; });

        table.rawset("isPlayer", args -> engine.wrapBoolean(entity.isPlayer()));
        table.rawset("isLiving", args -> engine.wrapBoolean(entity.isLiving()));
        table.rawset("isAlive", args -> engine.wrapBoolean(entity.isAlive()));
        table.rawset("remove", args -> { entity.remove(); return null; });

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
}
