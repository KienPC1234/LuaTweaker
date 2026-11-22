package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds Lua payload tables for combat events.
 */
public final class CombatPayloads {

    private static final String KEY_ENTITY_LOWER = "entity";
    private static final String KEY_ENTITY_UPPER = "Entity";
    private static final String KEY_AMOUNT_LOWER = "amount";

    private CombatPayloads() {
    }

    public static @NotNull ILuaTable buildHurt(@NotNull ILuaEngine engine, @NotNull LivingEntity victim,
                                              @NotNull DamageSource source, float amount) {
        ILuaTable table = PayloadTables.base(engine, source);
        PayloadEntities.putEntity(engine, table, "victim", "Victim", victim);
        PayloadEntities.putEntity(engine, table, KEY_ENTITY_LOWER, KEY_ENTITY_UPPER, victim);
        if (source.getEntity() != null) {
            PayloadEntities.putEntity(engine, table, "attacker", "Attacker", source.getEntity());
        }
        if (source.getDirectEntity() != null) {
            PayloadEntities.putEntity(engine, table, "directAttacker", "DirectAttacker", source.getDirectEntity());
        }
        PayloadTables.putBoth(table, KEY_AMOUNT_LOWER, engine.wrapNumber(amount));
        PayloadTables.putBoth(table, "damage", engine.wrapNumber(amount));
        if (source.typeHolder() != null && source.typeHolder().unwrapKey().isPresent()) {
            PayloadTables.putBoth(table, "damageType",
                    engine.wrapString(source.typeHolder().unwrapKey().get().location().toString()));
        }
        return table;
    }

    public static @NotNull ILuaTable buildDeath(@NotNull ILuaEngine engine, @NotNull LivingEntity victim,
                                               @NotNull DamageSource source) {
        ILuaTable table = PayloadTables.base(engine, source);
        PayloadEntities.putEntity(engine, table, "victim", "Victim", victim);
        PayloadEntities.putEntity(engine, table, KEY_ENTITY_LOWER, KEY_ENTITY_UPPER, victim);
        if (source.getEntity() != null) {
            PayloadEntities.putEntity(engine, table, "killer", "Killer", source.getEntity());
        }
        if (source.typeHolder() != null && source.typeHolder().unwrapKey().isPresent()) {
            PayloadTables.putBoth(table, "damageType",
                    engine.wrapString(source.typeHolder().unwrapKey().get().location().toString()));
        }
        return table;
    }

    public static @NotNull ILuaTable buildTarget(@NotNull ILuaEngine engine, @NotNull LivingEntity entity,
                                                @Nullable LivingEntity target) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putEntity(engine, table, KEY_ENTITY_LOWER, KEY_ENTITY_UPPER, entity);
        if (target != null) {
            PayloadEntities.putEntity(engine, table, "target", "Target", target);
        }
        return table;
    }

    public static @NotNull ILuaTable buildHeal(@NotNull ILuaEngine engine, @NotNull LivingEntity entity, float amount) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putEntity(engine, table, KEY_ENTITY_LOWER, KEY_ENTITY_UPPER, entity);
        PayloadTables.putBoth(table, KEY_AMOUNT_LOWER, engine.wrapNumber(amount));
        return table;
    }
}
