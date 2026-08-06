package com.luatweaker.content;

import com.luatweaker.api.content.*;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.*;

import java.util.function.BiFunction;

public class ContentLuaBinding {

    public static void registerBindings(ILuaEngine engine, IContentService contentService, IStorageService storageService, IDatapackService datapackService) {
        ILuaTable env = engine.getGlobalEnvironment();

        // 1. Register "startup" Service
        ILuaTable startupTable = engine.createTable();

        bindCreationHelper(engine, startupTable, "createItem", datapackService, (id, cb) -> contentService.createItem(id, cb));
        bindCreationHelper(engine, startupTable, "createSword", datapackService, (id, cb) -> contentService.createSword(id, cb));
        bindCreationHelper(engine, startupTable, "createPickaxe", datapackService, (id, cb) -> contentService.createPickaxe(id, cb));
        bindCreationHelper(engine, startupTable, "createAxe", datapackService, (id, cb) -> contentService.createAxe(id, cb));
        bindCreationHelper(engine, startupTable, "createShovel", datapackService, (id, cb) -> contentService.createShovel(id, cb));
        bindCreationHelper(engine, startupTable, "createHoe", datapackService, (id, cb) -> contentService.createHoe(id, cb));
        bindCreationHelper(engine, startupTable, "createHelmet", datapackService, (id, cb) -> contentService.createHelmet(id, cb));
        bindCreationHelper(engine, startupTable, "createChestplate", datapackService, (id, cb) -> contentService.createChestplate(id, cb));
        bindCreationHelper(engine, startupTable, "createLeggings", datapackService, (id, cb) -> contentService.createLeggings(id, cb));
        bindCreationHelper(engine, startupTable, "createBoots", datapackService, (id, cb) -> contentService.createBoots(id, cb));

        // startup:registerProjectile("luatweaker:ruby_orb", { damage = 25, explosionPower = 2, trailParticle = "minecraft:flame" })
        startupTable.rawset("registerProjectile", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) throw new IllegalArgumentException("startup:registerProjectile requires (id, [configTable])");
            String id = args[off].asString();
            double damage = 0;
            double explosionPower = 0;
            String trailParticle = "";
            if (args.length - off >= 2 && args[off + 1].isTable()) {
                ILuaTable cfg = args[off + 1].asTable();
                ILuaValue dmgVal = cfg.rawget("damage");
                if (dmgVal != null && !dmgVal.isNil()) damage = dmgVal.asDouble();
                ILuaValue expVal = cfg.rawget("explosionPower");
                if (expVal != null && !expVal.isNil()) explosionPower = expVal.asDouble();
                ILuaValue trailVal = cfg.rawget("trailParticle");
                if (trailVal != null && !trailVal.isNil()) trailParticle = trailVal.asString();
            }
            ProjectileRegistry.register(id, new com.luatweaker.api.content.ProjectileDefinition(damage, explosionPower, trailParticle));
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "Registered Custom Projectile definition: " + id + " (damage=" + damage + ", explosionPower=" + explosionPower + ")");
            return null;
        });

        // startup:createBlock("custom_ruby_block", function(block) ... end)
        startupTable.rawset("createBlock", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) throw new IllegalArgumentException("startup:createBlock requires (id, builderFunc)");
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createBlock(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable blockTable = engine.createTable();
                    bindBlockBuilderMethods(engine, blockTable, builder, datapackService);
                    try {
                        engine.callFunction(callbackVal, blockTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in block builder callback for '" + id + "': " + e.getMessage());
                    }
                }
            });

            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Block builder: " + id);
            return null;
        });

        // startup:createFluid("liquid_ruby", function(fluid) ... end)
        startupTable.rawset("createFluid", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) throw new IllegalArgumentException("startup:createFluid requires (id, builderFunc)");
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createFluid(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable fluidTable = engine.createTable();
                    bindFluidBuilderMethods(engine, fluidTable, builder);
                    try {
                        engine.callFunction(callbackVal, fluidTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in fluid builder callback for '" + id + "': " + e.getMessage());
                    }
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Fluid builder: " + id);
            return null;
        });

        // startup:createRangedItem("magic_staff", function(item) ... end)
        startupTable.rawset("createRangedItem", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) throw new IllegalArgumentException("startup:createRangedItem requires (id, builderFunc)");
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createRangedItem(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable itemTable = engine.createTable();
                    bindItemBuilderMethods(engine, itemTable, builder, datapackService);
                    try {
                        engine.callFunction(callbackVal, itemTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in ranged item builder callback for '" + id + "': " + e.getMessage());
                    }
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Ranged Item builder: " + id);
            return null;
        });

        // startup:createEntity("ruby_boss", function(entity) ... end)
        startupTable.rawset("createEntity", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) throw new IllegalArgumentException("startup:createEntity requires (id, builderFunc)");
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createEntity(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable entityTable = engine.createTable();
                    bindEntityBuilderMethods(engine, entityTable, builder, datapackService);
                    try {
                        engine.callFunction(callbackVal, entityTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in entity builder callback for '" + id + "': " + e.getMessage());
                    }
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Entity builder: " + id);
            return null;
        });
        startupTable.rawset("registerEntity", startupTable.rawget("createEntity"));

        // startup:createStairs("ruby_stairs", "luatweaker:ruby_block", function(block) ... end)
        startupTable.rawset("createStairs", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) throw new IllegalArgumentException("startup:createStairs requires (id, [baseBlockId])");
            String id = args[off].asString();
            String baseId = (args.length - off >= 2) ? args[off + 1].asString() : id;
            ILuaValue callbackVal = (args.length - off >= 3 && args[off + 2].isFunction()) ? args[off + 2] : null;
            final ILuaValue finalCb = callbackVal;
            contentService.createBlock(id, builder -> {
                builder.model("minecraft:block/stairs");
                builder.creativeTab("ruby_tab");
                if (finalCb != null && finalCb.isFunction()) {
                    ILuaTable blockTable = engine.createTable();
                    bindBlockBuilderMethods(engine, blockTable, builder, datapackService);
                    try {
                        engine.callFunction(finalCb, blockTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in stairs builder callback: " + e.getMessage());
                    }
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Stairs: " + id);
            return null;
        });

        // startup:createSlab("ruby_slab", "luatweaker:ruby_block", function(block) ... end)
        startupTable.rawset("createSlab", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) throw new IllegalArgumentException("startup:createSlab requires (id, [baseBlockId])");
            String id = args[off].asString();
            String baseId = (args.length - off >= 2) ? args[off + 1].asString() : id;
            ILuaValue callbackVal = (args.length - off >= 3 && args[off + 2].isFunction()) ? args[off + 2] : null;
            final ILuaValue finalCb = callbackVal;
            contentService.createBlock(id, builder -> {
                builder.model("minecraft:block/slab");
                builder.creativeTab("ruby_tab");
                if (finalCb != null && finalCb.isFunction()) {
                    ILuaTable blockTable = engine.createTable();
                    bindBlockBuilderMethods(engine, blockTable, builder, datapackService);
                    try {
                        engine.callFunction(finalCb, blockTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in slab builder callback: " + e.getMessage());
                    }
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Slab: " + id);
            return null;
        });

        // startup:createWall("ruby_wall", "luatweaker:ruby_block", function(block) ... end)
        startupTable.rawset("createWall", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) throw new IllegalArgumentException("startup:createWall requires (id, [baseBlockId])");
            String id = args[off].asString();
            String baseId = (args.length - off >= 2) ? args[off + 1].asString() : id;
            ILuaValue callbackVal = (args.length - off >= 3 && args[off + 2].isFunction()) ? args[off + 2] : null;
            final ILuaValue finalCb = callbackVal;
            contentService.createBlock(id, builder -> {
                builder.model("minecraft:block/wall");
                builder.creativeTab("ruby_tab");
                if (finalCb != null && finalCb.isFunction()) {
                    ILuaTable blockTable = engine.createTable();
                    bindBlockBuilderMethods(engine, blockTable, builder, datapackService);
                    try {
                        engine.callFunction(finalCb, blockTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in wall builder callback: " + e.getMessage());
                    }
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Wall: " + id);
            return null;
        });

        // startup:createTab("magic_tab", function(tab) ... end)
        startupTable.rawset("createTab", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) throw new IllegalArgumentException("startup:createTab requires (id, builderFunc)");
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createTab(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable tabTable = engine.createTable();
                    tabTable.rawset("title", a -> {
                        int aOff = getOffset(a);
                        if (a.length - aOff >= 1) builder.title(a[aOff].asString());
                        return tabTable;
                    });
                    tabTable.rawset("icon", a -> {
                        int aOff = getOffset(a);
                        if (a.length - aOff >= 1) builder.icon(a[aOff].asString());
                        return tabTable;
                    });
                    try {
                        engine.callFunction(callbackVal, tabTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in creative tab builder callback for '" + id + "': " + e.getMessage());
                    }
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Creative Tab: " + id);
            return null;
        });

        // startup:createArmorMaterial("ruby", function(mat) ... end)
        startupTable.rawset("createArmorMaterial", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) throw new IllegalArgumentException("startup:createArmorMaterial requires (id, builderFunc)");
            String id = args[off].asString();
            ILuaValue callbackVal = args[off + 1];

            contentService.createArmorMaterial(id, builder -> {
                if (callbackVal != null && callbackVal.isFunction()) {
                    ILuaTable matTable = engine.createTable();
                    matTable.rawset("layer", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) builder.layer(a[aOff].asString()); return matTable; });
                    matTable.rawset("equipSound", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) builder.equipSound(a[aOff].asString()); return matTable; });
                    matTable.rawset("toughness", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) builder.toughness((float) a[aOff].asDouble()); return matTable; });
                    matTable.rawset("knockbackResistance", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) builder.knockbackResistance((float) a[aOff].asDouble()); return matTable; });
                    matTable.rawset("enchantability", a -> { int aOff = getOffset(a); if (a.length - aOff >= 1) builder.enchantability(a[aOff].asInt()); return matTable; });
                    matTable.rawset("defense", a -> {
                        int aOff = getOffset(a);
                        if (a.length - aOff >= 2) {
                            builder.defense(a[aOff].asString(), a[aOff + 1].asInt());
                        }
                        return matTable;
                    });
                    try {
                        engine.callFunction(callbackVal, matTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in armor material builder callback for '" + id + "': " + e.getMessage());
                    }
                }
            });
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Explicit Custom Armor Material: " + id);
            return null;
        });

        // Content.NewItem("id"):DisplayName(...):MaxStackSize(...):Register()
        startupTable.rawset("NewItem", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builderTable = engine.createTable();
            final IItemBuilder[] itemBuilder = new IItemBuilder[1];
            contentService.createItem(id, b -> itemBuilder[0] = b);
            bindItemBuilderMethods(engine, builderTable, itemBuilder[0], datapackService);
            builderTable.rawset("Register", a -> builderTable);
            return builderTable;
        });

        // Content.NewBlock("id"):DisplayName(...):Hardness(...):Register()
        startupTable.rawset("NewBlock", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builderTable = engine.createTable();
            final IBlockBuilder[] blockBuilder = new IBlockBuilder[1];
            contentService.createBlock(id, b -> blockBuilder[0] = b);
            bindBlockBuilderMethods(engine, builderTable, blockBuilder[0], datapackService);
            builderTable.rawset("Register", a -> builderTable);
            return builderTable;
        });

        // Content.NewFluid("id"):Color(...):Register()
        startupTable.rawset("NewFluid", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            String id = args[off].asString();
            ILuaTable builderTable = engine.createTable();
            final IFluidBuilder[] fluidBuilder = new IFluidBuilder[1];
            contentService.createFluid(id, b -> fluidBuilder[0] = b);
            bindFluidBuilderMethods(engine, builderTable, fluidBuilder[0]);
            builderTable.rawset("Register", a -> builderTable);
            return builderTable;
        });

        // Content.Item("id", count)
        startupTable.rawset("Item", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            String id = args[off].asString();
            int count = args.length - off >= 2 ? args[off + 1].asInt() : 1;
            return engine.toLuaValue(new com.luatweaker.api.wrapper.ItemCount(id, count));
        });

        // Content.Ingredient("desc")
        startupTable.rawset("Ingredient", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            String desc = args[off].asString();
            return engine.toLuaValue(new com.luatweaker.api.wrapper.IngredientWrapper(desc));
        });

        // Content.Tag("name")
        startupTable.rawset("Tag", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            String desc = args[off].asString();
            if (!desc.startsWith("#")) desc = "#" + desc;
            return engine.toLuaValue(new com.luatweaker.api.wrapper.IngredientWrapper(desc));
        });

        // Content.OreDict("name")
        startupTable.rawset("OreDict", startupTable.rawget("Tag"));

        // Content.NewKeyMapping("staff_swap_skill"):DisplayName("Staff Skill Swap"):Category("luatweaker"):DefaultKey(90):OnPress("StaffSwapSkill"):Register()
        ILuaFunction newKeyMappingFn = args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) throw new IllegalArgumentException("Content.NewKeyMapping requires (id)");
            String id = args[off].asString();
            ILuaTable table = engine.createTable();
            final String[] displayName = new String[] { id };
            final String[] category = new String[] { "luatweaker" };
            final int[] defaultKey = new int[] { 90 };
            final String[] payload = new String[] { "" };

            bindMethod(table, "displayName", a -> { int o = getOffset(a); if (a.length - o >= 1) displayName[0] = a[o].asString(); return table; });
            bindMethod(table, "name", a -> { int o = getOffset(a); if (a.length - o >= 1) displayName[0] = a[o].asString(); return table; });
            bindMethod(table, "description", a -> { int o = getOffset(a); if (a.length - o >= 1) displayName[0] = a[o].asString(); return table; });
            bindMethod(table, "category", a -> { int o = getOffset(a); if (a.length - o >= 1) category[0] = a[o].asString(); return table; });
            bindMethod(table, "defaultKey", a -> { int o = getOffset(a); if (a.length - o >= 1) defaultKey[0] = a[o].asInt(); return table; });
            bindMethod(table, "onPress", a -> { int o = getOffset(a); if (a.length - o >= 1) payload[0] = a[o].asString(); return table; });
            bindMethod(table, "onPressPayload", a -> { int o = getOffset(a); if (a.length - o >= 1) payload[0] = a[o].asString(); return table; });
            bindMethod(table, "register", a -> {
                Object keyBindService = com.luatweaker.core.service.LuaServiceRegistry.get("KeyBindService");
                if (keyBindService instanceof com.luatweaker.api.client.IKeyBindService kbs) {
                    kbs.registerKeyBind(id, displayName[0], category[0], defaultKey[0], payload[0]);
                }
                return table;
            });
            return table;
        };
        startupTable.rawset("NewKeyMapping", newKeyMappingFn);
        startupTable.rawset("newKeyMapping", newKeyMappingFn);

        env.rawset("startup", startupTable);
        env.rawset("Content", startupTable);

        engine.registerService("Startup", startupTable);
        engine.registerService("Content", startupTable);

        // 2. Register "storage" Service
        ILuaTable storageTable = engine.createTable();

        storageTable.rawset("set", args -> {
            if (args.length < 3) throw new IllegalArgumentException("storage:set requires (key, value)");
            String key = args[1].asString();
            Object value = args[2].toJavaObject();
            storageService.set(key, value);
            return null;
        });

        storageTable.rawset("get", args -> {
            if (args.length < 2) throw new IllegalArgumentException("storage:get requires (key, [defaultVal])");
            String key = args[1].asString();
            Object defaultVal = args.length >= 3 ? args[2].toJavaObject() : null;
            Object result = storageService.get(key, defaultVal);
            return engine.toLuaValue(result);
        });

        env.rawset("storage", storageTable);
        engine.registerService("Storage", storageService);

        // 3. Register "datapack" Service
        ILuaTable datapackTable = engine.createTable();

        datapackTable.rawset("addJsonRecipe", args -> {
            if (args.length < 3) throw new IllegalArgumentException("datapack:addJsonRecipe requires (recipeId, jsonString)");
            datapackService.addJsonRecipe(args[1].asString(), args[2].asString());
            return null;
        });

        datapackTable.rawset("addLootTable", args -> {
            if (args.length < 3) throw new IllegalArgumentException("datapack:addLootTable requires (path, jsonString)");
            datapackService.addLootTable(args[1].asString(), args[2].asString());
            return null;
        });

        datapackTable.rawset("addAdvancement", args -> {
            if (args.length < 3) throw new IllegalArgumentException("datapack:addAdvancement requires (path, jsonString)");
            datapackService.addAdvancement(args[1].asString(), args[2].asString());
            return null;
        });

        datapackTable.rawset("addFunction", args -> {
            if (args.length < 3) throw new IllegalArgumentException("datapack:addFunction requires (path, commands)");
            datapackService.addFunction(args[1].asString(), args[2].asString());
            return null;
        });

        datapackTable.rawset("addData", args -> {
            if (args.length < 3) throw new IllegalArgumentException("datapack:addData requires (relPath, jsonString)");
            datapackService.addData(args[1].asString(), args[2].asString());
            return null;
        });

        // datapack:addTag("item", "luatweaker:ruby_items", {"luatweaker:custom_ruby", "luatweaker:ruby_block"})
        // datapack:addTag("block", "minecraft:beacon_base_blocks", {"luatweaker:ruby_block"})
        datapackTable.rawset("addTag", args -> {
            if (args.length < 4)
                throw new IllegalArgumentException("datapack:addTag requires (tagType, tagId, valuesTable)");
            String tagType = args[1].asString();
            String tagId   = args[2].asString();
            java.util.List<String> values = new java.util.ArrayList<>();
            // args[3] may be an ILuaTable — iterate sequential keys 1..n
            ILuaValue valArg = args[3];
            if (valArg instanceof ILuaTable valTable) {
                for (int i = 1; ; i++) {
                    ILuaValue entry = valTable.rawget(i);
                    if (entry == null || entry.isNil()) break;
                    values.add(entry.asString());
                }
            }
            if (!values.isEmpty()) {
                datapackService.addTag(tagType, tagId, values);
            }
            return null;
        });


        env.rawset("datapack", datapackTable);

        // Convenient global 'tag' shortcut API:
        //   tag.item("c:gems", "luatweaker:custom_ruby")
        //   tag.block("minecraft:beacon_base_blocks", {"luatweaker:ruby_block"})
        ILuaTable tagTable = engine.createTable();
        tagTable.rawset("item", args -> {
            if (args.length >= 2) addTagFromLua(datapackService, "item", args[1].asString(), args.length >= 3 ? args[2] : null);
            return null;
        });
        tagTable.rawset("block", args -> {
            if (args.length >= 2) addTagFromLua(datapackService, "block", args[1].asString(), args.length >= 3 ? args[2] : null);
            return null;
        });
        tagTable.rawset("add", args -> {
            if (args.length >= 3) addTagFromLua(datapackService, args[1].asString(), args[2].asString(), args.length >= 4 ? args[3] : null);
            return null;
        });

        // Add __call metamethod so tag(...) function call works for both tag ingredients AND tag registration!
        ILuaTable tagMeta = engine.createTable();
        tagMeta.rawset("__call", args -> {
            // Note: in Cobalt function invocation, args[1] is self (tagTable)
            if (args.length >= 3 && !args[2].isNil() && (
                    args[2].asString().equalsIgnoreCase("item") ||
                    args[2].asString().equalsIgnoreCase("block") ||
                    args[2].asString().equalsIgnoreCase("fluid") ||
                    args[2].asString().equalsIgnoreCase("entity_type")
            )) {
                String type = args[2].asString();
                String tagId = args.length >= 4 ? args[3].asString() : "";
                ILuaValue val = args.length >= 5 ? args[4] : null;
                if (!tagId.isEmpty()) {
                    addTagFromLua(datapackService, type, tagId, val);
                }
                return null;
            }

            // Single argument recipe ingredient tag call: tag("#minecraft:logs") or tag("minecraft:logs")
            if (args.length >= 2 && !args[1].isNil()) {
                String tagStr = args[1].asString();
                if (!tagStr.startsWith("#")) tagStr = "#" + tagStr;
                return engine.toLuaValue(new com.luatweaker.api.wrapper.IngredientWrapper(tagStr));
            }


            return null;
        });
        tagTable.setMetatable(tagMeta);

        env.rawset("tag", tagTable);


        engine.registerService("Datapack", datapackService);
    }

    private static void addTagFromLua(IDatapackService service, String type, String tagId, ILuaValue val) {
        if (service == null) return;
        java.util.List<String> list = new java.util.ArrayList<>();
        if (val instanceof ILuaTable tbl) {
            for (int i = 1; ; i++) {
                ILuaValue entry = tbl.rawget(i);
                if (entry == null || entry.isNil()) break;
                list.add(entry.asString());
            }
        } else if (val != null && !val.isNil()) {
            list.add(val.asString());
        }
        if (!list.isEmpty()) {
            service.addTag(type, tagId, list);
        }
    }

    private static void bindCreationHelper(ILuaEngine engine, ILuaTable startupTable, String name, IDatapackService datapackService, BiFunction<String, java.util.function.Consumer<IItemBuilder>, IItemBuilder> creator) {
        startupTable.rawset(name, args -> {
            if (args.length < 2) throw new IllegalArgumentException("startup:" + name + " requires (id, builderFunc)");
            String id = args[1].asString();
            ILuaValue callbackVal = args[2];

            creator.apply(id, builder -> {
                if (callbackVal.isFunction()) {
                    ILuaTable itemTable = engine.createTable();
                    bindItemBuilderMethods(engine, itemTable, builder, datapackService);
                    try {
                        engine.callFunction(callbackVal, itemTable);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error in item builder callback for '" + id + "': " + e.getMessage());
                    }
                }
            });

            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Item builder (" + name + "): " + id);
            return null;
        });
    }

    private static void bindItemBuilderMethods(ILuaEngine engine, ILuaTable table, IItemBuilder builder, IDatapackService datapackService) {
        bindMethod(table, "type", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.type(args[off].asString()); return table; });
        bindMethod(table, "maxStackSize", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.maxStackSize(args[off].asInt()); return table; });
        bindMethod(table, "rarity", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.rarity(args[off].asString()); return table; });
        bindMethod(table, "durability", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.durability(args[off].asInt()); return table; });
        bindMethod(table, "maxDamage", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.durability(args[off].asInt()); return table; });
        bindMethod(table, "miningLevel", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            Object obj = args[off].toJavaObject();
            if (obj instanceof Number num) builder.miningLevel(num.intValue());
            else builder.miningLevel(args[off].asString());
            return table;
        });
        bindMethod(table, "tier", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            Object obj = args[off].toJavaObject();
            if (obj instanceof Number num) builder.miningLevel(num.intValue());
            else builder.miningLevel(args[off].asString());
            return table;
        });
        bindMethod(table, "miningSpeed", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.miningSpeed((float) args[off].asDouble()); return table; });
        bindMethod(table, "efficiency", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.miningSpeed((float) args[off].asDouble()); return table; });
        bindMethod(table, "attackDamage", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.attackDamage((float) args[off].asDouble()); return table; });
        bindMethod(table, "attackSpeed", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.attackSpeed((float) args[off].asDouble()); return table; });
        bindMethod(table, "defense", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.defense(args[off].asInt()); return table; });
        bindMethod(table, "toughness", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.toughness((float) args[off].asDouble()); return table; });
        bindMethod(table, "knockbackResistance", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.knockbackResistance((float) args[off].asDouble()); return table; });
        bindMethod(table, "enchantability", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.enchantability(args[off].asInt()); return table; });
        bindMethod(table, "burnTime", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.burnTime(args[off].asInt()); return table; });
        bindMethod(table, "displayName", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.displayName(args[off].asString()); return table; });
        bindMethod(table, "model", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.model(args[off].asString()); return table; });
        bindMethod(table, "texture", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.texture(args[off].asString()); return table; });
        bindMethod(table, "armorMaterial", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.armorMaterial(args[off].asString()); return table; });
        bindMethod(table, "armorTexture", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.armorTexture(args[off].asString()); return table; });
        bindMethod(table, "creativeTab", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.creativeTab(args[off].asString()); return table; });
        bindMethod(table, "tabGroup", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.creativeTab(args[off].asString()); return table; });
        bindMethod(table, "tag", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) {
                String tagId = args[off].asString();
                builder.tag(tagId);
                if (datapackService != null) {
                    String fullId = builder.getId().contains(":") ? builder.getId() : "luatweaker:" + builder.getId();
                    datapackService.addTag("item", tagId, java.util.List.of(fullId));
                }
            }
            return table;
        });

        bindMethod(table, "onRightClick", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onRightClick((player, itemStack) -> {
                    try {
                        ILuaValue playerVal = (player instanceof com.luatweaker.api.entity.IPlayer p)
                                ? com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, p)
                                : engine.toLuaValue(player);
                        engine.callFunction(func, playerVal, engine.toLuaValue(itemStack));
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error executing item rightClick callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });

        bindMethod(table, "onHitEntity", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onHitEntity((targetEntity, shooterPlayer) -> {
                    try {
                        ILuaValue targetVal = (targetEntity instanceof com.luatweaker.api.entity.IEntity e)
                                ? com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, e)
                                : engine.toLuaValue(targetEntity);
                        ILuaValue shooterVal = (shooterPlayer instanceof com.luatweaker.api.entity.IPlayer p)
                                ? com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, p)
                                : engine.toLuaValue(shooterPlayer);
                        engine.callFunction(func, targetVal, shooterVal);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error executing item onHitEntity callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });

        bindMethod(table, "food", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.food(args[off].asInt(), (float) args[off + 1].asDouble()); return table; });
        bindMethod(table, "alwaysEdible", args -> { builder.alwaysEdible(); return table; });
        bindMethod(table, "onConsume", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onConsume((player, itemStack) -> {
                    try {
                        ILuaValue playerVal = (player instanceof com.luatweaker.api.entity.IPlayer p)
                                ? com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, p)
                                : engine.toLuaValue(player);
                        engine.callFunction(func, playerVal, engine.toLuaValue(itemStack));
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error executing item onConsume callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });
        bindMethod(table, "glow", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.glow(args[off].asBoolean()); return table; });
        bindMethod(table, "tooltip", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.tooltip(args[off].asString()); return table; });

        bindMethod(table, "food", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 2) {
                builder.food(args[off].asInt(), (float) args[off + 1].asDouble());
            }
            return table;
        });

        bindMethod(table, "alwaysEdible", args -> { builder.alwaysEdible(); return table; });

        bindMethod(table, "onConsume", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onConsume((player, itemStack) -> {
                    try {
                        ILuaValue playerVal = (player instanceof com.luatweaker.api.entity.IPlayer p)
                                ? com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, p)
                                : engine.toLuaValue(player);
                        engine.callFunction(func, playerVal, engine.toLuaValue(itemStack));
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error executing item onConsume callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });

        bindMethod(table, "glow", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            boolean enable = args.length - off < 1 || args[off].asBoolean();
            builder.glow(enable);
            return table;
        });

        bindMethod(table, "tooltip", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) builder.tooltip(args[off].asString());
            return table;
        });

    }

    private static void bindBlockBuilderMethods(ILuaEngine engine, ILuaTable table, IBlockBuilder builder, IDatapackService datapackService) {
        bindMethod(table, "hardness", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.hardness((float) args[off].asDouble()); return table; });
        bindMethod(table, "resistance", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.resistance((float) args[off].asDouble()); return table; });
        bindMethod(table, "lightLevel", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.lightLevel(args[off].asInt()); return table; });
        bindMethod(table, "light", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.lightLevel(args[off].asInt()); return table; });
        bindMethod(table, "emissive", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) {
                Object val = args[off].toJavaObject();
                if (val instanceof Number num) {
                    builder.lightLevel(Math.min(15, Math.max(0, num.intValue())));
                } else if (val instanceof Boolean b) {
                    builder.lightLevel(b ? 15 : 0);
                } else {
                    builder.lightLevel(args[off].asInt());
                }
            } else {
                builder.lightLevel(15);
            }
            return table;
        });


        bindMethod(table, "soundType", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.soundType(args[off].asString()); return table; });
        bindMethod(table, "requiresTool", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.requiresTool(args[off].asBoolean()); return table; });
        bindMethod(table, "mineableWith", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.mineableWith(args[off].asString()); return table; });
        bindMethod(table, "miningLevel", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            Object obj = args[off].toJavaObject();
            if (obj instanceof Number num) builder.miningLevel(num.intValue());
            else builder.miningLevel(args[off].asString());
            return table;
        });
        bindMethod(table, "friction", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.friction((float) args[off].asDouble()); return table; });
        bindMethod(table, "model", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.model(args[off].asString()); return table; });

        bindMethod(table, "drop", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) {
                String itemId = args[off].asString();
                int minCount = args.length - off >= 2 ? args[off + 1].asInt() : 1;
                int maxCount = args.length - off >= 3 ? args[off + 2].asInt() : minCount;
                builder.drop(itemId, minCount, maxCount);
            }
            return table;
        });

        bindMethod(table, "dropExperience", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) {
                int minExp = args[off].asInt();
                int maxExp = args.length - off >= 2 ? args[off + 1].asInt() : minExp;
                builder.dropExperience(minExp, maxExp);
            }
            return table;
        });

        bindMethod(table, "container", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) {
                int rows = args[off].asInt();
                int cols = args.length - off >= 2 ? args[off + 1].asInt() : 6;
                String dropMode = args.length - off >= 3 ? args[off + 2].asString() : "packed";
                builder.container(rows, cols, dropMode);
            }
            return table;
        });

        bindMethod(table, "containerUseDistance", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) builder.containerUseDistance(args[off].asDouble());
            return table;
        });

        bindMethod(table, "itemFilter", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.itemFilter((itemId, count) -> {
                    try {
                        ILuaValue allowed = engine.callFunction(func,
                                engine.wrapString(String.valueOf(itemId)),
                                engine.wrapNumber(((Number) count).doubleValue()));
                        return allowed.isNil() || allowed.asBoolean();
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error executing block itemFilter callback: " + e.getMessage());
                        return false;
                    }
                });
            }
            return table;
        });

        bindMethod(table, "containerTexture", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) builder.containerTexture(args[off].asString());
            return table;
        });

        bindMethod(table, "containerTitle", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) builder.containerTitle(args[off].asString());
            return table;
        });

        bindMethod(table, "texture", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.texture(args[off].asString()); return table; });
        bindMethod(table, "displayName", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.displayName(args[off].asString()); return table; });
        bindMethod(table, "creativeTab", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.creativeTab(args[off].asString()); return table; });
        bindMethod(table, "tabGroup", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.creativeTab(args[off].asString()); return table; });
        bindMethod(table, "tag", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) {
                String tagId = args[off].asString();
                builder.tag(tagId);
                if (datapackService != null) {
                    String fullId = builder.getId().contains(":") ? builder.getId() : "luatweaker:" + builder.getId();
                    datapackService.addTag("block", tagId, java.util.List.of(fullId));
                }
            }
            return table;
        });

        bindMethod(table, "onRightClick", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onRightClick((player, blockState) -> {
                    try {
                        ILuaValue playerVal = (player instanceof com.luatweaker.api.entity.IPlayer p)
                                ? com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, p)
                                : engine.toLuaValue(player);
                        engine.callFunction(func, playerVal, engine.toLuaValue(blockState));
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error executing block rightClick callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });
    }


    private static void bindFluidBuilderMethods(ILuaEngine engine, ILuaTable table, IFluidBuilder builder) {
        bindMethod(table, "displayName", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.displayName(args[off].asString()); return table; });
        bindMethod(table, "name", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.displayName(args[off].asString()); return table; });
        bindMethod(table, "color", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.color(args[off].asInt()); return table; });
        bindMethod(table, "stillTexture", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.stillTexture(args[off].asString()); return table; });
        bindMethod(table, "still", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.stillTexture(args[off].asString()); return table; });
        bindMethod(table, "flowingTexture", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.flowingTexture(args[off].asString()); return table; });
        bindMethod(table, "flowing", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.flowingTexture(args[off].asString()); return table; });
        bindMethod(table, "flow", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.flowingTexture(args[off].asString()); return table; });
        bindMethod(table, "temperature", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.temperature(args[off].asInt()); return table; });
        bindMethod(table, "viscosity", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.viscosity(args[off].asInt()); return table; });
        bindMethod(table, "density", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.density(args[off].asInt()); return table; });
        bindMethod(table, "lightLevel", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.lightLevel(args[off].asInt()); return table; });
        bindMethod(table, "light", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.lightLevel(args[off].asInt()); return table; });
        bindMethod(table, "luminance", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.lightLevel(args[off].asInt()); return table; });
        bindMethod(table, "slopeFindDistance", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.slopeFindDistance(args[off].asInt()); return table; });
        bindMethod(table, "levelDecreasePerBlock", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.levelDecreasePerBlock(args[off].asInt()); return table; });
        bindMethod(table, "tickRate", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.tickRate(args[off].asInt()); return table; });
        bindMethod(table, "explosionResistance", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.explosionResistance((float) args[off].asDouble()); return table; });
        bindMethod(table, "rarity", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.rarity(args[off].asString()); return table; });
        bindMethod(table, "creativeTab", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.creativeTab(args[off].asString()); return table; });
        bindMethod(table, "tabGroup", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.creativeTab(args[off].asString()); return table; });
        bindMethod(table, "onTouch", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            ILuaValue func = args[off];
            if (func != null && func.isFunction()) {
                builder.onTouch(player -> {
                    try {
                        ILuaValue playerVal = com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, player);
                        engine.callFunction(func, playerVal);
                    } catch (Exception e) {
                        LuaTweakerLog.get().error(LogStage.SYSTEM, "Error executing fluid onTouch callback: " + e.getMessage());
                    }
                });
            }
            return table;
        });
    }

    private static void bindEntityBuilderMethods(ILuaEngine engine, ILuaTable table, IEntityBuilder builder, IDatapackService datapackService) {
        bindMethod(table, "category", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.category(args[off].asString()); return table; });
        bindMethod(table, "dimensions", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 2) builder.dimensions((float) args[off].asDouble(), (float) args[off + 1].asDouble());
            return table;
        });
        bindMethod(table, "maxHealth", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.maxHealth(args[off].asDouble()); return table; });
        bindMethod(table, "movementSpeed", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.movementSpeed(args[off].asDouble()); return table; });
        bindMethod(table, "speed", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.movementSpeed(args[off].asDouble()); return table; });
        bindMethod(table, "attackDamage", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.attackDamage(args[off].asDouble()); return table; });
        bindMethod(table, "followRange", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.followRange(args[off].asDouble()); return table; });
        bindMethod(table, "armor", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.armor(args[off].asDouble()); return table; });
        bindMethod(table, "knockbackResistance", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.knockbackResistance(args[off].asDouble()); return table; });
        bindMethod(table, "spawnEgg", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 2) builder.spawnEgg(args[off].asInt(), args[off + 1].asInt());
            return table;
        });
        bindMethod(table, "model", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.model(args[off].asString()); return table; });
        bindMethod(table, "texture", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.texture(args[off].asString()); return table; });
        bindMethod(table, "bbmodel", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.bbmodel(args[off].asString()); return table; });
        bindMethod(table, "ambientSound", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.ambientSound(args[off].asString()); return table; });
        bindMethod(table, "hurtSound", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.hurtSound(args[off].asString()); return table; });
        bindMethod(table, "deathSound", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.deathSound(args[off].asString()); return table; });
        bindMethod(table, "drop", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) {
                String itemId = args[off].asString();
                int minCount = args.length - off >= 2 ? args[off + 1].asInt() : 1;
                int maxCount = args.length - off >= 3 ? args[off + 2].asInt() : minCount;
                builder.drop(itemId, minCount, maxCount);
            }
            return table;
        });
        bindMethod(table, "experience", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.experience(args[off].asInt()); return table; });
        bindMethod(table, "creativeTab", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.creativeTab(args[off].asString()); return table; });
        bindMethod(table, "spawnEggTexture", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.spawnEggTexture(args[off].asString()); return table; });
        bindMethod(table, "eggTexture", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.spawnEggTexture(args[off].asString()); return table; });
        bindMethod(table, "parent", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.parent(args[off].asString()); return table; });
        bindMethod(table, "parentMob", args -> { int off = com.luatweaker.core.bind.LuaBinder.getOffset(args); builder.parentMob(args[off].asString()); return table; });
        bindMethod(table, "bossBar", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 1) {
                String title = args[off].asString();
                String color = args.length - off >= 2 ? args[off + 1].asString() : "RED";
                String overlay = args.length - off >= 3 ? args[off + 2].asString() : "PROGRESS";
                builder.bossBar(title, color, overlay);
            }
            return table;
        });
    }

    private static void bindMethod(ILuaTable table, String name, ILuaFunction func) {
        table.rawset(name, func);
        if (name != null && !name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
            String pascal = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            table.rawset(pascal, func);
        }
    }

    private static int getOffset(ILuaValue[] args) {
        if (args != null && args.length > 0 && args[0] != null && args[0].isTable()) {
            return 1;
        }
        return 0;
    }

}
