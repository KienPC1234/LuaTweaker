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
public class EventAliasRegistry {
    private static final Map<String, Class<? extends Event>> ALIAS_MAP = new ConcurrentHashMap<>();

    static {
        // Combat
        ALIAS_MAP.put("EntityHurt", LivingDamageEvent.Pre.class);
        ALIAS_MAP.put("EntityDeath", LivingDeathEvent.class);
        
        // Interaction
        ALIAS_MAP.put("PlayerInteractBlock", PlayerInteractEvent.RightClickBlock.class);
        ALIAS_MAP.put("PlayerInteractEntity", PlayerInteractEvent.EntityInteract.class);
        
        // Block
        ALIAS_MAP.put("BlockBreak", BlockEvent.BreakEvent.class);
        ALIAS_MAP.put("BlockPlace", BlockEvent.EntityPlaceEvent.class);
        
        // Player
        ALIAS_MAP.put("PlayerJoin", PlayerEvent.PlayerLoggedInEvent.class);
        ALIAS_MAP.put("PlayerLeave", PlayerEvent.PlayerLoggedOutEvent.class);
        
        // World/Entity
        ALIAS_MAP.put("EntitySpawn", EntityJoinLevelEvent.class);
        
        // Tick
        ALIAS_MAP.put("ServerTick", ServerTickEvent.Post.class);
    }

    public static Class<? extends Event> getClassForAlias(String alias) {
        return ALIAS_MAP.get(alias);
    }
    
    public static Map<String, Class<? extends Event>> getAllAliases() {
        return java.util.Collections.unmodifiableMap(ALIAS_MAP);
    }
}
