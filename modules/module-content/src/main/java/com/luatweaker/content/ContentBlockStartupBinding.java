package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

import java.util.function.BiFunction;

/** Registers the block/fluid/entity startup creators. */
final class ContentBlockStartupBinding {

    private static final String DEFAULT_RUBY_TAB = "ruby_tab";

    private ContentBlockStartupBinding() {
    }

    static void bindBlockCreators(ILuaEngine engine, ILuaTable startupTable,
            IContentService contentService, IDatapackService datapackService) {
        startupTable.rawset("createBlock", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("startup:createBlock requires (id, builderFunc)");
            }
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createBlock(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable blockTable = engine.createTable();
                    ContentBlockBinding.bindBlockBuilderMethods(engine, blockTable, builder, datapackService);
                    ContentBindingSupport.invokeBuilderCallback(engine, "block", id, callbackVal, blockTable);
                }
            });

            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Block builder: " + id);
            return null;
        });
        bindVariantBlockCreator(engine, startupTable, datapackService,
                "createStairs", "Stairs", "minecraft:block/stairs", contentService::createStairs);
        bindVariantBlockCreator(engine, startupTable, datapackService,
                "createSlab", "Slab", "minecraft:block/slab", contentService::createSlab);
        bindVariantBlockCreator(engine, startupTable, datapackService,
                "createWall", "Wall", "minecraft:block/wall", contentService::createWall);
        startupTable.rawset("NewBlock", args -> {
            int off = LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builderTable = engine.createTable();
            final IBlockBuilder[] blockBuilder = new IBlockBuilder[1];
            contentService.createBlock(id, b -> blockBuilder[0] = b);
            ContentBlockBinding.bindBlockBuilderMethods(engine, builderTable, blockBuilder[0], datapackService);
            builderTable.rawset("Register", a -> builderTable);
            return builderTable;
        });
    }

    private static void bindVariantBlockCreator(ILuaEngine engine, ILuaTable startupTable,
            IDatapackService datapackService, String name, String label,
            String defaultModel, BiFunction<String, String, IBlockBuilder> creator) {
        startupTable.rawset(name, args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("startup:" + name + " requires (id, [baseBlockId])");
            }
            String id = args[off].asString();
            ILuaValue second = ContentBindingSupport.hasMinArgs(args, off, 2) ? args[off + 1] : null;
            String baseId = second != null && !second.isFunction() ? second.asString() : null;
            ILuaValue callbackVal = firstFunctionArg(args, off);
            IBlockBuilder builder = creator.apply(id, baseId);
            builder.model(defaultModel);
            builder.creativeTab(DEFAULT_RUBY_TAB);
            if (callbackVal != null) {
                ILuaTable blockTable = engine.createTable();
                ContentBlockBinding.bindBlockBuilderMethods(engine, blockTable, builder, datapackService);
                ContentBindingSupport.invokeBuilderCallback(engine, name, id, callbackVal, blockTable);
            }
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom " + label + ": " + id);
            return null;
        });
    }

    private static ILuaValue firstFunctionArg(ILuaValue[] args, int off) {
        if (ContentBindingSupport.hasMinArgs(args, off, 2) && args[off + 1].isFunction()) {
            return args[off + 1];
        }
        if (ContentBindingSupport.hasMinArgs(args, off, 3) && args[off + 2].isFunction()) {
            return args[off + 2];
        }
        return null;
    }

    static void bindSimpleCreators(ILuaEngine engine, ILuaTable startupTable,
            IContentService contentService, IDatapackService datapackService) {
        startupTable.rawset("createFluid", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("startup:createFluid requires (id, builderFunc)");
            }
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createFluid(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable fluidTable = engine.createTable();
                    ContentEntityBinding.bindFluidBuilderMethods(engine, fluidTable, builder);
                    ContentBindingSupport.invokeBuilderCallback(engine, "fluid", id, callbackVal, fluidTable);
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Fluid builder: " + id);
            return null;
        });

        startupTable.rawset("createRangedItem", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("startup:createRangedItem requires (id, builderFunc)");
            }
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createRangedItem(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable itemTable = engine.createTable();
                    ContentItemBinding.bindItemBuilderMethods(engine, itemTable, builder, datapackService);
                    ContentBindingSupport.invokeBuilderCallback(engine, "ranged item", id, callbackVal, itemTable);
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Ranged Item builder: " + id);
            return null;
        });

        bindEntityCreators(engine, startupTable, contentService, datapackService);
    }

    static void bindEntityCreators(ILuaEngine engine, ILuaTable startupTable,
            IContentService contentService, IDatapackService datapackService) {
        startupTable.rawset("createEntity", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException("startup:createEntity requires (id, builderFunc)");
            }
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createEntity(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable entityTable = engine.createTable();
                    ContentEntityBinding.bindEntityBuilderMethods(engine, entityTable, builder);
                    ContentBindingSupport.invokeBuilderCallback(engine, "entity", id, callbackVal, entityTable);
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Entity builder: " + id);
            return null;
        });
        startupTable.rawset("registerEntity", startupTable.rawget("createEntity"));

        startupTable.rawset("NewItem", args -> {
            int off = LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builderTable = engine.createTable();
            final IItemBuilder[] itemBuilder = new IItemBuilder[1];
            contentService.createItem(id, b -> itemBuilder[0] = b);
            ContentItemBinding.bindItemBuilderMethods(engine, builderTable, itemBuilder[0], datapackService);
            builderTable.rawset("Register", a -> builderTable);
            return builderTable;
        });

        startupTable.rawset("NewFluid", args -> {
            int off = LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builderTable = engine.createTable();
            final IFluidBuilder[] fluidBuilder = new IFluidBuilder[1];
            contentService.createFluid(id, b -> fluidBuilder[0] = b);
            ContentEntityBinding.bindFluidBuilderMethods(engine, builderTable, fluidBuilder[0]);
            builderTable.rawset("Register", a -> builderTable);
            return builderTable;
        });
    }
}

