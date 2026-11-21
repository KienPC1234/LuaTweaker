package com.luatweaker.api.interaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IInteractableItem extends IEnchantableItem {
    @NotNull
    String getId();

    int getCount();

    void setCount(int count);

    int getSlot();

    void setSlot(int slot);

    @Nullable
    String getOwnerUuid();

    boolean useItem(@NotNull Object actorEntity);

    boolean drop(@NotNull Object actorEntity, int count);

    default int getDamage() { return 0; }
    default void setDamage(int damage) {}
    default int getMaxDamage() { return 0; }
    default @Nullable String getCustomName() { return null; }
    default void setCustomName(@NotNull String name) {}
    default boolean isDamageable() { return false; }
    default boolean isEnchanted() { return false; }

    default @NotNull String getNbt() { return "{}"; }
    default void setNbt(@NotNull String nbtJson) {}
    default @Nullable String getAttribute(@NotNull String key) { return null; }
    default void setAttribute(@NotNull String key, @NotNull String value) {}

    default @Nullable Object getCustomData(@NotNull String key) { return null; }
    default void setCustomData(@NotNull String key, @Nullable Object value) {}

    default boolean playAnimation(@NotNull String animName, @NotNull String controllerName) {
        return com.luatweaker.api.pal.Platform.getInteraction().triggerGeoAnimation(this, controllerName, animName, 1.0, false);
    }

    default boolean playAnimation(@NotNull String animName) {
        return playAnimation(animName, "controller");
    }

    default boolean stopAnimation(@NotNull String animName, @NotNull String controllerName) {
        return com.luatweaker.api.pal.Platform.getInteraction().stopGeoAnimation(this, controllerName, animName);
    }

    default boolean stopAnimation(@NotNull String animName) {
        return stopAnimation(animName, "controller");
    }

    default @NotNull java.util.List<String> getLore() { return java.util.List.of(); }
    default void setLore(@NotNull java.util.List<String> lore) {}
    default void addLore(@NotNull String line) {}
    default @NotNull String getRarity() { return "COMMON"; }
    default boolean hasGlint() { return false; }
    default int getMaxStackSize() { return 64; }
    default int getBurnTime() { return 0; }
    default boolean isFood() { return false; }
    default int getNutrition() { return 0; }
    default float getSaturation() { return 0.0f; }
    default boolean isEmpty() { return false; }
    default @NotNull IInteractableItem copy() { return this; }

    @NotNull
    Object getRawItemStack();
}
