package com.luatweaker.neoforge.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Key gameplay events must resolve through their short Lua aliases. */
class EventAliasMappingTest {

    @Test
    void livingDropsAliasResolves() {
        assertEquals(net.neoforged.neoforge.event.entity.living.LivingDropsEvent.class,
                EventAliasRegistry.getClassForAlias("LivingDrops"),
                "the LivingDrops alias must resolve to the vanilla drops event");
    }

    @Test
    void blockDropsAliasResolves() {
        assertEquals(net.neoforged.neoforge.event.level.BlockDropsEvent.class,
                EventAliasRegistry.getClassForAlias("BlockDrops"),
                "the BlockDrops alias must resolve to the vanilla block drops event");
    }

    @Test
    void entityTargetAliasResolves() {
        assertEquals(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.class,
                EventAliasRegistry.getClassForAlias("EntityTarget"),
                "the EntityTarget alias must resolve to the target-change event");
    }

    @Test
    void entityHealAliasResolves() {
        assertEquals(net.neoforged.neoforge.event.entity.living.LivingHealEvent.class,
                EventAliasRegistry.getClassForAlias("EntityHeal"),
                "the EntityHeal alias must resolve to the vanilla heal event");
    }

    @Test
    void itemTossAliasResolves() {
        assertEquals(net.neoforged.neoforge.event.entity.item.ItemTossEvent.class,
                EventAliasRegistry.getClassForAlias("ItemToss"),
                "the ItemToss alias must resolve to the vanilla toss event");
    }

    @Test
    void itemPickupAliasResolves() {
        assertEquals(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre.class,
                EventAliasRegistry.getClassForAlias("ItemPickup"),
                "the ItemPickup alias must resolve to the vanilla pickup event");
    }

    @Test
    void explosionAliasResolves() {
        assertEquals(net.neoforged.neoforge.event.level.ExplosionEvent.Start.class,
                EventAliasRegistry.getClassForAlias("Explosion"),
                "the Explosion alias must resolve to the vanilla explosion event");
    }

    @Test
    void projectileImpactAliasResolves() {
        assertEquals(net.neoforged.neoforge.event.entity.ProjectileImpactEvent.class,
                EventAliasRegistry.getClassForAlias("ProjectileImpact"),
                "the ProjectileImpact alias must resolve to the vanilla impact event");
    }

    @Test
    void mobSpawnCheckAliasResolves() {
        assertEquals(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent.class,
                EventAliasRegistry.getClassForAlias("MobSpawnCheck"),
                "the MobSpawnCheck alias must resolve to the vanilla finalize-spawn event");
    }
}
