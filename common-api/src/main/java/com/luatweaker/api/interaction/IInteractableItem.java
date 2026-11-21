package com.luatweaker.api.interaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IInteractableItem {
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

    @NotNull
    Object getRawItemStack();
}
