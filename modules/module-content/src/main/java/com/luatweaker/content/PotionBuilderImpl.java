package com.luatweaker.content;

import com.luatweaker.api.content.IPotionBuilder;
import com.luatweaker.api.content.PotionDefinition;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Collects one custom potion and hands it to {@link EffectRegistry}. */
final class PotionBuilderImpl implements IPotionBuilder {

    private final String id;
    private final List<PotionDefinition.PotionEffectSpec> effects = new ArrayList<>();

    PotionBuilderImpl(@NotNull String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Potion id must not be blank");
        }
        this.id = id;
    }

    @Override
    public @NotNull IPotionBuilder effect(@NotNull String effectId, int durationTicks,
                                          int amplifier) {
        effects.add(new PotionDefinition.PotionEffectSpec(effectId, durationTicks, amplifier));
        return this;
    }

    @Override
    public boolean register() {
        EffectRegistry.registerPotion(new PotionDefinition(id, effects));
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered custom potion: " + id);
        return true;
    }
}
