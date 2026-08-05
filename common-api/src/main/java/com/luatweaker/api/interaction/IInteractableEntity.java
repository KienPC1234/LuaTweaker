package com.luatweaker.api.interaction;

import org.jetbrains.annotations.NotNull;

public interface IInteractableEntity {
    @NotNull
    String getId();

    @NotNull
    String getName();

    @NotNull
    String getType();

    double getX();

    double getY();

    double getZ();

    void setPosition(double x, double y, double z);

    float getHealth();

    void setHealth(float health);

    void lookAt(double x, double y, double z);

    void lookAt(@NotNull Object targetEntity);

    default float getMaxHealth() { return 20.0f; }
    default void setMaxHealth(float maxHealth) {}
    default boolean isAlive() { return true; }
    default boolean isOnFire() { return false; }
    default void setOnFire(boolean onFire) {}
    default boolean isSneaking() { return false; }
    default void setSneaking(boolean sneaking) {}
    default boolean isSprinting() { return false; }
    default void setSprinting(boolean sprinting) {}
    default @NotNull String getCustomName() { return getName(); }
    default void setCustomName(@NotNull String name) {}
    default double getVx() { return 0.0; }
    default double getVy() { return 0.0; }
    default double getVz() { return 0.0; }
    default void setVelocity(double vx, double vy, double vz) {}

    default @NotNull String getNbt() { return "{}"; }
    default void setNbt(@NotNull String nbtJson) {}
    default @org.jetbrains.annotations.Nullable String getAttribute(@NotNull String key) { return null; }
    default void setAttribute(@NotNull String key, @NotNull String value) {}

    default void SendMessage(@NotNull String message) {}
    default void SendTitle(@NotNull String title, @NotNull String subtitle) {}
    default void SendOverlayMessage(@NotNull String message) {}
    default boolean GiveItem(@NotNull String itemId, int count) { return false; }

    default void PlayAnimation(@NotNull String animationName, double speed, double transitionLength) {}
    default void StopAnimation(@NotNull String animationName) {}

    default void Destroy() {}
}
