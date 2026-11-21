package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** Resolves the Lua entity builder that owns a live entity (server-side only). */
final class EntityBuilderLookup {

    private EntityBuilderLookup() {
    }

    @Nullable
    static IEntityBuilder find(@NotNull IContentService contentService,
                               @NotNull Function<String, ResourceLocation> locationParser,
                               @Nullable Entity entity) {
        if (entity == null || entity.getType() == null) {
            return null;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (key == null) {
            return null;
        }
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            try {
                if (key.equals(locationParser.apply(builder.getId()))) {
                    return builder;
                }
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Entity hook dispatch: bad builder id '" + builder.getId() + "': " + e.getMessage());
            }
        }
        return null;
    }

    static boolean isServer(@Nullable Entity entity) {
        return entity != null && entity.level() != null && !entity.level().isClientSide();
    }
}
