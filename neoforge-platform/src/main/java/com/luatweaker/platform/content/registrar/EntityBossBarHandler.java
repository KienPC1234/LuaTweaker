package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Server-side boss-bar tracking for Lua mobs ({@code bossBar(...)} in the entity builder). */
public final class EntityBossBarHandler {
    private static final double BOSS_BAR_RANGE = 32.0;

    private final IContentService contentService;
    private final Map<UUID, ServerBossEvent> activeBossBars = new ConcurrentHashMap<>();
    private final Function<String, ResourceLocation> locationParser;

    public EntityBossBarHandler(IContentService contentService, Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.locationParser = locationParser;
    }

    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() != null) {
            removeBossBar(event.getEntity().getUUID());
        }
    }

    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) {
            return;
        }
        if (!entity.isAlive() || entity.isRemoved()) {
            removeBossBar(entity.getUUID());
            return;
        }
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityId == null) {
            return;
        }
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            if (builder.hasBossBar() && locationParser.apply(builder.getId()).equals(entityId)) {
                updateBossBar(entity, builder);
            }
        }
    }

    private void updateBossBar(LivingEntity entity, IEntityBuilder builder) {
        ServerBossEvent bossEvent = activeBossBars.computeIfAbsent(entity.getUUID(), uuid -> {
            Component name = Component.literal(
                    builder.getBossBarTitle() != null ? builder.getBossBarTitle() : builder.getId());
            return new ServerBossEvent(name, parseBossColor(builder.getBossBarColor()),
                    parseBossOverlay(builder.getBossBarOverlay()));
        });
        bossEvent.setProgress(Math.max(0.0f, Math.min(1.0f, entity.getHealth() / entity.getMaxHealth())));
        syncPlayers(bossEvent, entity);
    }

    private static void syncPlayers(ServerBossEvent bossEvent, LivingEntity entity) {
        var area = entity.getBoundingBox().inflate(BOSS_BAR_RANGE);
        Set<ServerPlayer> nearbyPlayers = new HashSet<>(
                entity.level().getEntitiesOfClass(ServerPlayer.class, area));
        for (ServerPlayer player : nearbyPlayers) {
            bossEvent.addPlayer(player);
        }
        for (ServerPlayer player : new ArrayList<>(bossEvent.getPlayers())) {
            if (!nearbyPlayers.contains(player)) {
                bossEvent.removePlayer(player);
            }
        }
    }

    private void removeBossBar(UUID uuid) {
        ServerBossEvent bossEvent = activeBossBars.remove(uuid);
        if (bossEvent != null) {
            bossEvent.removeAllPlayers();
            bossEvent.setVisible(false);
        }
    }

    private static BossEvent.BossBarColor parseBossColor(String color) {
        if (color == null) {
            return BossEvent.BossBarColor.RED;
        }
        return switch (color.toUpperCase(Locale.ROOT)) {
            case "PINK" -> BossEvent.BossBarColor.PINK;
            case "BLUE" -> BossEvent.BossBarColor.BLUE;
            case "GREEN" -> BossEvent.BossBarColor.GREEN;
            case "YELLOW" -> BossEvent.BossBarColor.YELLOW;
            case "PURPLE" -> BossEvent.BossBarColor.PURPLE;
            case "WHITE" -> BossEvent.BossBarColor.WHITE;
            default -> BossEvent.BossBarColor.RED;
        };
    }

    private static BossEvent.BossBarOverlay parseBossOverlay(String overlay) {
        if (overlay == null) {
            return BossEvent.BossBarOverlay.PROGRESS;
        }
        return switch (overlay.toUpperCase(Locale.ROOT)) {
            case "NOTCHED_6" -> BossEvent.BossBarOverlay.NOTCHED_6;
            case "NOTCHED_10" -> BossEvent.BossBarOverlay.NOTCHED_10;
            case "NOTCHED_12" -> BossEvent.BossBarOverlay.NOTCHED_12;
            case "NOTCHED_20" -> BossEvent.BossBarOverlay.NOTCHED_20;
            default -> BossEvent.BossBarOverlay.PROGRESS;
        };
    }
}
