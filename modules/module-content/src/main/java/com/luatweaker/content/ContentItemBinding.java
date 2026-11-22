package com.luatweaker.content;

import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds the Lua item-builder table (startup:createItem family and NewItem). */
final class ContentItemBinding {

    private ContentItemBinding() {
    }

    static void bindItemBuilderMethods(ILuaEngine engine, ILuaTable table, IItemBuilder builder,
            IDatapackService datapackService) {
        bindItemBasics(table, builder, datapackService);
        bindItemMining(table, builder);
        bindItemFood(engine, table, builder);
        ContentItemHandlersBinding.bindItemUseHandlers(engine, table, builder);
        ContentItemHandlersBinding.bindItemCombatHandlers(engine, table, builder);
        bindItemGeo(table, builder);
    }

    private static void bindItemBasics(ILuaTable table, IItemBuilder builder,
            IDatapackService datapackService) {
        ContentBindingSupport.bindMethod(table, "type", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:type");
            builder.type(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "maxStackSize", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:maxStackSize");
            builder.maxStackSize(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "rarity", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:rarity");
            builder.rarity(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "durability", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:durability");
            builder.durability(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "maxDamage", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:maxDamage");
            builder.durability(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "miningSpeed", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:miningSpeed");
            builder.miningSpeed((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "efficiency", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:efficiency");
            builder.miningSpeed((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "attackDamage", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:attackDamage");
            builder.attackDamage((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "attackSpeed", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:attackSpeed");
            builder.attackSpeed((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "defense", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:defense");
            builder.defense(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "toughness", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:toughness");
            builder.toughness((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "knockbackResistance", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:knockbackResistance");
            builder.knockbackResistance((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "enchantability", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:enchantability");
            builder.enchantability(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "burnTime", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:burnTime");
            builder.burnTime(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_DISPLAY_NAME, args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:displayName");
            builder.displayName(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "model", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:model");
            builder.model(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "texture", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:texture");
            builder.texture(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "armorMaterial", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:armorMaterial");
            builder.armorMaterial(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "armorTexture", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:armorTexture");
            builder.armorTexture(args[off].asString());
            return table;
        });
        bindCreativeTabAndTag(table, builder, datapackService);
    }

    private static void bindCreativeTabAndTag(ILuaTable table, IItemBuilder builder,
            IDatapackService datapackService) {
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_CREATIVE_TAB, args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:creativeTab");
            builder.creativeTab(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "tabGroup", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:tabGroup");
            builder.creativeTab(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "tag", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                String tagId = args[off].asString();
                builder.tag(tagId);
                if (datapackService != null) {
                    String fullId = builder.getId().contains(":") ? builder.getId() : "luatweaker:" + builder.getId();
                    datapackService.addTag(ContentBindingSupport.TAG_TYPE_ITEM, tagId, java.util.List.of(fullId));
                }
            }
            return table;
        });
    }

    private static void bindItemMining(ILuaTable table, IItemBuilder builder) {
        ContentBindingSupport.bindMethod(table, "miningLevel", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:miningLevel");
            Object obj = args[off].toJavaObject();
            if (obj instanceof Number num) {
                builder.miningLevel(num.intValue());
            } else {
                builder.miningLevel(args[off].asString());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "tier", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:tier");
            Object obj = args[off].toJavaObject();
            if (obj instanceof Number num) {
                builder.miningLevel(num.intValue());
            } else {
                builder.miningLevel(args[off].asString());
            }
            return table;
        });
    }

    private static void bindItemFood(ILuaEngine engine, ILuaTable table, IItemBuilder builder) {
        ContentBindingSupport.bindMethod(table, "food", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 2, "item:food");
            builder.food(args[off].asInt(), (float) args[off + 1].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "alwaysEdible", args -> {
            builder.alwaysEdible();
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onConsume", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onConsume((player, itemStack) -> ContentBindingSupport.invokeCallback(
                        engine, "item onConsume", func,
                        ContentBindingSupport.wrapActor(engine, player),
                        ContentBindingSupport.wrapItemValue(engine, itemStack)));
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "glow", args -> {
            int off = LuaBinder.getOffset(args);
            boolean enable = !ContentBindingSupport.hasMinArgs(args, off, 1) || args[off].asBoolean();
            builder.glow(enable);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "tooltip", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                builder.tooltip(args[off].asString());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "useAnimation", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:useAnimation");
            builder.useAnimation(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "useDuration", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "item:useDuration");
            builder.useDuration(args[off].asInt());
            return table;
        });
    }

    private static void bindItemGeo(ILuaTable table, IItemBuilder builder) {
        ContentBindingSupport.bindMethod(table, "geoModel", args -> {
            int off = LuaBinder.getOffset(args);
            String[] geo = GeoModelArgs.parse(args, off, "item:geoModel");
            builder.geoModel(geo[0], geo[1], geo[2], geo[3]);
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
}
