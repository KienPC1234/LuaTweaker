package com.luatweaker.platform.event;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps clean string aliases (Roblox style) to exact NeoForge Event classes.
 * Ensures WORA (Write Once Run Anywhere) for mod scripts.
 */
public final class EventAliasRegistry {

    private EventAliasRegistry() {
    }
    private static final Map<String, Class<? extends Event>> ALIAS_MAP = new ConcurrentHashMap<>();

    static {
        // Combat
        ALIAS_MAP.put("EntityHurt", LivingDamageEvent.Pre.class);
        ALIAS_MAP.put("EntityDeath", LivingDeathEvent.class);
        ALIAS_MAP.put("EntityTarget", net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.class);
        ALIAS_MAP.put("LivingTarget", net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.class);
        ALIAS_MAP.put("EntityHeal", net.neoforged.neoforge.event.entity.living.LivingHealEvent.class);
        ALIAS_MAP.put("LivingHeal", net.neoforged.neoforge.event.entity.living.LivingHealEvent.class);

        // Interaction & Items
        ALIAS_MAP.put("PlayerInteractBlock", PlayerInteractEvent.RightClickBlock.class);
        ALIAS_MAP.put("BlockInteract", PlayerInteractEvent.RightClickBlock.class);
        ALIAS_MAP.put("PlayerInteractEntity", PlayerInteractEvent.EntityInteract.class);
        ALIAS_MAP.put("ItemToss", net.neoforged.neoforge.event.entity.item.ItemTossEvent.class);
        ALIAS_MAP.put("ItemPickup", net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre.class);
        ALIAS_MAP.put("ItemPickupPost", net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post.class);

        // Drops
        ALIAS_MAP.put("LivingDrops", net.neoforged.neoforge.event.entity.living.LivingDropsEvent.class);
        ALIAS_MAP.put("BlockDrops", net.neoforged.neoforge.event.level.BlockDropsEvent.class);

        // Block
        ALIAS_MAP.put("BlockBreak", BlockEvent.BreakEvent.class);
        ALIAS_MAP.put("BlockPlace", BlockEvent.EntityPlaceEvent.class);
        ALIAS_MAP.put("BlockBreakSpeed", PlayerEvent.BreakSpeed.class);

        // Player
        ALIAS_MAP.put("PlayerJoin", PlayerEvent.PlayerLoggedInEvent.class);
        ALIAS_MAP.put("PlayerLeave", PlayerEvent.PlayerLoggedOutEvent.class);
        ALIAS_MAP.put("PlayerRespawn", PlayerEvent.PlayerRespawnEvent.class);
        ALIAS_MAP.put("PlayerAdvancement", net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent.class);
        ALIAS_MAP.put("ContainerOpen", net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open.class);
        ALIAS_MAP.put("ContainerClose", net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close.class);

        // World & Entity
        ALIAS_MAP.put("EntitySpawn", EntityJoinLevelEvent.class);
        ALIAS_MAP.put("MobSpawnCheck", net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent.class);
        ALIAS_MAP.put("EntityChangeDimension", net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent.class);
        ALIAS_MAP.put("Explosion", net.neoforged.neoforge.event.level.ExplosionEvent.Start.class);
        ALIAS_MAP.put("ExplosionDetonate", net.neoforged.neoforge.event.level.ExplosionEvent.Detonate.class);
        ALIAS_MAP.put("ProjectileImpact", net.neoforged.neoforge.event.entity.ProjectileImpactEvent.class);

        // Tick
        ALIAS_MAP.put("ServerTick", ServerTickEvent.Post.class);
        ALIAS_MAP.put("PlayerTick", net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class);
    }

    public static Class<? extends Event> getClassForAlias(String alias) {
        return ALIAS_MAP.get(alias);
    }
    
    public static Map<String, Class<? extends Event>> getAllAliases() {
        return java.util.Collections.unmodifiableMap(ALIAS_MAP);
    }
}
