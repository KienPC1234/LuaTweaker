package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

/** Entity lifecycle and inventory payloads: spawn, interact, toss, pickup, projectile impact. */
final class EntityPayloads {

    private static final String HAND = "hand";
    private static final String DIMENSION = "dimension";

    private EntityPayloads() {
    }

    static void register(@NotNull PayloadRegistry registry) {
        registry.add(EntityJoinLevelEvent.class, EntityPayloads::buildSpawn);
        registry.add(PlayerInteractEvent.EntityInteract.class, EntityPayloads::buildInteractEntity);
        registry.add(ItemTossEvent.class, EntityPayloads::buildToss);
        registry.add(ItemEntityPickupEvent.Pre.class, EntityPayloads::buildPickup);
        registry.add(ProjectileImpactEvent.class, EntityPayloads::buildProjectile);
    }

    private static ILuaTable buildSpawn(ILuaEngine engine, EntityJoinLevelEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putEntity(engine, table, "entity", "Entity", event.getEntity());
        PayloadTables.putBoth(table, DIMENSION,
                engine.wrapString(PayloadBlocks.dimensionOf(event.getLevel())));
        return table;
    }

    private static ILuaTable buildInteractEntity(ILuaEngine engine, PlayerInteractEvent.EntityInteract event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getEntity());
        PayloadEntities.putEntity(engine, table, "target", "Target", event.getTarget());
        PayloadTables.putBoth(table, HAND, engine.wrapString(event.getHand().name()));
        return table;
    }

    private static ILuaTable buildToss(ILuaEngine engine, ItemTossEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getPlayer());
        PayloadEntities.putItem(engine, table, event.getEntity().getItem());
        return table;
    }

    private static ILuaTable buildPickup(ILuaEngine engine, ItemEntityPickupEvent.Pre event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getPlayer());
        PayloadEntities.putItem(engine, table, event.getItemEntity().getItem());
        return table;
    }

    private static ILuaTable buildProjectile(ILuaEngine engine, ProjectileImpactEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putEntity(engine, table, "projectile", "Projectile", event.getProjectile());
        return table;
    }
}
