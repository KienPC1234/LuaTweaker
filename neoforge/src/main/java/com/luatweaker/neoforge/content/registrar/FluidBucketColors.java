package com.luatweaker.neoforge.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IFluidBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class FluidBucketColors {

    private FluidBucketColors() {
    }

    public static void register(IContentService contentService,
                               Map<ResourceLocation, Item> createdItems,
                               Function<String, ResourceLocation> locationParser,
                               RegisterColorHandlersEvent.Item event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            ResourceLocation bucketId = locationParser.apply(builder.getId() + "_bucket");
            Item bucket = createdItems.get(bucketId);
            registerColor(event, bucket);
            ResourceLocation altBucketId = locationParser.apply(builder.getId().replace("_fluid", "") + "_bucket");
            Item altBucket = createdItems.get(altBucketId);
            if (!Objects.equals(altBucket, bucket)) {
                registerColor(event, altBucket);
            }
        }
    }

    private static void registerColor(RegisterColorHandlersEvent.Item event, @Nullable Item item) {
        if (item != null) {
            event.register(new DynamicFluidContainerModel.Colors(), item);
        }
    }
}
