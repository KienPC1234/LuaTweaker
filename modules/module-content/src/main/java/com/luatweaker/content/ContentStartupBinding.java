package com.luatweaker.content;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.content.ProjectileDefinition;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Registers the "startup"/"Content" creation table: items, blocks, fluids, entities, tabs, key mappings. */
final class ContentStartupBinding {

    private ContentStartupBinding() {
    }

    static ILuaTable registerStartupBindings(ILuaEngine engine, IContentService contentService,
            IDatapackService datapackService) {
        ILuaTable startupTable = engine.createTable();
        bindItemCreators(engine, startupTable, contentService, datapackService);
        ContentBlockStartupBinding.bindBlockCreators(engine, startupTable, contentService, datapackService);
        ContentBlockStartupBinding.bindSimpleCreators(engine, startupTable, contentService, datapackService);
        ContentTabStartupBinding.bindTabAndMaterialCreators(engine, startupTable, contentService);
        bindShortcuts(engine, startupTable);
        ContentKeyMappingBinding.bindKeyMappingCreator(engine, startupTable);
        return startupTable;
    }

    private static void bindItemCreators(ILuaEngine engine, ILuaTable startupTable,
            IContentService contentService, IDatapackService datapackService) {
        bindCreationHelper(engine, startupTable, "createItem", datapackService, contentService::createItem);
        bindCreationHelper(engine, startupTable, "createSword", datapackService, contentService::createSword);
        bindCreationHelper(engine, startupTable, "createPickaxe", datapackService, contentService::createPickaxe);
        bindCreationHelper(engine, startupTable, "createAxe", datapackService, contentService::createAxe);
        bindCreationHelper(engine, startupTable, "createShovel", datapackService, contentService::createShovel);
        bindCreationHelper(engine, startupTable, "createHoe", datapackService, contentService::createHoe);
        bindCreationHelper(engine, startupTable, "createHelmet", datapackService, contentService::createHelmet);
        bindCreationHelper(engine, startupTable, "createChestplate", datapackService, contentService::createChestplate);
        bindCreationHelper(engine, startupTable, "createLeggings", datapackService, contentService::createLeggings);
        bindCreationHelper(engine, startupTable, "createBoots", datapackService, contentService::createBoots);
        bindRegisterProjectile(startupTable);
    }

    private static void bindRegisterProjectile(ILuaTable startupTable) {
        startupTable.rawset("registerProjectile", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("startup:registerProjectile requires (id, [configTable])");
            }
            String id = args[off].asString();
            ProjectileDefinition definition = readProjectileConfig(args, off);
            ProjectileRegistry.register(id, definition);
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "Registered Custom Projectile definition: " + id + " (damage=" + definition.damage()
                            + ", explosionPower=" + definition.explosionPower() + ")");
            return null;
        });
    }

    private static ProjectileDefinition readProjectileConfig(ILuaValue[] args, int off) {
        double damage = 0;
        double explosionPower = 0;
        String trailParticle = "";
        if (ContentBindingSupport.hasMinArgs(args, off, 2) && args[off + 1].isTable()) {
            ILuaTable cfg = args[off + 1].asTable();
            ILuaValue dmgVal = cfg.rawget("damage");
            if (dmgVal != null && !dmgVal.isNil()) {
                damage = dmgVal.asDouble();
            }
            ILuaValue expVal = cfg.rawget("explosionPower");
            if (expVal != null && !expVal.isNil()) {
                explosionPower = expVal.asDouble();
            }
            ILuaValue trailVal = cfg.rawget("trailParticle");
            if (trailVal != null && !trailVal.isNil()) {
                trailParticle = trailVal.asString();
            }
        }
        return new ProjectileDefinition(damage, explosionPower, trailParticle);
    }

    private static void bindCreationHelper(ILuaEngine engine, ILuaTable startupTable, String name,
            IDatapackService datapackService, BiFunction<String, Consumer<IItemBuilder>, IItemBuilder> creator) {
        startupTable.rawset(name, args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("startup:" + name + " requires (id, builderFunc)");
            }
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            creator.apply(id, builder -> {
                if (callbackVal.isFunction()) {
                    ILuaTable itemTable = engine.createTable();
                    ContentItemBinding.bindItemBuilderMethods(engine, itemTable, builder, datapackService);
                    ContentBindingSupport.invokeBuilderCallback(engine, "item", id, callbackVal, itemTable);
                }
            });

            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Item builder (" + name + "): " + id);
            return null;
        });
    }


    private static void bindShortcuts(ILuaEngine engine, ILuaTable startupTable) {
        startupTable.rawset("Item", args -> {
            int off = LuaBinder.getOffset(args);
            String id = args[off].asString();
            int count = ContentBindingSupport.hasMinArgs(args, off, 2)
                    ? args[off + 1].asInt() : ContentBindingSupport.DEFAULT_COUNT;
            return engine.toLuaValue(new com.luatweaker.api.wrapper.ItemCount(id, count));
        });

        startupTable.rawset("Ingredient", args -> {
            int off = LuaBinder.getOffset(args);
            String desc = args[off].asString();
            return engine.toLuaValue(new com.luatweaker.api.wrapper.IngredientWrapper(desc));
        });

        startupTable.rawset("Tag", args -> {
            int off = LuaBinder.getOffset(args);
            String desc = args[off].asString();
            if (!desc.startsWith("#")) {
                desc = "#" + desc;
            }
            return engine.toLuaValue(new com.luatweaker.api.wrapper.IngredientWrapper(desc));
        });

        startupTable.rawset("OreDict", startupTable.rawget("Tag"));
    }
}
