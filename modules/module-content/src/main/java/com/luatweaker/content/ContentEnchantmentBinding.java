package com.luatweaker.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Binds {@code Content.NewEnchantment(id)} and {@code Content:createEnchantment(id, fn)}.
 * The fluent methods mutate {@link EnchantmentConfig}; {@link EnchantmentBuilderImpl} owns
 * validation and the datapack write.
 */
final class ContentEnchantmentBinding {

    private static final String ERROR_BLANK_SLOTS = "Enchantment slots requires a string or a table";

    private ContentEnchantmentBinding() {
    }

    static void bindEnchantmentCreator(ILuaEngine engine, ILuaTable startupTable,
                                       IDatapackService datapackService) {
        startupTable.rawset("NewEnchantment", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Content:NewEnchantment requires an id");
            }
            return bindBuilder(engine, new EnchantmentBuilderImpl(args[off].asString(), datapackService));
        });

        startupTable.rawset("createEnchantment", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException(
                        "Content:createEnchantment requires (id, builderFunc)");
            }
            String id = args[off].asString();
            ILuaValue callback = args[off + 1];
            EnchantmentBuilderImpl builder =
                    new EnchantmentBuilderImpl(id, datapackService);
            ILuaTable table = bindBuilder(engine, builder);
            ContentBindingSupport.invokeBuilderCallback(engine, "enchantment", id, callback, table);
            builder.register();
            return null;
        });
        startupTable.rawset("registerEnchantment", startupTable.rawget("createEnchantment"));
    }

    private static ILuaTable bindBuilder(ILuaEngine engine, EnchantmentBuilderImpl builder) {
        ILuaTable table = engine.createTable();
        EnchantmentConfig config = builder.config();

        ContentBindingSupport.bindMethod(table, "description", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "enchantment:description");
            config.descriptionText = args[off].asString();
            config.usesTranslation = false;
            return table;
        });
        ContentBindingSupport.bindMethod(table, "descriptionTranslate", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "enchantment:descriptionTranslate");
            config.descriptionKey = args[off].asString();
            config.usesTranslation = true;
            return table;
        });
        ContentBindingSupport.bindMethod(table, "supportedItems", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "enchantment:supportedItems");
            config.supportedItems = args[off].asString();
            return table;
        });
        ContentBindingSupport.bindMethod(table, "primaryItems", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "enchantment:primaryItems");
            config.primaryItems = args[off].asString();
            return table;
        });
        ContentBindingSupport.bindMethod(table, "weight", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "enchantment:weight");
            config.weight = args[off].asInt();
            return table;
        });
        ContentBindingSupport.bindMethod(table, "maxLevel", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "enchantment:maxLevel");
            config.maxLevel = args[off].asInt();
            return table;
        });
        ContentBindingSupport.bindMethod(table, "minCost", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 2, "enchantment:minCost");
            config.minCost = new EnchantmentJson.Cost(args[off].asInt(), args[off + 1].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "maxCost", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 2, "enchantment:maxCost");
            config.maxCost = new EnchantmentJson.Cost(args[off].asInt(), args[off + 1].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "anvilCost", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "enchantment:anvilCost");
            config.anvilCost = args[off].asInt();
            return table;
        });
        ContentBindingSupport.bindMethod(table, "slots", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "enchantment:slots");
            config.slots = readSlots(args[off]);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "exclusiveSet", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "enchantment:exclusiveSet");
            config.exclusiveSet = args[off].asString();
            return table;
        });
        ContentBindingSupport.bindMethod(table, "damage", args -> {
            int off = LuaBinder.getOffset(args);
            double base = args[off].asDouble();
            double perLevel = ContentBindingSupport.hasMinArgs(args, off, 2) ? args[off + 1].asDouble() : 0.0;
            EnchantmentJsonConverter.addValueEffect(config.effects, "minecraft:damage", base, perLevel);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "damageProtection", args -> {
            int off = LuaBinder.getOffset(args);
            double base = args[off].asDouble();
            double perLevel = ContentBindingSupport.hasMinArgs(args, off, 2) ? args[off + 1].asDouble() : 0.0;
            EnchantmentJsonConverter.addValueEffect(config.effects, "minecraft:damage_protection", base, perLevel);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "knockback", args -> {
            int off = LuaBinder.getOffset(args);
            EnchantmentJsonConverter.addValueEffect(config.effects, "minecraft:knockback", args[off].asDouble(), 0.0);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "curse", args -> {
            int off = LuaBinder.getOffset(args);
            config.curse = !ContentBindingSupport.hasMinArgs(args, off, 1) || args[off].asBoolean();
            return table;
        });
        ContentBindingSupport.bindMethod(table, "effects", args -> {
            ILuaValue val = args[LuaBinder.getOffset(args)];
            JsonObject incoming = val.isTable()
                    ? EnchantmentJsonConverter.convertTableToJsonObject(val.asTable())
                    : readEffects(val.asString());
            for (Map.Entry<String, JsonElement> entry : incoming.entrySet()) {
                config.effects.add(entry.getKey(), entry.getValue());
            }
            return table;
        });
        ContentBindingSupport.bindMethod(table, "toJson", args -> engine.wrapString(builder.toJson()));
        table.rawset("Register", args -> {
            builder.register();
            return table;
        });
        return table;
    }

    private static List<String> readSlots(ILuaValue value) {
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException(ERROR_BLANK_SLOTS);
        }
        if (!value.isTable()) {
            return List.of(value.asString());
        }
        return readSlotTable(value.asTable());
    }

    private static List<String> readSlotTable(ILuaTable table) {
        int length = table.length();
        if (length == 0) {
            throw new IllegalArgumentException(ERROR_BLANK_SLOTS);
        }
        List<String> slots = new ArrayList<>(length);
        for (int index = 1; index <= length; index++) {
            slots.add(readSlot(table, index));
        }
        return slots;
    }

    private static String readSlot(ILuaTable table, int index) {
        ILuaValue slot = table.rawget(index);
        if (slot == null || slot.isNil()) {
            throw new IllegalArgumentException("Enchantment slot #" + index + " is missing");
        }
        return slot.asString();
    }

    private static JsonObject readEffects(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Enchantment effects JSON must not be blank");
        }
        try {
            var parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Enchantment effects JSON must be an object, got: " + parsed);
            }
            return parsed.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException(
                    "Enchantment effects JSON is invalid: " + e.getMessage(), e);
        }
    }
}
