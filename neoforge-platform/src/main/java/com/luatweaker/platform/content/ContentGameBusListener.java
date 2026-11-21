package com.luatweaker.platform.content;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.platform.content.registrar.ItemRegistrar;
import com.luatweaker.platform.entity.NeoForgeEntityWrapper;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import com.luatweaker.platform.util.CallbackFirewall;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Game-bus hooks owned by content registration (projectile scripts and left-click swings). */
public final class ContentGameBusListener {
    private final IContentService contentService;

    public ContentGameBusListener(IContentService contentService) {
        this.contentService = contentService;
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hitResult) {
            dispatchImpact(event.getProjectile(), hitResult);
        }
    }

    private void dispatchImpact(Projectile projectile, EntityHitResult hitResult) {
        IItemBuilder owner = findHitBuilder(projectile);
        if (owner == null || owner.getOnHitEntityHandler() == null
                || !(projectile.getOwner() instanceof Player player)) {
            return;
        }
        CallbackFirewall.run(() -> owner.getOnHitEntityHandler().accept(
                        new NeoForgeEntityWrapper(hitResult.getEntity()),
                        new NeoForgePlayerWrapper(player)),
                LogStage.SYSTEM,
                () -> "Failed projectile onHitEntity handler for " + owner.getId());
    }

    @Nullable
    private IItemBuilder findHitBuilder(Projectile projectile) {
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            if (projectile.getTags().contains("lt_proj_" + builder.getId())) {
                return builder;
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemRegistrar.handleSwing(event.getEntity(), event.getItemStack());
    }

    @SubscribeEvent
    public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        ItemRegistrar.handleSwing(event.getEntity(), event.getItemStack());
    }
}

