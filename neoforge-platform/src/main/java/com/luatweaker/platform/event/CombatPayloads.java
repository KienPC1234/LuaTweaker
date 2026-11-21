package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Combat payloads: damage, death, target switches and healing. */
final class CombatPayloads {

    private static final String AMOUNT = "amount";
    private static final String DAMAGE_TYPE = "damageType";
    private static final String ENTITY = "entity";
    private static final String ENTITY_PASCAL = "Entity";
    private static final String SET_AMOUNT = "setAmount";
    private static final int MIN_ONE_ARG = 1;

    private CombatPayloads() {
    }

    static void register(@NotNull PayloadRegistry registry) {
        registry.add(LivingDamageEvent.Pre.class, CombatPayloads::buildHurt);
        registry.add(LivingDeathEvent.class, CombatPayloads::buildDeath);
        registry.add(LivingChangeTargetEvent.class, CombatPayloads::buildTarget);
        registry.add(LivingHealEvent.class, CombatPayloads::buildHeal);
    }

    private static ILuaTable buildHurt(ILuaEngine engine, LivingDamageEvent.Pre event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putEntity(engine, table, ENTITY, ENTITY_PASCAL, event.getEntity());
        if (event.getSource() != null) {
            PayloadEntities.putEntity(engine, table, "attacker", "Attacker", event.getSource().getEntity());
        }
        PayloadTables.putBoth(table, AMOUNT, engine.wrapNumber(event.getNewDamage()));
        PayloadTables.putBoth(table, DAMAGE_TYPE, engine.wrapString(damageTypeOf(event.getSource())));
        table.rawset(SET_AMOUNT, (ILuaFunction) args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_ONE_ARG) {
                event.setNewDamage((float) args[off].asDouble());
            }
            return engine.nilValue();
        });
        PayloadTables.putPascal(table, SET_AMOUNT, table.rawget(SET_AMOUNT));
        return table;
    }

    private static ILuaTable buildDeath(ILuaEngine engine, LivingDeathEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putEntity(engine, table, ENTITY, ENTITY_PASCAL, event.getEntity());
        if (event.getSource() != null) {
            PayloadEntities.putEntity(engine, table, "attacker", "Attacker", event.getSource().getEntity());
        }
        PayloadTables.putBoth(table, DAMAGE_TYPE, engine.wrapString(damageTypeOf(event.getSource())));
        return table;
    }

    private static ILuaTable buildTarget(ILuaEngine engine, LivingChangeTargetEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putEntity(engine, table, ENTITY, ENTITY_PASCAL, event.getEntity());
        Entity target = event.getNewAboutToBeSetTarget();
        if (target != null) {
            PayloadEntities.putEntity(engine, table, "target", "Target", target);
        }
        return table;
    }

    private static ILuaTable buildHeal(ILuaEngine engine, LivingHealEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putEntity(engine, table, ENTITY, ENTITY_PASCAL, event.getEntity());
        PayloadTables.putBoth(table, AMOUNT, engine.wrapNumber(event.getAmount()));
        table.rawset(SET_AMOUNT, (ILuaFunction) args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_ONE_ARG) {
                event.setAmount((float) args[off].asDouble());
            }
            return engine.nilValue();
        });
        PayloadTables.putPascal(table, SET_AMOUNT, table.rawget(SET_AMOUNT));
        return table;
    }

    private static String damageTypeOf(@Nullable DamageSource source) {
        return source != null ? source.getMsgId() : "generic";
    }
}
