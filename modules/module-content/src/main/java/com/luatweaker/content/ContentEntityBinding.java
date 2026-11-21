package com.luatweaker.content;

import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds the Lua entity-builder and fluid-builder tables. */
final class ContentEntityBinding {

    private ContentEntityBinding() {
    }

    static void bindEntityBuilderMethods(ILuaEngine engine, ILuaTable table, IEntityBuilder builder) {
        bindEntityBasics(table, builder);
        bindEntityOnTick(engine, table, builder);
        bindEntityOnHurt(engine, table, builder);
        bindEntityOnDeath(engine, table, builder);
        bindEntityOnInteract(engine, table, builder);
        bindEntityBossBar(table, builder);
        bindEntityGeo(table, builder);
    }

    private static void bindEntityBasics(ILuaTable table, IEntityBuilder builder) {
        ContentBindingSupport.bindMethod(table, "category", args -> {
            builder.category(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "dimensions", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 2)) {
                builder.dimensions((float) args[off].asDouble(), (float) args[off + 1].asDouble());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "maxHealth", args -> {
            builder.maxHealth(args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "movementSpeed", args -> {
            builder.movementSpeed(args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "speed", args -> {
            builder.movementSpeed(args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "attackDamage", args -> {
            builder.attackDamage(args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "followRange", args -> {
            builder.followRange(args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "armor", args -> {
            builder.armor(args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "knockbackResistance", args -> {
            builder.knockbackResistance(args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "spawnEgg", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 2)) {
                builder.spawnEgg(args[off].asInt(), args[off + 1].asInt());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "model", args -> {
            builder.model(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "texture", args -> {
            builder.texture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "bbmodel", args -> {
            builder.bbmodel(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "ambientSound", args -> {
            builder.ambientSound(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "hurtSound", args -> {
            builder.hurtSound(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "deathSound", args -> {
            builder.deathSound(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "drop", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                String itemId = args[off].asString();
                int minCount = ContentBindingSupport.hasMinArgs(args, off, 2)
                        ? args[off + 1].asInt() : ContentBindingSupport.DEFAULT_COUNT;
                int maxCount = ContentBindingSupport.hasMinArgs(args, off, 3) ? args[off + 2].asInt() : minCount;
                builder.drop(itemId, minCount, maxCount);
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "experience", args -> {
            builder.experience(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_CREATIVE_TAB, args -> {
            builder.creativeTab(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "spawnEggTexture", args -> {
            builder.spawnEggTexture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "eggTexture", args -> {
            builder.spawnEggTexture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "parent", args -> {
            builder.parent(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "parentMob", args -> {
            builder.parentMob(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "rig", args -> {
            builder.rig(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "defaultGoals", args -> {
            builder.defaultGoals(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "renderScale", args -> {
            builder.renderScale(args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "fireImmune", args -> {
            int off = LuaBinder.getOffset(args);
            builder.fireImmune(!ContentBindingSupport.hasMinArgs(args, off, 1) || args[off].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "tickRate", args -> {
            builder.tickRate(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
    }

    private static void bindEntityOnTick(ILuaEngine engine, ILuaTable table, IEntityBuilder builder) {
        ContentBindingSupport.bindMethod(table, "onTick", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onTick(entity -> {
                    try {
                        engine.callFunction(func,
                                com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, entity));
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing entity onTick callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });
    }

    private static void bindEntityOnHurt(ILuaEngine engine, ILuaTable table, IEntityBuilder builder) {
        ContentBindingSupport.bindMethod(table, "onHurt", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onHurt((entity, amount, attacker) -> {
                    try {
                        ILuaValue entityVal =
                                com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, entity);
                        ILuaValue attackerVal = wrapAttacker(engine, attacker);
                        ILuaValue result = engine.callFunction(func, entityVal,
                                engine.wrapNumber(amount), attackerVal);
                        if (result != null && !result.isNil() && result.isNumber()) {
                            return (float) result.asDouble();
                        }
                        return null;
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing entity onHurt callback: " + e.getMessage());
                        return null;
                    }
                });
            }
            return table;
        });
    }

    private static ILuaValue wrapAttacker(ILuaEngine engine, Object attacker) {
        if (attacker instanceof com.luatweaker.api.entity.IEntity entityAttacker) {
            return com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, entityAttacker);
        }
        return engine.nilValue();
    }

    private static void bindEntityOnDeath(ILuaEngine engine, ILuaTable table, IEntityBuilder builder) {
        ContentBindingSupport.bindMethod(table, "onDeath", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onDeath((entity, killer) -> {
                    try {
                        ILuaValue entityVal =
                                com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, entity);
                        ILuaValue killerVal = killer instanceof com.luatweaker.api.entity.IEntity entityKiller
                                ? com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, entityKiller)
                                : engine.nilValue();
                        ILuaValue result = engine.callFunction(func, entityVal, killerVal);
                        return result != null && !result.isNil() && result.asBoolean();
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing entity onDeath callback: " + e.getMessage());
                        return false;
                    }
                });
            }
            return table;
        });
    }

    private static void bindEntityOnInteract(ILuaEngine engine, ILuaTable table, IEntityBuilder builder) {
        ContentBindingSupport.bindMethod(table, "onInteract", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onInteract((entity, player) -> {
                    try {
                        ILuaValue entityVal =
                                com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, entity);
                        ILuaValue playerVal =
                                com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, player);
                        ILuaValue result = engine.callFunction(func, entityVal, playerVal);
                        return result != null && !result.isNil() && result.asBoolean();
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing entity onInteract callback: " + e.getMessage());
                        return false;
                    }
                });
            }
            return table;
        });
    }

    private static void bindEntityBossBar(ILuaTable table, IEntityBuilder builder) {
        ContentBindingSupport.bindMethod(table, "bossBar", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                String title = args[off].asString();
                String color = ContentBindingSupport.hasMinArgs(args, off, 2)
                        ? args[off + 1].asString() : ContentBindingSupport.DEFAULT_BOSS_BAR_COLOR;
                String overlay = ContentBindingSupport.hasMinArgs(args, off, 3)
                        ? args[off + 2].asString() : ContentBindingSupport.DEFAULT_BOSS_BAR_OVERLAY;
                builder.bossBar(title, color, overlay);
            }
            return table;
        });
    }

    private static void bindEntityGeo(ILuaTable table, IEntityBuilder builder) {
        ContentBindingSupport.bindMethod(table, "geoModel", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 3)) {
                String modelPath = args[off].asString();
                String texturePath = args[off + 1].asString();
                String animPath = args[off + 2].asString();
                String defaultAnim = ContentBindingSupport.hasMinArgs(args, off, 4) && !args[off + 3].isNil()
                        ? args[off + 3].asString() : null;
                builder.geoModel(modelPath, texturePath, animPath, defaultAnim);
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "defaultAnimation", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1) && builder.getGeoModel() != null) {
                var prev = builder.getGeoModel();
                builder.geoModel(prev.modelPath(), prev.texturePath(), prev.animationPath(), args[off].asString());
            }
            return table;
        });
    }

    static void bindFluidBuilderMethods(ILuaEngine engine, ILuaTable table, IFluidBuilder builder) {
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_DISPLAY_NAME, args -> {
            builder.displayName(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "name", args -> {
            builder.displayName(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "color", args -> {
            builder.color(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "stillTexture", args -> {
            builder.stillTexture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "still", args -> {
            builder.stillTexture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "flowingTexture", args -> {
            builder.flowingTexture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "flowing", args -> {
            builder.flowingTexture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "flow", args -> {
            builder.flowingTexture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "temperature", args -> {
            builder.temperature(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "viscosity", args -> {
            builder.viscosity(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "density", args -> {
            builder.density(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "lightLevel", args -> {
            builder.lightLevel(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "light", args -> {
            builder.lightLevel(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "luminance", args -> {
            builder.lightLevel(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "slopeFindDistance", args -> {
            builder.slopeFindDistance(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "levelDecreasePerBlock", args -> {
            builder.levelDecreasePerBlock(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "tickRate", args -> {
            builder.tickRate(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "explosionResistance", args -> {
            builder.explosionResistance((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "rarity", args -> {
            builder.rarity(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_CREATIVE_TAB, args -> {
            builder.creativeTab(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "tabGroup", args -> {
            builder.creativeTab(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onTouch", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onTouch(player -> {
                    try {
                        ILuaValue playerVal =
                                com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, player);
                        engine.callFunction(func, playerVal);
                    } catch (IllegalStateException e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM,
                                "Error executing fluid onTouch callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });
    }
}
