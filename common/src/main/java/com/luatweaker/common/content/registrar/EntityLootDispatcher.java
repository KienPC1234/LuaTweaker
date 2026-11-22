package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.EntityDrop;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.interaction.ServerLevels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

    public boolean handleLivingDrops(LivingEntity living) {
        if (!EntityBuilderLookup.isServer(living)) {
            return false;
        }
        ServerLevel level = ServerLevels.asServer(living.level());
        if (level == null) {
            return false;
        }
        IEntityBuilder builder = EntityBuilderLookup.find(contentService, locationParser, living);
        if (builder == null || builder.getDrops().isEmpty()) {
            return false;
        }
        for (EntityDrop drop : builder.getDrops()) {
            spawnDrop(level, living, drop, builder.getId());
        }
        return true;
    }

    private void spawnDrop(ServerLevel level, LivingEntity living, EntityDrop drop, String entityId) {
        ResourceLocation itemId = locationParser.apply(drop.itemId());
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Unknown drop item '" + itemId + "' for custom entity " + entityId);
            return;
        }
        int min = drop.minCount();
        int max = drop.maxCount();
        int count = min + (max > min ? level.random.nextInt(max - min + 1) : 0);
        if (count <= 0) {
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        ItemEntity entity = new ItemEntity(level, living.getX(), living.getY(), living.getZ(), stack);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }
}
