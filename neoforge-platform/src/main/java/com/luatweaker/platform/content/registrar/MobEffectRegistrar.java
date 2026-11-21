package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.MobEffectDefinition;
import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.content.EffectRegistry;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.client.effect.CustomMobEffectClientExtensions;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.common.EffectCure;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Creates the real {@code MobEffect}/{@code Potion} registry entries from the Lua-declared
 * {@link EffectRegistry} definitions. Runs during the NeoForge registry events, before the
 * registries freeze.
 */
public final class MobEffectRegistrar {

    private static final String TEXTURES_PREFIX = "textures/";
    private static final String MOB_EFFECT_TEXTURE_DIR = "textures/mob_effect/";
    private static final String PNG_EXT = ".png";

    public void registerMobEffects(@NotNull RegisterEvent event) {
        EffectRegistry.getMobEffects().forEach((name, definition) -> {
            ResourceLocation id = requireLocation(name);
            event.register(Registries.MOB_EFFECT, id, () -> new CustomMobEffect(id, definition));
        });
    }

    public void registerPotions(@NotNull RegisterEvent event) {
        EffectRegistry.getPotions().forEach((name, definition) -> {
            ResourceLocation id = requireLocation(name);
            MobEffectInstance[] instances = toInstances(name, definition);
            event.register(Registries.POTION, id, () -> new Potion(instances));
        });
    }

    public static @NotNull ResourceLocation resolveTextureLocation(String path, ResourceLocation effectId) {
        if (path == null || path.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath(effectId.getNamespace(),
                    MOB_EFFECT_TEXTURE_DIR + effectId.getPath() + PNG_EXT);
        }
        String normalized = path.replace('\\', '/');
        int colon = normalized.indexOf(':');
        String ns = colon > 0 ? normalized.substring(0, colon) : effectId.getNamespace();
        String p = colon > 0 ? normalized.substring(colon + 1) : normalized;
        String dir = resolveDirectoryPath(p);
        String file = dir.endsWith(PNG_EXT) ? dir : dir + PNG_EXT;
        return ResourceLocation.fromNamespaceAndPath(ns, file);
    }

    private static String resolveDirectoryPath(String path) {
        if (path.contains("/")) {
            return path.startsWith(TEXTURES_PREFIX) ? path : TEXTURES_PREFIX + path;
        }
        return MOB_EFFECT_TEXTURE_DIR + path;
    }

    private static MobEffectInstance[] toInstances(
            String potionId, com.luatweaker.api.content.PotionDefinition definition) {
        return definition.effects().stream()
                .map(spec -> new MobEffectInstance(
                        resolveRequiredEffect(potionId, spec.effectId()),
                        spec.durationTicks(), spec.amplifier()))
                .toArray(MobEffectInstance[]::new);
    }

    private static Holder<MobEffect> resolveRequiredEffect(String potionId, String effectId) {
        Holder<MobEffect> holder = resolveEffect(effectId);
        if (holder == null) {
            throw new IllegalStateException("Potion '" + potionId
                    + "' references unknown mob effect '" + effectId + "'");
        }
        return holder;
    }

    private static Holder<MobEffect> resolveEffect(String effectId) {
        ResourceLocation location = ResourceLocation.tryParse(effectId);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getHolder(location).orElse(null);
    }

    private static ResourceLocation requireLocation(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid effect resource id: '" + id + "'");
        }
        return location;
    }

    static void postEffectEvent(String eventName, ResourceLocation id,
                                LivingEntity entity, int amplifier) {
        Object service = LuaServiceRegistry.get("Events");
        if (!(service instanceof IEventService events)) {
            return;
        }
        ILuaEngine engine = events.getEngine();
        ILuaTable payload = engine.createTable();
        var wrapper = new com.luatweaker.platform.entity.NeoForgeEntityWrapper(entity);
        payload.rawset("entity", engine.wrapDynamicJavaObject(wrapper));
        payload.rawset("Entity", payload.rawget("entity"));
        payload.rawset("effect", engine.wrapString(id.toString()));
        payload.rawset("Effect", payload.rawget("effect"));
        payload.rawset("amplifier", engine.wrapNumber(amplifier));
        payload.rawset("Amplifier", payload.rawget("amplifier"));
        events.post(eventName, payload);
    }

    /** Feature-rich custom effect: attributes, client texture, cures, and tick event dispatch. */
    private static final class CustomMobEffect extends MobEffect {

        private final ResourceLocation id;
        private final MobEffectDefinition definition;

        CustomMobEffect(ResourceLocation id, MobEffectDefinition definition) {
            super(MobEffectCategory.valueOf(definition.category()), definition.color());
            this.id = id;
            this.definition = definition;
            int index = 0;
            for (MobEffectDefinition.AttributeModifierSpec modifier : definition.attributeModifiers()) {
                addAttributeModifier(resolveAttribute(id, modifier.attributeId()),
                        ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                                id.getPath() + "_" + index++),
                        modifier.amount(),
                        AttributeModifier.Operation.valueOf(modifier.operation()));
            }
        }

        @Override
        public boolean isInstantenous() {
            return definition.instantaneous();
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            int interval = definition.tickInterval();
            return interval > 0 && duration % interval == 0;
        }

        @Override
        public boolean applyEffectTick(@NotNull LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide() && Platform.isInitialized()) {
                postEffectEvent("MobEffectTick", id, entity, amplifier);
            }
            return true;
        }

        @Override
        public void onEffectStarted(@NotNull LivingEntity entity, int amplifier) {
            if (!entity.level().isClientSide() && Platform.isInitialized()) {
                postEffectEvent("MobEffectStarted", id, entity, amplifier);
            }
        }

        @Override
        public void fillEffectCures(@NotNull Set<EffectCure> cures, @NotNull MobEffectInstance instance) {
            if (definition.curable()) {
                super.fillEffectCures(cures, instance);
            } else {
                cures.clear();
            }
        }

        @Override
        public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
            if (definition.texture() != null && !definition.texture().isBlank()) {
                ResourceLocation tex = resolveTextureLocation(definition.texture(), id);
                consumer.accept(new CustomMobEffectClientExtensions(tex));
            }
        }

        private static Holder<Attribute> resolveAttribute(ResourceLocation effectId, String raw) {
            ResourceLocation location = ResourceLocation.tryParse(raw);
            if (location == null) {
                throw new IllegalStateException("Effect '" + effectId
                        + "' has an invalid attribute id '" + raw + "'");
            }
            return BuiltInRegistries.ATTRIBUTE.getHolder(location).orElseThrow(
                    () -> new IllegalStateException("Effect '" + effectId
                            + "' references unknown attribute '" + raw + "'"));
        }
    }
}
