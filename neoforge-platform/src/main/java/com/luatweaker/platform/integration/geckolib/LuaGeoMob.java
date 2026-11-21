package com.luatweaker.platform.integration.geckolib;

import com.luatweaker.integration.geo.GeoModelSpec;
import com.luatweaker.platform.entity.LuaCustomMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Generic custom entity with full GeckoLib animation support.
 */
public class LuaGeoMob extends LuaCustomMob implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final GeoModelSpec geoSpec;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public LuaGeoMob(EntityType<?> type, Level level, @NotNull GeoModelSpec geoSpec) {
        super((EntityType<? extends PathfinderMob>) (EntityType) type, level);
        this.geoSpec = geoSpec;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> {
            String def = geoSpec.defaultAnimation();
            if (def != null && !def.isBlank()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop(def));
            }
            return PlayState.STOP;
        }).receiveTriggeredAnimations());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @NotNull
    public GeoModelSpec getGeoSpec() {
        return this.geoSpec;
    }
}
