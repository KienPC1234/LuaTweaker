package com.luatweaker.content;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;

/**
 * Binds {@code Content.NewMobEffect}/{@code Content.NewPotion} and their callback variants.
 * The actual registry work happens later in the NeoForge registry event (see EffectRegistry).
 */
final class ContentEffectBinding {

    private static final String DEFAULT_OPERATION = "ADD_VALUE";

    private ContentEffectBinding() {
    }

    static void bindEffectCreators(ILuaEngine engine, ILuaTable startupTable) {
        startupTable.rawset("NewMobEffect", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Content:NewMobEffect requires an id");
            }
            return bindMobEffectBuilder(engine, new MobEffectBuilderImpl(args[off].asString()));
        });
        startupTable.rawset("createMobEffect", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException(
                        "Content:createMobEffect requires (id, builderFunc)");
            }
            String id = args[off].asString();
            MobEffectBuilderImpl builder = new MobEffectBuilderImpl(id);
            ILuaTable table = bindMobEffectBuilder(engine, builder);
            ContentBindingSupport.invokeBuilderCallback(engine, "mob effect", id,
                    args[off + 1], table);
            builder.register();
            return null;
        });
        startupTable.rawset("registerMobEffect", startupTable.rawget("createMobEffect"));

        startupTable.rawset("NewPotion", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
                throw new IllegalArgumentException("Content:NewPotion requires an id");
            }
            return bindPotionBuilder(engine, new PotionBuilderImpl(args[off].asString()));
        });
        startupTable.rawset("createPotion", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException(
                        "Content:createPotion requires (id, builderFunc)");
            }
            String id = args[off].asString();
            PotionBuilderImpl builder = new PotionBuilderImpl(id);
            ILuaTable table = bindPotionBuilder(engine, builder);
            ContentBindingSupport.invokeBuilderCallback(engine, "potion", id,
                    args[off + 1], table);
            builder.register();
            return null;
        });
        startupTable.rawset("registerPotion", startupTable.rawget("createPotion"));
    }

    private static ILuaTable bindMobEffectBuilder(ILuaEngine engine, MobEffectBuilderImpl builder) {
        ILuaTable table = engine.createTable();
        ContentBindingSupport.bindMethod(table, "color", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "effect:color");
            builder.color(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "category", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "effect:category");
            builder.category(args[off].asString());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "attribute", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 2)) {
                throw new IllegalArgumentException(
                        "Mob effect:attribute requires (attributeId, amount, [operation])");
            }
            String attributeId = args[off].asString();
            double amount = args[off + 1].asDouble();
            String operation = ContentBindingSupport.hasMinArgs(args, off, 3)
                    ? args[off + 2].asString() : DEFAULT_OPERATION;
            builder.attribute(attributeId, amount, operation);
            return table;
        });
        ContentBindingSupport.bindMethod(table, "texture", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "effect:texture");
            builder.texture(args[off].asString());
            return table;
        });
        table.rawset("icon", table.rawget("texture"));
        table.rawset("Icon", table.rawget("texture"));
        ContentBindingSupport.bindMethod(table, "displayName", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "effect:displayName");
            builder.displayName(args[off].asString());
            return table;
        });
        table.rawset("name", table.rawget("displayName"));
        table.rawset("Name", table.rawget("displayName"));
        ContentBindingSupport.bindMethod(table, "instantaneous", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "effect:instantaneous");
            builder.instantaneous(args[off].asBoolean());
            return table;
        });
        table.rawset("instant", args -> {
            builder.instantaneous(true);
            return table;
        });
        table.rawset("Instant", table.rawget("instant"));
        ContentBindingSupport.bindMethod(table, "tickInterval", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "effect:tickInterval");
            builder.tickInterval(args[off].asInt());
            return table;
        });
        ContentBindingSupport.bindMethod(table, "curable", args -> {
            int off = LuaBinder.getOffset(args);
            ContentBindingSupport.requireArgs(args, off, 1, "effect:curable");
            builder.curable(args[off].asBoolean());
            return table;
        });
        table.rawset("beneficial", args -> {
            builder.category("BENEFICIAL");
            return table;
        });
        table.rawset("Beneficial", table.rawget("beneficial"));
        table.rawset("harmful", args -> {
            builder.category("HARMFUL");
            return table;
        });
        table.rawset("Harmful", table.rawget("harmful"));
        table.rawset("neutral", args -> {
            builder.category("NEUTRAL");
            return table;
        });
        table.rawset("Neutral", table.rawget("neutral"));
        table.rawset("Register", args -> {
            builder.register();
            return table;
        });
        return table;
    }

    private static ILuaTable bindPotionBuilder(ILuaEngine engine, PotionBuilderImpl builder) {
        ILuaTable table = engine.createTable();
        ContentBindingSupport.bindMethod(table, "effect", args -> {
            int off = LuaBinder.getOffset(args);
            if (!ContentBindingSupport.hasMinArgs(args, off, 3)) {
                throw new IllegalArgumentException(
                        "Potion:effect requires (effectId, durationTicks, amplifier)");
            }
            builder.effect(args[off].asString(), args[off + 1].asInt(), args[off + 2].asInt());
            return table;
        });
        table.rawset("Register", args -> {
            builder.register();
            return table;
        });
        return table;
    }
}
