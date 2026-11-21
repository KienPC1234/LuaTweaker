package com.luatweaker.content;

import com.luatweaker.api.content.MobEffectDefinition;
import com.luatweaker.api.content.PotionDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of custom effect/potion definitions declared by startup scripts. The NeoForge
 * platform reads it during the registry events; there is no in-memory consumer that could
 * silently diverge from the real registry.
 */
public final class EffectRegistry {

    public static final Set<String> CATEGORIES = Set.of("BENEFICIAL", "HARMFUL", "NEUTRAL");
    public static final Set<String> OPERATIONS = Set.of(
            "ADD_VALUE", "ADD_MULTIPLIED_BASE", "ADD_MULTIPLIED_TOTAL");
    public static final int MIN_AMPLIFIER = 0;
    public static final int MAX_AMPLIFIER = 255;
    public static final int INFINITE_DURATION = -1;
    public static final int MAX_RGB = 0xFFFFFF;
    public static final String DEFAULT_NAMESPACE = "luatweaker";

    private static final String MOB_EFFECT_LABEL = "Mob effect";
    private static final String POTION_LABEL = "Potion";
    private static final Map<String, MobEffectDefinition> MOB_EFFECTS = new ConcurrentHashMap<>();
    private static final Map<String, PotionDefinition> POTIONS = new ConcurrentHashMap<>();

    private EffectRegistry() {
    }

    public static void registerMobEffect(@NotNull MobEffectDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Mob effect definition must not be null");
        }
        String id = normalizeId(definition.id(), MOB_EFFECT_LABEL);
        if (definition.color() < 0 || definition.color() > MAX_RGB) {
            throw new IllegalArgumentException(
                    MOB_EFFECT_LABEL + " " + id + " color must be between 0 and " + MAX_RGB);
        }
        String category = definition.category().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException(
                    MOB_EFFECT_LABEL + " " + id + " category must be one of " + CATEGORIES);
        }
        for (MobEffectDefinition.AttributeModifierSpec modifier : definition.attributeModifiers()) {
            validateModifier(id, modifier);
        }
        MOB_EFFECTS.put(id, new MobEffectDefinition(id, definition.color(), category,
                definition.attributeModifiers(), definition.texture(), definition.displayName(),
                definition.instantaneous(), definition.tickInterval(), definition.curable()));
    }

    public static void registerPotion(@NotNull PotionDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Potion definition must not be null");
        }
        String id = normalizeId(definition.id(), POTION_LABEL);
        if (definition.effects().isEmpty()) {
            throw new IllegalArgumentException(POTION_LABEL + " " + id + " needs at least one effect");
        }
        for (PotionDefinition.PotionEffectSpec effect : definition.effects()) {
            validatePotionEffect(id, effect);
        }
        POTIONS.put(id, new PotionDefinition(id, definition.effects()));
    }

    public static Map<String, MobEffectDefinition> getMobEffects() {
        return Map.copyOf(MOB_EFFECTS);
    }

    public static Map<String, PotionDefinition> getPotions() {
        return Map.copyOf(POTIONS);
    }

    /** Drops every definition; a reload rebuilds them from startup scripts. */
    public static void clear() {
        MOB_EFFECTS.clear();
        POTIONS.clear();
    }

    private static void validateModifier(String effectId,
                                         MobEffectDefinition.AttributeModifierSpec modifier) {
        if (modifier == null) {
            throw new IllegalArgumentException(MOB_EFFECT_LABEL + " " + effectId + " has a null modifier");
        }
        normalizeId(modifier.attributeId(), MOB_EFFECT_LABEL + " " + effectId + " attribute");
        if (!Double.isFinite(modifier.amount())) {
            throw new IllegalArgumentException(
                    MOB_EFFECT_LABEL + " " + effectId + " attribute amount must be finite");
        }
        String operation = modifier.operation().toUpperCase(Locale.ROOT);
        if (!OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException(
                    MOB_EFFECT_LABEL + " " + effectId + " operation must be one of " + OPERATIONS);
        }
    }

    private static void validatePotionEffect(String potionId,
                                             PotionDefinition.PotionEffectSpec effect) {
        if (effect == null) {
            throw new IllegalArgumentException(POTION_LABEL + " " + potionId + " has a null effect");
        }
        normalizeId(effect.effectId(), POTION_LABEL + " " + potionId + " effect");
        if (effect.durationTicks() < INFINITE_DURATION) {
            throw new IllegalArgumentException(
                    POTION_LABEL + " " + potionId + " duration must be -1 (infinite) or >= 0");
        }
        if (effect.amplifier() < MIN_AMPLIFIER || effect.amplifier() > MAX_AMPLIFIER) {
            throw new IllegalArgumentException(
                    POTION_LABEL + " " + potionId + " amplifier must be between "
                            + MIN_AMPLIFIER + " and " + MAX_AMPLIFIER);
        }
    }

    /** Canonical {@code namespace:path} form used by both registries. */
    public static @NotNull String normalizeId(@NotNull String raw, @NotNull String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + " id must not be blank");
        }
        String id = canonical(raw);
        if (!isValidId(id)) {
            throw new IllegalArgumentException(label + " id is not a valid resource id: '" + raw + "'");
        }
        return id;
    }

    private static String canonical(String raw) {
        return raw.contains(":") ? raw : DEFAULT_NAMESPACE + ":" + raw;
    }

    private static boolean isValidId(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            return false;
        }
        if (id.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        return id.substring(0, colon).matches("[a-z0-9_.-]+")
                && id.substring(colon + 1).matches("[a-z0-9_./-]+");
    }
}
