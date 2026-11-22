package com.luatweaker.neoforge.event;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps clean string aliases (Roblox style) to exact NeoForge Event classes.
 */
public final class EventAliasRegistry {

    private static final Map<String, Class<? extends Event>> ALIAS_MAP = new ConcurrentHashMap<>();

    static {
        registerCombatEvents();
        registerInteractionAndItemEvents();
        registerBlockAndPlayerEvents();
        registerWorldAndTickEvents();
    }

    private EventAliasRegistry() {
    }

    private static void registerCombatEvents() {
        ALIAS_MAP.put("EntityHurt", LivingDamageEvent.Pre.class);
        ALIAS_MAP.put("EntityDeath", LivingDeathEvent.class);
        ALIAS_MAP.put("EntityTarget", LivingChangeTargetEvent.class);
        ALIAS_MAP.put("LivingTarget", LivingChangeTargetEvent.class);
        ALIAS_MAP.put("EntityHeal", LivingHealEvent.class);
        ALIAS_MAP.put("LivingHeal", LivingHealEvent.class);
        ALIAS_MAP.put("LivingDrops", LivingDropsEvent.class);
    }

    private static void registerInteractionAndItemEvents() {
        ALIAS_MAP.put("PlayerInteractBlock", PlayerInteractEvent.RightClickBlock.class);
        ALIAS_MAP.put("BlockInteract", PlayerInteractEvent.RightClickBlock.class);
        ALIAS_MAP.put("PlayerInteractEntity", PlayerInteractEvent.EntityInteract.class);
        ALIAS_MAP.put("ItemToss", ItemTossEvent.class);
        ALIAS_MAP.put("ItemPickup", ItemEntityPickupEvent.Pre.class);
        ALIAS_MAP.put("ItemPickupPost", ItemEntityPickupEvent.Post.class);
    }

    private static void registerBlockAndPlayerEvents() {
        ALIAS_MAP.put("BlockDrops", BlockDropsEvent.class);
        ALIAS_MAP.put("BlockBreak", BlockEvent.BreakEvent.class);
        ALIAS_MAP.put("BlockPlace", BlockEvent.EntityPlaceEvent.class);
        ALIAS_MAP.put("BlockBreakSpeed", PlayerEvent.BreakSpeed.class);
        ALIAS_MAP.put("PlayerJoin", PlayerEvent.PlayerLoggedInEvent.class);
        ALIAS_MAP.put("PlayerLeave", PlayerEvent.PlayerLoggedOutEvent.class);
        ALIAS_MAP.put("PlayerRespawn", PlayerEvent.PlayerRespawnEvent.class);
        ALIAS_MAP.put("PlayerAdvancement", AdvancementEvent.AdvancementEarnEvent.class);
        ALIAS_MAP.put("ContainerOpen", PlayerContainerEvent.Open.class);
        ALIAS_MAP.put("ContainerClose", PlayerContainerEvent.Close.class);
    }

    private static void registerWorldAndTickEvents() {
        ALIAS_MAP.put("EntitySpawn", EntityJoinLevelEvent.class);
        ALIAS_MAP.put("MobSpawnCheck", FinalizeSpawnEvent.class);
        ALIAS_MAP.put("EntityChangeDimension", EntityTravelToDimensionEvent.class);
        ALIAS_MAP.put("Explosion", ExplosionEvent.Start.class);
        ALIAS_MAP.put("ExplosionDetonate", ExplosionEvent.Detonate.class);
        ALIAS_MAP.put("ProjectileImpact", ProjectileImpactEvent.class);
        ALIAS_MAP.put("ServerTick", ServerTickEvent.Post.class);
        ALIAS_MAP.put("PlayerTick", PlayerTickEvent.Post.class);
    }

    @Nullable
    public static Class<? extends Event> getClassForAlias(@NotNull String alias) {
        return ALIAS_MAP.get(alias);
    }

    @NotNull
    public static Map<String, Class<? extends Event>> getAllAliases() {
        return Collections.unmodifiableMap(ALIAS_MAP);
    }
}
