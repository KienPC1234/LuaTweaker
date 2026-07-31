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
            if (args.length < 2) throw new IllegalArgumentException("startup:registerProjectile requires (id, [configTable])");
            String id = args[1].asString();
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Projectile definition: " + id);
            return null;
        });


        // startup:createBlock("custom_ruby_block", function(block) ... end)
        startupTable.rawset("createBlock", args -> {
            if (args.length < 2) throw new IllegalArgumentException("startup:createBlock requires (id, builderFunc)");
            String id = args[1].asString();
            ILuaValue callbackVal = args[2];

            contentService.createBlock(id, builder -> {
                if (callbackVal.isFunction()) {
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
            if (args.length < 2) throw new IllegalArgumentException("startup:createFluid requires (id, builderFunc)");
            String id = args[1].asString();
            ILuaValue callbackVal = args[2];

            contentService.createFluid(id, builder -> {
                if (callbackVal.isFunction()) {
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
            if (args.length < 2) throw new IllegalArgumentException("startup:createRangedItem requires (id, builderFunc)");
            String id = args[1].asString();
            ILuaValue callbackVal = args[2];

            contentService.createRangedItem(id, builder -> {
                if (callbackVal.isFunction()) {
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
            if (args.length < 2) throw new IllegalArgumentException("startup:createEntity requires (id, builderFunc)");
            String id = args[1].asString();
            ILuaValue callbackVal = args[2];

            contentService.createEntity(id, builder -> {
                if (callbackVal.isFunction()) {
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
            if (args.length < 2) throw new IllegalArgumentException("startup:createStairs requires (id, baseBlockId)");
            String id = args[1].asString();
            String baseId = args.length >= 3 ? args[2].asString() : id;
            ILuaValue callbackVal = args.length >= 4 && args[3].isFunction() ? args[3] : null;
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
            if (args.length < 2) throw new IllegalArgumentException("startup:createSlab requires (id, baseBlockId)");
            String id = args[1].asString();
            String baseId = args.length >= 3 ? args[2].asString() : id;
            ILuaValue callbackVal = args.length >= 4 && args[3].isFunction() ? args[3] : null;
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
            if (args.length < 2) throw new IllegalArgumentException("startup:createWall requires (id, baseBlockId)");
            String id = args[1].asString();
            String baseId = args.length >= 3 ? args[2].asString() : id;
            ILuaValue callbackVal = args.length >= 4 && args[3].isFunction() ? args[3] : null;
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
            if (args.length < 2) throw new IllegalArgumentException("startup:createTab requires (id, builderFunc)");
            String id = args[1].asString();
            ILuaValue callbackVal = args[2];

            contentService.createTab(id, builder -> {
                if (callbackVal.isFunction()) {
                    ILuaTable tabTable = engine.createTable();
                    tabTable.rawset("title", a -> { builder.title(a[1].asString()); return tabTable; });
                    tabTable.rawset("icon", a -> { builder.icon(a[1].asString()); return tabTable; });
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
            if (args.length < 2) throw new IllegalArgumentException("startup:createArmorMaterial requires (id, builderFunc)");
            String id = args[1].asString();
            ILuaValue callbackVal = args[2];

            contentService.createArmorMaterial(id, builder -> {
                if (callbackVal.isFunction()) {
                    ILuaTable matTable = engine.createTable();
                    matTable.rawset("layer", a -> { builder.layer(a[1].asString()); return matTable; });
                    matTable.rawset("equipSound", a -> { builder.equipSound(a[1].asString()); return matTable; });
                    matTable.rawset("toughness", a -> { builder.toughness((float) a[1].asDouble()); return matTable; });
                    matTable.rawset("knockbackResistance", a -> { builder.knockbackResistance((float) a[1].asDouble()); return matTable; });
                    matTable.rawset("enchantability", a -> { builder.enchantability(a[1].asInt()); return matTable; });
                    matTable.rawset("defense", a -> {
                        if (a.length >= 3) {
                            builder.defense(a[1].asString(), a[2].asInt());
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

        // startup:createEntity("custom_zombie", function(entity) ... end)
        startupTable.rawset("createEntity", args -> {
            if (args.length < 2) throw new IllegalArgumentException("startup:createEntity requires (id, builderFunc)");
            String id = args[1].asString();
            ILuaValue callbackVal = args[2];

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
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Entity Type builder: " + id);
            return null;
        });


        env.rawset("startup", startupTable);

        engine.registerService("Startup", contentService);

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

        table.rawset("type", args -> { builder.type(args[1].asString()); return table; });
        table.rawset("maxStackSize", args -> { builder.maxStackSize(args[1].asInt()); return table; });
        table.rawset("rarity", args -> { builder.rarity(args[1].asString()); return table; });
        table.rawset("durability", args -> { builder.durability(args[1].asInt()); return table; });
        table.rawset("maxDamage", args -> { builder.durability(args[1].asInt()); return table; });
        table.rawset("miningLevel", args -> {
            Object obj = args[1].toJavaObject();
            if (obj instanceof Number num) builder.miningLevel(num.intValue());
            else builder.miningLevel(args[1].asString());
            return table;
        });
        table.rawset("tier", args -> {
            Object obj = args[1].toJavaObject();
            if (obj instanceof Number num) builder.miningLevel(num.intValue());
            else builder.miningLevel(args[1].asString());
            return table;
        });
        table.rawset("miningSpeed", args -> { builder.miningSpeed((float) args[1].asDouble()); return table; });
        table.rawset("efficiency", args -> { builder.miningSpeed((float) args[1].asDouble()); return table; });
        table.rawset("attackDamage", args -> { builder.attackDamage((float) args[1].asDouble()); return table; });
        table.rawset("attackSpeed", args -> { builder.attackSpeed((float) args[1].asDouble()); return table; });
        table.rawset("defense", args -> { builder.defense(args[1].asInt()); return table; });
        table.rawset("toughness", args -> { builder.toughness((float) args[1].asDouble()); return table; });
        table.rawset("knockbackResistance", args -> { builder.knockbackResistance((float) args[1].asDouble()); return table; });
        table.rawset("enchantability", args -> { builder.enchantability(args[1].asInt()); return table; });
        table.rawset("burnTime", args -> { builder.burnTime(args[1].asInt()); return table; });
        table.rawset("displayName", args -> { builder.displayName(args[1].asString()); return table; });
        table.rawset("model", args -> { builder.model(args[1].asString()); return table; });
        table.rawset("texture", args -> { builder.texture(args[1].asString()); return table; });
        table.rawset("armorMaterial", args -> { builder.armorMaterial(args[1].asString()); return table; });
        table.rawset("armorTexture", args -> { builder.armorTexture(args[1].asString()); return table; });
        table.rawset("creativeTab", args -> { builder.creativeTab(args[1].asString()); return table; });

        table.rawset("tabGroup", args -> { builder.creativeTab(args[1].asString()); return table; });
        table.rawset("tag", args -> {
            if (args.length >= 2) {
                String tagId = args[1].asString();
                builder.tag(tagId);
                if (datapackService != null) {
                    String fullId = builder.getId().contains(":") ? builder.getId() : "luatweaker:" + builder.getId();
                    datapackService.addTag("item", tagId, java.util.List.of(fullId));
                }
            }
            return table;
        });

        table.rawset("onRightClick", args -> {
            ILuaValue func = args[1];
            if (func.isFunction()) {
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

        table.rawset("onHitEntity", args -> {
            ILuaValue func = args[1];
            if (func.isFunction()) {
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

        table.rawset("food", args -> {
            if (args.length >= 3) {
                builder.food(args[1].asInt(), (float) args[2].asDouble());
            }
            return table;
        });

        table.rawset("alwaysEdible", args -> { builder.alwaysEdible(); return table; });

        table.rawset("onConsume", args -> {
            ILuaValue func = args[1];
            if (func.isFunction()) {
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

        table.rawset("glow", args -> {
            boolean enable = args.length < 2 || args[1].asBoolean();
            builder.glow(enable);
            return table;
        });

        table.rawset("tooltip", args -> {
            if (args.length >= 2) builder.tooltip(args[1].asString());
            return table;
        });

    }

    private static void bindBlockBuilderMethods(ILuaEngine engine, ILuaTable table, IBlockBuilder builder, IDatapackService datapackService) {
        table.rawset("hardness", args -> { builder.hardness((float) args[1].asDouble()); return table; });
        table.rawset("resistance", args -> { builder.resistance((float) args[1].asDouble()); return table; });
        table.rawset("lightLevel", args -> { builder.lightLevel(args[1].asInt()); return table; });
        table.rawset("light", args -> { builder.lightLevel(args[1].asInt()); return table; });
        table.rawset("emissive", args -> {
            if (args.length >= 2) {
                Object val = args[1].toJavaObject();
                if (val instanceof Number num) {
                    builder.lightLevel(Math.min(15, Math.max(0, num.intValue())));
                } else if (val instanceof Boolean b) {
                    builder.lightLevel(b ? 15 : 0);
                } else {
                    builder.lightLevel(args[1].asInt());
                }
            } else {
                builder.lightLevel(15);
            }
            return table;
        });


        table.rawset("soundType", args -> { builder.soundType(args[1].asString()); return table; });
        table.rawset("requiresTool", args -> { builder.requiresTool(args[1].asBoolean()); return table; });
        table.rawset("mineableWith", args -> { builder.mineableWith(args[1].asString()); return table; });
        table.rawset("miningLevel", args -> {
            Object obj = args[1].toJavaObject();
            if (obj instanceof Number num) builder.miningLevel(num.intValue());
            else builder.miningLevel(args[1].asString());
            return table;
        });
        table.rawset("friction", args -> { builder.friction((float) args[1].asDouble()); return table; });
        table.rawset("model", args -> { builder.model(args[1].asString()); return table; });

        table.rawset("drop", args -> {
            if (args.length >= 2) {
                String itemId = args[1].asString();
                int minCount = args.length >= 3 ? args[2].asInt() : 1;
                int maxCount = args.length >= 4 ? args[3].asInt() : minCount;
                builder.drop(itemId, minCount, maxCount);
            }
            return table;
        });

        table.rawset("dropExperience", args -> {
            if (args.length >= 2) {
                int minExp = args[1].asInt();
                int maxExp = args.length >= 3 ? args[2].asInt() : minExp;
                builder.dropExperience(minExp, maxExp);
            }
            return table;
        });

        table.rawset("texture", args -> { builder.texture(args[1].asString()); return table; });
        table.rawset("creativeTab", args -> { builder.creativeTab(args[1].asString()); return table; });
        table.rawset("tabGroup", args -> { builder.creativeTab(args[1].asString()); return table; });
        table.rawset("tag", args -> {
            if (args.length >= 2) {
                String tagId = args[1].asString();
                builder.tag(tagId);
                if (datapackService != null) {
                    String fullId = builder.getId().contains(":") ? builder.getId() : "luatweaker:" + builder.getId();
                    datapackService.addTag("block", tagId, java.util.List.of(fullId));
                }
            }
            return table;
        });

        table.rawset("onRightClick", args -> {
            ILuaValue func = args[1];
            if (func.isFunction()) {
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
        table.rawset("color", args -> { builder.color(args[1].asInt()); return table; });
        table.rawset("stillTexture", args -> { builder.stillTexture(args[1].asString()); return table; });
        table.rawset("flowingTexture", args -> { builder.flowingTexture(args[1].asString()); return table; });
        table.rawset("temperature", args -> { builder.temperature(args[1].asInt()); return table; });
        table.rawset("viscosity", args -> { builder.viscosity(args[1].asInt()); return table; });
        table.rawset("density", args -> { builder.density(args[1].asInt()); return table; });
        table.rawset("lightLevel", args -> { builder.lightLevel(args[1].asInt()); return table; });
        table.rawset("light", args -> { builder.lightLevel(args[1].asInt()); return table; });
        table.rawset("slopeFindDistance", args -> { builder.slopeFindDistance(args[1].asInt()); return table; });
        table.rawset("levelDecreasePerBlock", args -> { builder.levelDecreasePerBlock(args[1].asInt()); return table; });
        table.rawset("tickRate", args -> { builder.tickRate(args[1].asInt()); return table; });
        table.rawset("explosionResistance", args -> { builder.explosionResistance((float) args[1].asDouble()); return table; });
        table.rawset("rarity", args -> { builder.rarity(args[1].asString()); return table; });
        table.rawset("creativeTab", args -> { builder.creativeTab(args[1].asString()); return table; });
        table.rawset("tabGroup", args -> { builder.creativeTab(args[1].asString()); return table; });
        table.rawset("onTouch", args -> {
            ILuaValue func = args[1];
            if (func.isFunction()) {
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
        table.rawset("category", args -> { builder.category(args[1].asString()); return table; });
        table.rawset("dimensions", args -> {
            if (args.length >= 3) builder.dimensions((float) args[1].asDouble(), (float) args[2].asDouble());
            return table;
        });
        table.rawset("maxHealth", args -> { builder.maxHealth(args[1].asDouble()); return table; });
        table.rawset("movementSpeed", args -> { builder.movementSpeed(args[1].asDouble()); return table; });
        table.rawset("speed", args -> { builder.movementSpeed(args[1].asDouble()); return table; });
        table.rawset("attackDamage", args -> { builder.attackDamage(args[1].asDouble()); return table; });
        table.rawset("followRange", args -> { builder.followRange(args[1].asDouble()); return table; });
        table.rawset("armor", args -> { builder.armor(args[1].asDouble()); return table; });
        table.rawset("knockbackResistance", args -> { builder.knockbackResistance(args[1].asDouble()); return table; });
        table.rawset("spawnEgg", args -> {
            if (args.length >= 3) builder.spawnEgg(args[1].asInt(), args[2].asInt());
            return table;
        });
        table.rawset("model", args -> { builder.model(args[1].asString()); return table; });
        table.rawset("texture", args -> { builder.texture(args[1].asString()); return table; });
        table.rawset("bbmodel", args -> { builder.bbmodel(args[1].asString()); return table; });
        table.rawset("ambientSound", args -> { builder.ambientSound(args[1].asString()); return table; });
        table.rawset("hurtSound", args -> { builder.hurtSound(args[1].asString()); return table; });
        table.rawset("deathSound", args -> { builder.deathSound(args[1].asString()); return table; });
        table.rawset("drop", args -> {
            if (args.length >= 2) {
                String itemId = args[1].asString();
                int minCount = args.length >= 3 ? args[2].asInt() : 1;
                int maxCount = args.length >= 4 ? args[3].asInt() : minCount;
                builder.drop(itemId, minCount, maxCount);
            }
            return table;
        });
        table.rawset("experience", args -> { builder.experience(args[1].asInt()); return table; });
        table.rawset("creativeTab", args -> { builder.creativeTab(args[1].asString()); return table; });
        table.rawset("spawnEggTexture", args -> { builder.spawnEggTexture(args[1].asString()); return table; });
        table.rawset("eggTexture", args -> { builder.spawnEggTexture(args[1].asString()); return table; });
        table.rawset("parent", args -> { builder.parent(args[1].asString()); return table; });
        table.rawset("parentMob", args -> { builder.parentMob(args[1].asString()); return table; });
        table.rawset("bossBar", args -> {
            String title = args[1].asString();
            String color = args.length >= 3 ? args[2].asString() : "RED";
            String overlay = args.length >= 4 ? args[3].asString() : "PROGRESS";
            builder.bossBar(title, color, overlay);
            return table;
        });
    }

}
