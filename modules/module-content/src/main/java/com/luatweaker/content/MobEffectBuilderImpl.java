package com.luatweaker.content;

import com.luatweaker.api.content.IMobEffectBuilder;
import com.luatweaker.api.content.MobEffectDefinition;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Collects one custom mob effect and hands it to {@link EffectRegistry}. */
final class MobEffectBuilderImpl implements IMobEffectBuilder {

    private static final int DEFAULT_COLOR = 0xFFFFFF;
    private static final String DEFAULT_CATEGORY = "NEUTRAL";
    private static final String DEFAULT_OPERATION = "ADD_VALUE";

    private final String id;
    private final List<MobEffectDefinition.AttributeModifierSpec> modifiers = new ArrayList<>();
    private int colorValue = DEFAULT_COLOR;
    private String categoryValue = DEFAULT_CATEGORY;
    private String texturePath;
    private String name;
    private boolean isInstant;
    private int ticks;
    private boolean canCure = true;

    MobEffectBuilderImpl(@NotNull String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Mob effect id must not be blank");
        }
        this.id = id;
    }

    @Override
    public @NotNull IMobEffectBuilder color(int rgb) {
        if (rgb < 0 || rgb > EffectRegistry.MAX_RGB) {
            throw new IllegalArgumentException(
                    "Mob effect color must be between 0 and " + EffectRegistry.MAX_RGB);
        }
        this.colorValue = rgb;
        return this;
    }

    @Override
    public @NotNull IMobEffectBuilder category(@NotNull String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Mob effect category must not be blank");
        }
        this.categoryValue = value;
        return this;
    }

    @Override
    public @NotNull IMobEffectBuilder attribute(@NotNull String attributeId, double amount,
                                                @NotNull String operation) {
        String resolvedOperation = operation == null || operation.isBlank()
                ? DEFAULT_OPERATION : operation;
        modifiers.add(new MobEffectDefinition.AttributeModifierSpec(
                attributeId, amount, resolvedOperation));
        return this;
    }

    @Override
    public @NotNull IMobEffectBuilder texture(@NotNull String texture) {
        this.texturePath = texture;
        return this;
    }

    @Override
    public @NotNull IMobEffectBuilder displayName(@NotNull String displayName) {
        this.name = displayName;
        return this;
    }

    @Override
    public @NotNull IMobEffectBuilder instantaneous(boolean instantaneous) {
        this.isInstant = instantaneous;
        return this;
    }

    @Override
    public @NotNull IMobEffectBuilder tickInterval(int intervalTicks) {
        this.ticks = Math.max(0, intervalTicks);
        return this;
    }

    @Override
    public @NotNull IMobEffectBuilder curable(boolean curable) {
        this.canCure = curable;
        return this;
    }

    @Override
    public boolean register() {
        EffectRegistry.registerMobEffect(
                new MobEffectDefinition(id, colorValue, categoryValue, modifiers,
                        texturePath, name, isInstant, ticks, canCure));
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered custom mob effect: " + id);
        return true;
    }
}
