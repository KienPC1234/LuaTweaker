package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.MobEffectDefinition;
import com.luatweaker.api.content.PotionDefinition;
import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.content.EffectRegistry;
import com.luatweaker.core.service.LuaServiceRegistry;
import dev.architectury.registry.registries.Registrar;
import dev.architectury.registry.registries.RegistrarManager;
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
import org.jetbrains.annotations.NotNull;

public final class MobEffectRegistrar {

    private static final String TEXTURES_PREFIX = "textures/";
    private static final String MOB_EFFECT_TEXTURE_DIR = "textures/mob_effect/";
    private static final String PNG_EXT = ".png";

    public void registerMobEffects() {
        Registrar<MobEffect> registrar = RegistrarManager.get("luatweaker").get(Registries.MOB_EFFECT);
        EffectRegistry.getMobEffects().forEach((name, definition) -> {
            ResourceLocation id = requireLocation(name);
            registrar.register(id, () -> new CustomMobEffect(id, definition));
        });
    }

    public void registerPotions() {
        Registrar<Potion> registrar = RegistrarManager.get("luatweaker").get(Registries.POTION);
        EffectRegistry.getPotions().forEach((name, definition) -> {
            ResourceLocation id = requireLocation(name);
            MobEffectInstance[] instances = toInstances(name, definition);
            registrar.register(id, () -> new Potion(instances));
        });
    }

    public static @NotNull ResourceLocation resolveTextureLocation(String path, ResourceLocation effectId) {
        if (path == null || path.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath(effectId.getNamespace(),
                    MOB_EFFECT_TEXTURE_DIR + effectId.getPath() + PNG_EXT);
        }
        String clean = path.startsWith(TEXTURES_PREFIX) ? path.substring(TEXTURES_PREFIX.length()) : path;
        int colon = clean.indexOf(':');
        String namespace = colon >= 0 ? clean.substring(0, colon) : effectId.getNamespace();
        String file = colon >= 0 ? clean.substring(colon + 1) : clean;
        if (!file.endsWith(PNG_EXT)) {
            file += PNG_EXT;
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, file);
    }

    private static ResourceLocation requireLocation(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            throw new IllegalArgumentException("Invalid effect/potion id '" + name + "'");
        }
        return id;
    }

    private static MobEffectInstance[] toInstances(String potionName, PotionDefinition def) {
        return def.effects().stream()
                .map(spec -> toInstance(potionName, spec))
                .toArray(MobEffectInstance[]::new);
    }

    private static MobEffectInstance toInstance(String potionName, PotionDefinition.PotionEffectSpec spec) {
        ResourceLocation effectId = ResourceLocation.tryParse(spec.effectId());
        if (effectId == null) {
            throw new IllegalArgumentException("Potion '" + potionName
                    + "' references an invalid effect id '" + spec.effectId() + "'");
        }
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElseThrow(
                () -> new IllegalArgumentException("Potion '" + potionName
                        + "' references unregistered effect '" + effectId + "'"));
        return new MobEffectInstance(holder, spec.durationTicks(), spec.amplifier());
    }

    private static void postEffectEvent(String eventName, ResourceLocation id, LivingEntity entity, int amplifier) {
        Object rawEvents = LuaServiceRegistry.get("Events");
        if (!(rawEvents instanceof IEventService events)) {
            return;
        }
        ILuaEngine engine = com.luatweaker.common.LuaTweakerCommon.getActiveEngine();
        if (engine == null) {
            return;
        }
        var wrapper = new com.luatweaker.common.entity.MinecraftEntityWrapper(entity);
        ILuaTable payload = engine.createTable();
        payload.rawset("entity", engine.wrapDynamicJavaObject(wrapper));
        payload.rawset("Entity", payload.rawget("entity"));
        payload.rawset("effect", engine.wrapString(id.toString()));
        payload.rawset("Effect", payload.rawget("effect"));
        payload.rawset("amplifier", engine.wrapNumber(amplifier));
        payload.rawset("Amplifier", payload.rawget("amplifier"));
        events.post(eventName, payload);
    }

    /** Feature-rich custom effect: attributes and tick event dispatch. */
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
