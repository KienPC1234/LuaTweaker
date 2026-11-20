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
}
