package com.luatweaker.api.interaction;

import org.jetbrains.annotations.NotNull;

public interface IInteractableBlock {
    @NotNull
    String getId();

    void setId(@NotNull String blockId);

    int getX();

    int getY();

    int getZ();

    @NotNull
    String getDimension();

    boolean breakBlock();

    boolean useBlock(@NotNull Object actorEntity);

    default float getHardness() { return 1.0f; }
    default int getLightLevel() { return 0; }
    default boolean isAir() { return false; }
    default boolean isSolid() { return true; }
    default boolean isLiquid() { return false; }

    default @NotNull String getNbt() { return "{}"; }
    default void setNbt(@NotNull String nbtJson) {}
    default @org.jetbrains.annotations.Nullable String getAttribute(@NotNull String key) { return null; }
    default void setAttribute(@NotNull String key, @NotNull String value) {}

    @NotNull
    Object getRawBlockState();

    default boolean Destroy() { return breakBlock(); }

    default boolean triggerAnimation(@NotNull String animName, @NotNull String controllerName) {
        return com.luatweaker.api.pal.Platform.getInteraction().triggerGeoAnimation(this, controllerName, animName, 1.0, false);
    }

    default boolean playAnimation(@NotNull String animName, @NotNull String controllerName) {
        return triggerAnimation(animName, controllerName);
    }

    default boolean stopAnimation(@NotNull String animName, @NotNull String controllerName) {
        return com.luatweaker.api.pal.Platform.getInteraction().stopGeoAnimation(this, controllerName, animName);
    }

    default boolean stopAnimation(@NotNull String animName) {
        return stopAnimation(animName, "controller");
    }

    default @org.jetbrains.annotations.Nullable java.util.Map<String, Object> getEnergy(@org.jetbrains.annotations.Nullable String side) {
        var cap = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        return cap != null ? cap.getEnergy(getDimension(), getX(), getY(), getZ(), side) : null;
    }
    default @org.jetbrains.annotations.Nullable java.util.Map<String, Object> getEnergy() { return getEnergy(null); }

    default int insertEnergy(int amount, @org.jetbrains.annotations.Nullable String side) {
        var cap = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        return cap != null ? cap.insertEnergy(getDimension(), getX(), getY(), getZ(), amount, side) : 0;
    }
    default int insertEnergy(int amount) { return insertEnergy(amount, null); }

    default int extractEnergy(int amount, @org.jetbrains.annotations.Nullable String side) {
        var cap = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        return cap != null ? cap.extractEnergy(getDimension(), getX(), getY(), getZ(), amount, side) : 0;
    }
    default int extractEnergy(int amount) { return extractEnergy(amount, null); }

    default @org.jetbrains.annotations.Nullable java.util.List<java.util.Map<String, Object>> getItemSlots(@org.jetbrains.annotations.Nullable String side) {
        var cap = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        return cap != null ? cap.getItemSlots(getDimension(), getX(), getY(), getZ(), side) : null;
    }
    default @org.jetbrains.annotations.Nullable java.util.List<java.util.Map<String, Object>> getItemSlots() { return getItemSlots(null); }

    default int insertItem(@NotNull String itemId, int count, @org.jetbrains.annotations.Nullable String side) {
        var cap = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        return cap != null ? cap.insertItem(getDimension(), getX(), getY(), getZ(), itemId, count, side) : 0;
    }
    default int insertItem(@NotNull String itemId, int count) { return insertItem(itemId, count, null); }

    default @org.jetbrains.annotations.Nullable java.util.Map<String, Object> extractItem(int slot, int count, @org.jetbrains.annotations.Nullable String side) {
        var cap = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        return cap != null ? cap.extractItem(getDimension(), getX(), getY(), getZ(), slot, count, side) : null;
    }
    default @org.jetbrains.annotations.Nullable java.util.Map<String, Object> extractItem(int slot, int count) { return extractItem(slot, count, null); }

    default @org.jetbrains.annotations.Nullable java.util.Map<String, Object> getFluid(@org.jetbrains.annotations.Nullable String side) {
        var cap = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        return cap != null ? cap.getFluid(getDimension(), getX(), getY(), getZ(), side) : null;
    }
    default @org.jetbrains.annotations.Nullable java.util.Map<String, Object> getFluid() { return getFluid(null); }

    default int fillFluid(@NotNull String fluidId, int amount, @org.jetbrains.annotations.Nullable String side) {
        var cap = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        return cap != null ? cap.fillFluid(getDimension(), getX(), getY(), getZ(), fluidId, amount, side) : 0;
    }
    default int fillFluid(@NotNull String fluidId, int amount) { return fillFluid(fluidId, amount, null); }

    default int drainFluid(@org.jetbrains.annotations.Nullable String fluidId, int amount, @org.jetbrains.annotations.Nullable String side) {
        var cap = com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        return cap != null ? cap.drainFluid(getDimension(), getX(), getY(), getZ(), fluidId, amount, side) : 0;
    }
    default int drainFluid(int amount) { return drainFluid("*", amount, null); }
}
