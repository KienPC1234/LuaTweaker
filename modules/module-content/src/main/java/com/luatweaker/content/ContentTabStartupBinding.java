package com.luatweaker.content;

import com.luatweaker.api.content.IArmorMaterialBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.ICreativeTabBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/** Registers the creative-tab and armor-material startup creators. */
final class ContentTabStartupBinding {

    private ContentTabStartupBinding() {
    }

    static void bindTabAndMaterialCreators(ILuaEngine engine, ILuaTable startupTable,
            IContentService contentService) {
        bindCreateTab(engine, startupTable, contentService);
        bindCreateArmorMaterial(engine, startupTable, contentService);
    }

    private static void bindCreateTab(ILuaEngine engine, ILuaTable startupTable,
            IContentService contentService) {
        startupTable.rawset("createTab", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("startup:createTab requires (id, builderFunc)");
            }
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createTab(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable tabTable = engine.createTable();
                    bindTabStrings(tabTable, builder);
                    ContentBindingSupport.invokeBuilderCallback(engine, "creative tab", id, callbackVal, tabTable);
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Creative Tab: " + id);
            return null;
        });
    }

    private static void bindTabStrings(ILuaTable tabTable,
            ICreativeTabBuilder builder) {
        tabTable.rawset("title", a -> {
            ContentBindingSupport.bindFirstString(a, builder::title);
            return tabTable;
        });
        tabTable.rawset("icon", a -> {
            ContentBindingSupport.bindFirstString(a, builder::icon);
            return tabTable;
        });
    }

    private static void bindCreateArmorMaterial(ILuaEngine engine, ILuaTable startupTable,
            IContentService contentService) {
        startupTable.rawset("createArmorMaterial", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("startup:createArmorMaterial requires (id, builderFunc)");
            }
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createArmorMaterial(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable matTable = engine.createTable();
                    bindArmorMaterialMethods(matTable, builder);
                    ContentBindingSupport.invokeBuilderCallback(engine, "armor material", id, callbackVal, matTable);
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Explicit Custom Armor Material: " + id);
            return null;
        });
    }

    private static void bindArmorMaterialMethods(ILuaTable matTable, IArmorMaterialBuilder builder) {
        matTable.rawset("layer", a -> {
            ContentBindingSupport.bindFirstString(a, builder::layer);
            return matTable;
        });
        matTable.rawset("equipSound", a -> {
            ContentBindingSupport.bindFirstString(a, builder::equipSound);
            return matTable;
        });
        matTable.rawset("toughness", a -> {
            bindDoubleArg(a, v -> builder.toughness((float) v));
            return matTable;
        });
        matTable.rawset("knockbackResistance", a -> {
            bindDoubleArg(a, v -> builder.knockbackResistance((float) v));
            return matTable;
        });
        matTable.rawset("enchantability", a -> {
            bindIntArg(a, builder::enchantability);
            return matTable;
        });
        matTable.rawset("defense", a -> {
            int aOff = LuaBinder.getOffset(a);
            if (ContentBindingSupport.hasMinArgs(a, aOff, 2)) {
                builder.defense(a[aOff].asString(), a[aOff + 1].asInt());
            }
            return matTable;
        });
    }

    private static void bindDoubleArg(ILuaValue[] args, DoubleConsumer setter) {
        int off = LuaBinder.getOffset(args);
        if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
            setter.accept(args[off].asDouble());
        }
    }

    private static void bindIntArg(ILuaValue[] args, IntConsumer setter) {
        int off = LuaBinder.getOffset(args);
        if (ContentBindingSupport.hasMinArgs(args, off, 1)) {
            setter.accept(args[off].asInt());
        }
    }

}

