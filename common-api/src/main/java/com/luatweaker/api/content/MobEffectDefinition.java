package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Declarative definition of a custom {@code MobEffect}.
 *
 * @param id                 resource id ({@code namespace:path})
 * @param color              RGB color shown by the effect icon (0xRRGGBB)
 * @param category           {@code BENEFICIAL}, {@code HARMFUL} or {@code NEUTRAL}
 * @param attributeModifiers modifiers applied while the effect is active
 * @param texture            optional custom texture path
 * @param displayName        optional display name
 * @param instantaneous      whether the effect is instantaneous
 * @param tickInterval       ticks between periodic effect ticks (0 = passive marker)
 * @param curable            whether curative items (milk) cure this effect
 */
@LuaDoc(description = "Definition of a custom status effect (marker + optional attribute modifiers).")
public record MobEffectDefinition(@NotNull String id, int color, @NotNull String category,
                                  @NotNull List<AttributeModifierSpec> attributeModifiers,
                                  @Nullable String texture,
                                  @Nullable String displayName,
                                  boolean instantaneous,
                                  int tickInterval,
                                  boolean curable) {

    public MobEffectDefinition {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(category, "category");
        attributeModifiers = attributeModifiers == null ? List.of() : List.copyOf(attributeModifiers);
    }

    public MobEffectDefinition(@NotNull String id, int color, @NotNull String category,
                               @NotNull List<AttributeModifierSpec> attributeModifiers) {
        this(id, color, category, attributeModifiers, null, null, false, 0, true);
    }

    /** One attribute modifier attached to a custom effect. */
    public record AttributeModifierSpec(@NotNull String attributeId, double amount,
                                        @NotNull String operation) {
        public AttributeModifierSpec {
            java.util.Objects.requireNonNull(attributeId, "attributeId");
            java.util.Objects.requireNonNull(operation, "operation");
        }
    }
}
