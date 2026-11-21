package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.EntityDrop;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.interaction.ServerLevels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.function.Function;

/** Server-side loot dispatch for custom entity types ({@code drops(...)} in Lua). */
public final class EntityLootDispatcher {
    private final IContentService contentService;
    private final Function<String, ResourceLocation> locationParser;

    public EntityLootDispatcher(IContentService contentService,
                                Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.locationParser = locationParser;
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity living = event.getEntity();
        if (!EntityBuilderLookup.isServer(living)) {
            return;
        }
        // Explicit loot replaces vanilla drops; unloaded/no-level means no dispatch.
        ServerLevels.run(ServerLevels.asServer(living.level()), level -> dispatch(event, living, level));
    }

    private void dispatch(LivingDropsEvent event, LivingEntity living, ServerLevel level) {
        IEntityBuilder builder = EntityBuilderLookup.find(contentService, locationParser, living);
        if (builder == null || builder.getDrops().isEmpty()) {
            return;
        }
        event.setCanceled(true);
        for (EntityDrop drop : builder.getDrops()) {
            spawnDrop(level, living, drop, builder.getId());
        }
    }

    private static void spawnDrop(ServerLevel level, LivingEntity living, EntityDrop drop, String builderId) {
        ResourceLocation itemId = drop.itemId().contains(":")
                ? ResourceLocation.tryParse(drop.itemId())
                : ResourceLocation.fromNamespaceAndPath("minecraft", drop.itemId());
        if (itemId == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Entity drop skipped for " + builderId + ": bad item id '" + drop.itemId() + "'");
            return;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Entity drop skipped for " + builderId + ": unknown item '" + drop.itemId() + "'");
            return;
        }
        int range = Math.max(0, drop.maxCount() - drop.minCount());
        int count = drop.minCount() + (range == 0 ? 0 : living.getRandom().nextInt(range + 1));
        ItemEntity dropEntity = new ItemEntity(level,
                living.getX(), living.getY(), living.getZ(), new ItemStack(item, count));
        level.addFreshEntity(dropEntity);
    }
}
