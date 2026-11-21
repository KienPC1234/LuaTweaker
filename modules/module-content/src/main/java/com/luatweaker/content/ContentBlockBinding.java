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
            builder.hardness((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "resistance", args -> {
            builder.resistance((float) args[LuaBinder.getOffset(args)].asDouble());
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
            builder.soundType(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "requiresTool", args -> {
            builder.requiresTool(args[LuaBinder.getOffset(args)].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "mineableWith", args -> {
            builder.mineableWith(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "friction", args -> {
            builder.friction((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
    }

    private static void bindBlockMining(ILuaTable table, IBlockBuilder builder) {
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
            builder.jumpFactor((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "speedFactor", args -> {
            builder.speedFactor((float) args[LuaBinder.getOffset(args)].asDouble());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "noCollision", args -> {
            builder.noCollision(args[LuaBinder.getOffset(args)].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "noOcclusion", args -> {
            builder.noOcclusion(args[LuaBinder.getOffset(args)].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "pushReaction", args -> {
            builder.pushReaction(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "replaceable", args -> {
            builder.replaceable(args[LuaBinder.getOffset(args)].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "ignitedByLava", args -> {
            builder.ignitedByLava(args[LuaBinder.getOffset(args)].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "flammable", args -> {
            builder.ignitedByLava(args[LuaBinder.getOffset(args)].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "liquid", args -> {
            builder.liquid(args[LuaBinder.getOffset(args)].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "offsetType", args -> {
            builder.offsetType(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "redstoneConductor", args -> {
            builder.redstoneConductor(args[LuaBinder.getOffset(args)].asBoolean());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "model", args -> {
            builder.model(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
    }




    private static void bindBlockMeta(ILuaTable table, IBlockBuilder builder) {
        ContentBindingSupport.bindMethod(table, "texture", args -> {
            builder.texture(args[LuaBinder.getOffset(args)].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, ContentBindingSupport.METHOD_DISPLAY_NAME, args -> {
            builder.displayName(args[LuaBinder.getOffset(args)].asString());
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
