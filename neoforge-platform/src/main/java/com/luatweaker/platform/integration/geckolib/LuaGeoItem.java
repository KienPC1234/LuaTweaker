package com.luatweaker.platform.integration.geckolib;

import com.luatweaker.integration.geo.GeoModelSpec;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.platform.content.registrar.ItemRegistrar;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Custom item with full GeckoLib 3D animated model support.
 */
public class LuaGeoItem extends ItemRegistrar.CustomGenericItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final GeoModelSpec geoSpec;

    public LuaGeoItem(Properties props, IItemBuilder builder, @NotNull GeoModelSpec geoSpec) {
        super(props, builder);
        this.geoSpec = geoSpec;
        software.bernie.geckolib.animatable.SingletonGeoAnimatable.registerSyncedAnimatable(this);
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

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoItemRenderer<>(new LuaDynamicGeoModel<>(geoSpec, "item"));
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoItemRenderer<>(new LuaDynamicGeoModel<>(geoSpec, "item"));
                }
                return this.renderer;
            }
        });
    }

    @NotNull
    public GeoModelSpec getGeoSpec() {
        return this.geoSpec;
    }
}
