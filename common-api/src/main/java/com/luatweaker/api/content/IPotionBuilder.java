package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import org.jetbrains.annotations.NotNull;

/**
 * Builder for a custom {@code Potion}. Potions are registry entries; use the vanilla potion
 * item with a {@code potion_contents} component to obtain one in game.
 */
@LuaDoc(description = "Builder for a custom potion. Call Register() after adding its effects.")
public interface IPotionBuilder {

    @LuaDoc(description = "Adds one effect instance. durationTicks -1 means infinite; "
            + "amplifier is 0-based (0 = level I).",
            params = {"effectId: string", "durationTicks: integer", "amplifier: integer"},
            returnType = "IPotionBuilder")
    @NotNull IPotionBuilder effect(@NotNull String effectId, int durationTicks, int amplifier);

    @LuaDoc(description = "Registers the potion during the next registry event. Returns false "
            + "when the definition was invalid.",
            returnType = "boolean")
    boolean register();
}
