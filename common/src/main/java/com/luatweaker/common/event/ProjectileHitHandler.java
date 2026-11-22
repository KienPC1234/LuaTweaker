package com.luatweaker.common.event;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.common.entity.MinecraftEntityWrapper;
import com.luatweaker.common.entity.MinecraftPlayerWrapper;
import com.luatweaker.common.util.CallbackFirewall;
import com.luatweaker.core.service.LuaServiceRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

public final class ProjectileHitHandler {

    private ProjectileHitHandler() {
    }

    public static void handleProjectileHit(Projectile projectile, Entity hitEntity) {
        Object rawContent = LuaServiceRegistry.get("Content");
        if (!(rawContent instanceof IContentService contentService) || !(projectile.getOwner() instanceof Player player)) {
            return;
        }
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            if (projectile.getTags().contains("lt_proj_" + builder.getId()) && builder.getOnHitEntityHandler() != null) {
                CallbackFirewall.run(
                        () -> builder.getOnHitEntityHandler().accept(
                                new MinecraftEntityWrapper(hitEntity),
                                new MinecraftPlayerWrapper(player)),
                        LogStage.SYSTEM,
                        () -> "Failed projectile onHitEntity handler for " + builder.getId());
                break;
            }
        }
    }
}
