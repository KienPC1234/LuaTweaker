package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Declarative definition of a custom {@code Potion}. The potion registry entry is created
 * during the NeoForge registry event; scripts obtain a potion item through the vanilla
 * {@code minecraft:potion} item with a {@code potion_contents} component (for example via
 * {@code World:ExecuteCommand}).
 */
@LuaDoc(description = "Definition of a custom potion (one or more status effects).")
public record PotionDefinition(@NotNull String id, @NotNull List<PotionEffectSpec> effects) {

    public PotionDefinition {
        java.util.Objects.requireNonNull(id, "id");
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    /** One effect instance inside a potion. */
    public record PotionEffectSpec(@NotNull String effectId, int durationTicks, int amplifier) {
        public PotionEffectSpec {
            java.util.Objects.requireNonNull(effectId, "effectId");
        }
    }
}
