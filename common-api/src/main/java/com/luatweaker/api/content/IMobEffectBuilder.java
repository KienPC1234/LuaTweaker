package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import org.jetbrains.annotations.NotNull;

/**
 * Builder for a custom {@code MobEffect}. The effect is registered once during the NeoForge
 * registry event, so it must be declared from a startup script (not from a runtime reload).
 */
@LuaDoc(description = "Builder for a custom status effect. Call Register() after configuring it.")
public interface IMobEffectBuilder {

    @LuaDoc(description = "Sets the icon/particle color as 0xRRGGBB.",
            params = {"rgb: integer"}, returnType = LuaDocFragments.Builder.I_MOB_EFFECT_BUILDER)
    @NotNull IMobEffectBuilder color(int rgb);

    @LuaDoc(description = "Sets the category: BENEFICIAL, HARMFUL or NEUTRAL.",
            params = {"category: string"}, returnType = LuaDocFragments.Builder.I_MOB_EFFECT_BUILDER)
    @NotNull IMobEffectBuilder category(@NotNull String category);

    @LuaDoc(description = "Adds an attribute modifier applied while the effect is active. "
            + "operation is ADD_VALUE, ADD_MULTIPLIED_BASE or ADD_MULTIPLIED_TOTAL.",
            params = {"attributeId: string", "amount: number", "[operation: string]"},
            returnType = LuaDocFragments.Builder.I_MOB_EFFECT_BUILDER)
    @NotNull IMobEffectBuilder attribute(@NotNull String attributeId, double amount,
                                         @NotNull String operation);

    @LuaDoc(description = "Sets the texture path for this effect (e.g. 'mymod:textures/mob_effect/fire.png').",
            params = {"texture: string"}, returnType = LuaDocFragments.Builder.I_MOB_EFFECT_BUILDER)
    @NotNull IMobEffectBuilder texture(@NotNull String texture);

    @LuaDoc(description = "Sets the user-visible display name for this effect.",
            params = {"name: string"}, returnType = LuaDocFragments.Builder.I_MOB_EFFECT_BUILDER)
    @NotNull IMobEffectBuilder displayName(@NotNull String name);

    @LuaDoc(description = "Configures whether this effect is instantaneous (like Instant Health).",
            params = {"instantaneous: boolean"}, returnType = LuaDocFragments.Builder.I_MOB_EFFECT_BUILDER)
    @NotNull IMobEffectBuilder instantaneous(boolean instantaneous);

    @LuaDoc(description = "Sets the interval in ticks between MobEffectTick events (0 = disabled).",
            params = {"ticks: integer"}, returnType = LuaDocFragments.Builder.I_MOB_EFFECT_BUILDER)
    @NotNull IMobEffectBuilder tickInterval(int ticks);

    @LuaDoc(description = "Sets whether curative items (milk bucket) cure this effect.",
            params = {"curable: boolean"}, returnType = LuaDocFragments.Builder.I_MOB_EFFECT_BUILDER)
    @NotNull IMobEffectBuilder curable(boolean curable);

    @LuaDoc(description = "Registers the effect during the next registry event. Returns false "
            + "when the definition was invalid.",
            returnType = "boolean")
    boolean register();
}
