package com.luatweaker.common.content;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.LuaTweakerCommon;
import dev.architectury.registry.item.ItemPropertiesRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;

/** Registers the vanilla bow "pulling"/"pull" item properties via Architectury {@link ItemPropertiesRegistry}. */
public final class BowItemProperties {
    private static final int DEFAULT_PULL_TICKS = 20;

    private BowItemProperties() {
    }

    public static void register(IContentService contentService, Map<ResourceLocation, Item> createdItems) {
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            String animation = builder.getUseAnimation();
            if (!"BOW".equalsIgnoreCase(animation) && !"CROSSBOW".equalsIgnoreCase(animation)) {
                continue;
            }
            ResourceLocation id = LuaTweakerCommon.parseLocation(builder.getId());
            Item item = createdItems.get(id);
            if (item != null) {
                registerProperties(item, builder, id);
            }
        }
    }

    private static void registerProperties(Item item, IItemBuilder builder, ResourceLocation id) {
        ItemPropertiesRegistry.register(item, ResourceLocation.withDefaultNamespace("pulling"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem()
                        && Objects.equals(entity.getUseItem(), stack) ? 1.0F : 0.0F);
        ItemPropertiesRegistry.register(item, ResourceLocation.withDefaultNamespace("pull"),
                (stack, level, entity, seed) -> pullProgress(stack, entity, builder));
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered bow pulling item properties for " + id);
    }

    private static float pullProgress(ItemStack stack, LivingEntity entity, IItemBuilder builder) {
        if (entity == null || !Objects.equals(entity.getUseItem(), stack)) {
            return 0.0F;
        }
        int duration = builder.getUseDuration() > 0 ? builder.getUseDuration() : DEFAULT_PULL_TICKS;
        float remaining = stack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
        return remaining / duration;
    }
}
