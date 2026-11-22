package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds Lua payload tables for entity lifecycle and interaction events.
 */
public final class EntityPayloads {

    private EntityPayloads() {
    }

    public static @NotNull ILuaTable buildSpawn(@NotNull ILuaEngine engine, @NotNull Entity entity, @NotNull Level level) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putEntity(engine, table, "entity", "Entity", entity);
        PayloadBlocks.putBlockAndPos(engine, table, level, entity.blockPosition());
        return table;
    }

    public static @NotNull ILuaTable buildInteractEntity(@NotNull ILuaEngine engine, @NotNull Player player,
                                                        @NotNull Entity target, @NotNull InteractionHand hand) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadEntities.putEntity(engine, table, "target", "Target", target);
        PayloadTables.putBoth(table, "hand", engine.wrapString(hand.name()));
        return table;
    }

    public static @NotNull ILuaTable buildAttackEntity(@NotNull ILuaEngine engine, @NotNull Player player,
                                                      @NotNull Entity target) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadEntities.putEntity(engine, table, "target", "Target", target);
        return table;
    }

    public static @NotNull ILuaTable buildToss(@NotNull ILuaEngine engine, @NotNull Player player,
                                              @NotNull ItemEntity itemEntity) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadEntities.putItem(engine, table, itemEntity.getItem());
        return table;
    }

    public static @NotNull ILuaTable buildPickup(@NotNull ILuaEngine engine, @NotNull Player player,
                                                @NotNull ItemEntity itemEntity, @Nullable ItemStack stack) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        PayloadEntities.putItem(engine, table, stack != null ? stack : itemEntity.getItem());
        return table;
    }

    public static @NotNull ILuaTable buildProjectile(@NotNull ILuaEngine engine, @NotNull Projectile projectile,
                                                    @Nullable HitResult hitResult) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putEntity(engine, table, "projectile", "Projectile", projectile);
        if (projectile.getOwner() instanceof Player owner) {
            PayloadEntities.putPlayer(engine, table, owner);
        } else if (projectile.getOwner() != null) {
            PayloadEntities.putEntity(engine, table, "owner", "Owner", projectile.getOwner());
        }
        if (hitResult instanceof EntityHitResult entityHit) {
            PayloadEntities.putEntity(engine, table, "hitEntity", "HitEntity", entityHit.getEntity());
        }
        return table;
    }
}
