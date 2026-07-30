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

        return table;
    }

    public static ILuaTable createEntityLuaTable(ILuaEngine engine, IEntity entity) {
        ILuaTable table = engine.createTable();
        table.rawset("getType", args -> engine.wrapString(entity.getType()));
        table.rawset("getName", args -> engine.wrapString(entity.getName()));
        table.rawset("getHealth", args -> engine.wrapNumber(entity.getHealth()));
        table.rawset("getMaxHealth", args -> engine.wrapNumber(entity.getMaxHealth()));
        table.rawset("setHealth", args -> {
            if (args.length >= 2) entity.setHealth((float) args[1].asDouble());
            return null;
        });

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
        table.rawset("removeAllEffects", args -> { entity.removeAllEffects(); return null; });

        table.rawset("setIgniteSeconds", args -> {
            if (args.length >= 2) entity.setIgniteSeconds(args[1].asInt());
            return null;
        });

        table.rawset("playSound", args -> {
            if (args.length >= 2) {
                String soundId = args[1].asString();
                float volume = args.length >= 3 ? (float) args[2].asDouble() : 1.0f;
                float pitch  = args.length >= 4 ? (float) args[3].asDouble() : 1.0f;
                entity.playSound(soundId, volume, pitch);
            }
            return null;
        });

        table.rawset("teleport", args -> {
            if (args.length >= 4) entity.teleport(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            return null;
        });

        table.rawset("setMotion", args -> {
            if (args.length >= 4) entity.setMotion(args[1].asDouble(), args[2].asDouble(), args[3].asDouble());
            return null;
        });

        table.rawset("getX", args -> engine.wrapNumber(entity.getX()));
        table.rawset("getY", args -> engine.wrapNumber(entity.getY()));
        table.rawset("getZ", args -> engine.wrapNumber(entity.getZ()));

        table.rawset("isPlayer", args -> engine.wrapBoolean(entity.isPlayer()));
        table.rawset("isLiving", args -> engine.wrapBoolean(entity.isLiving()));
        table.rawset("isAlive", args -> engine.wrapBoolean(entity.isAlive()));
        table.rawset("remove", args -> { entity.remove(); return null; });
        return table;
    }
}
