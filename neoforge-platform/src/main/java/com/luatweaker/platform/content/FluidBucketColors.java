package com.luatweaker.platform.content;

import com.luatweaker.api.content.IFluidBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;

import java.util.Objects;

/** Registers fluid-bucket item color handlers (bucket + "_fluid"-stripped alias). */
final class FluidBucketColors {

    private FluidBucketColors() {
    }

    static void register(NeoForgeContentRegistry registry, RegisterColorHandlersEvent.Item event) {
        for (IFluidBuilder builder : registry.getContentService().getRegisteredFluids()) {
            ResourceLocation bucketId = NeoForgeContentRegistry.parseLocation(builder.getId() + "_bucket");
            Item bucket = registry.getCreatedItems().get(bucketId);
            registerColor(event, bucket);
            ResourceLocation altBucketId =
                    NeoForgeContentRegistry.parseLocation(builder.getId().replace("_fluid", "") + "_bucket");
            Item altBucket = registry.getCreatedItems().get(altBucketId);
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

