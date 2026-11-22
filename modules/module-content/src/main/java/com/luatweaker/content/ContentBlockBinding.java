package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds the Lua block-builder table (startup:createBlock family and NewBlock). */
final class ContentBlockBinding {

    private ContentBlockBinding() {
    }

    static void bindBlockBuilderMethods(ILuaEngine engine, ILuaTable table, IBlockBuilder builder,
            IDatapackService datapackService) {
        bindBlockBasics(table, builder);
        bindBlockMining(table, builder);
        bindBlockProperties(table, builder);
        ContentBlockContainerBinding.bindBlockDrops(table, builder);
        ContentBlockContainerBinding.bindBlockContainer(table, builder);
        ContentBlockContainerBinding.bindBlockItemFilter(engine, table, builder);
        ContentBlockContainerBinding.bindBlockSlots(table, builder);
        ContentBlockContainerBinding.bindBlockSlotLocks(table, builder);
        ContentBlockMachineBinding.bindBlockEnergy(table, builder);
        ContentBlockRuntimeBinding.bindBlockState(engine, table, builder);
        ContentBlockRuntimeBinding.bindBlockGuiShapes(table, builder);
        ContentBlockRuntimeBinding.bindBlockRender(engine, table, builder);
        ContentBlockRuntimeBinding.bindBlockSignals(engine, table, builder);
        bindBlockMeta(table, builder);
        bindBlockTagInteract(engine, table, builder, datapackService);
    }

    private static void bindBlockBasics(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "hardness", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:hardness");
            builder.hardness((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "resistance", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:resistance");
            builder.resistance((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "lightLevel", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:lightLevel");
            builder.lightLevel(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "light", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:light");
            builder.lightLevel(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "emissive", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                Object val = args[off].toJavaObject();
                if (val instanceof Number num) {
                    builder.lightLevel(Math.min(15, Math.max(0, num.intValue())));
                } else if (val instanceof Boolean light) {
                    builder.lightLevel(light ? 15 : 0);
                } else {
                    builder.lightLevel(args[off].asInt());
                }
            } else {
                builder.lightLevel(15);
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "soundType", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:soundType");
            builder.soundType(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "requiresTool", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:requiresTool");
            builder.requiresTool(args[off].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "mineableWith", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:mineableWith");
            builder.mineableWith(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "friction", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:friction");
            builder.friction((float) args[off].asDouble());
            return table;
        });
    }

    private static void bindBlockMining(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "miningLevel", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:miningLevel");
            Object obj = args[off].toJavaObject();
            if (obj instanceof Number num) {
                builder.miningLevel(num.intValue());
            } else {
                builder.miningLevel(args[off].asString());
            }
            return table;
        });
    }

    private static void bindBlockProperties(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "mapColor", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                Object obj = args[off].toJavaObject();
                if (obj instanceof Number num) {
                    builder.mapColor(String.valueOf(num.intValue()));
                } else {
                    builder.mapColor(args[off].asString());
                }
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "jumpFactor", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:jumpFactor");
            builder.jumpFactor((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "speedFactor", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:speedFactor");
            builder.speedFactor((float) args[off].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "noCollision", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:noCollision");
            builder.noCollision(args[off].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "noOcclusion", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:noOcclusion");
            builder.noOcclusion(args[off].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "pushReaction", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:pushReaction");
            builder.pushReaction(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "replaceable", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:replaceable");
            builder.replaceable(args[off].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "ignitedByLava", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:ignitedByLava");
            builder.ignitedByLava(args[off].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "flammable", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:flammable");
            builder.ignitedByLava(args[off].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "liquid", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:liquid");
            builder.liquid(args[off].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "offsetType", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:offsetType");
            builder.offsetType(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "redstoneConductor", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:redstoneConductor");
            builder.redstoneConductor(args[off].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "model", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:model");
            builder.model(args[off].asString());
            return table;
        });
    }




    private static void bindBlockMeta(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "texture", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:texture");
            builder.texture(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_DISPLAY_NAME, args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:displayName");
            builder.displayName(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_CREATIVE_TAB, args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:creativeTab");
            builder.creativeTab(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "tabGroup", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "block:tabGroup");
            builder.creativeTab(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "geoModel", args -> {
            int off = LuaBinder.getOffset(args);
            String[] geo = GeoModelArgs.parse(args, off, "block:geoModel");
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

    private static void bindBlockTagInteract(ILuaEngine engine, ILuaTable table, IBlockBuilder builder,
            IDatapackService datapackService) {
        ContentBindingSupport.bindMethod(table, "tag", args -> {
            int off = LuaBinder.getOffset(args);
            if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
                String tagId = args[off].asString();
                builder.tag(tagId);
                if (datapackService != null) {
                    String fullId = builder.getId().contains(":") ? builder.getId() : "luatweaker:" + builder.getId();
                    datapackService.addTag(ContentBindingSupport.TAG_TYPE_BLOCK, tagId, java.util.List.of(fullId));
                }
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "onRightClick", args -> {
            int off = LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onRightClick((player, blockState) -> ContentBindingSupport.invokeCallback(
                        engine, "block rightClick", func,
                        ContentBindingSupport.wrapActor(engine, player),
                        engine.toLuaValue(blockState)));
            }
            return table;
        });
    }
}
