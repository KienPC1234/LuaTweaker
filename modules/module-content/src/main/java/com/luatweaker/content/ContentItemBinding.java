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
            builder.type(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "maxStackSize", args -> {
            builder.maxStackSize(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "rarity", args -> {
            builder.rarity(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "durability", args -> {
            builder.durability(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "maxDamage", args -> {
            builder.durability(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "miningSpeed", args -> {
            builder.miningSpeed((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "efficiency", args -> {
            builder.miningSpeed((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "attackDamage", args -> {
            builder.attackDamage((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "attackSpeed", args -> {
            builder.attackSpeed((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "defense", args -> {
            builder.defense(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "toughness", args -> {
            builder.toughness((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "knockbackResistance", args -> {
            builder.knockbackResistance((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "enchantability", args -> {
            builder.enchantability(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "burnTime", args -> {
            builder.burnTime(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_DISPLAY_NAME, args -> {
            builder.displayName(args[LuaBinder.getOffset(args)].asString());
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
        ContentBindingSupport.bindMethod(table, "armorMaterial", args -> {
            builder.armorMaterial(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "armorTexture", args -> {
            builder.armorTexture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        bindCreativeTabAndTag(table, builder, datapackService);
    }

    private static void bindCreativeTabAndTag(ILuaTable table, IItemBuilder builder,
            IDatapackService datapackService) {
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_CREATIVE_TAB, args -> {
            builder.creativeTab(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "tabGroup", args -> {
            builder.creativeTab(args[LuaBinder.getOffset(args)].asString());
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
            builder.useAnimation(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "useDuration", args -> {
            builder.useDuration(args[LuaBinder.getOffset(args)].asInt());
            return table;
        });
    }

    private static void bindItemGeo(ILuaTable table, IItemBuilder builder) {
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
}
