package com.luatweaker.api.pal;

import org.jetbrains.annotations.NotNull;

/**
 * Platform abstraction for runtime structure operations
 * (implemented by the NeoForge platform).
 */
@FunctionalInterface
public interface IPlatformStructures {

    /**
     * Places an NBT template into a loaded dimension level at the given
     * position.
     *
     * @param templateId  template reference {@code "namespace:path"} (optional
     *                    trailing {@code .nbt} accepted)
     * @param dimensionId resource location of the target dimension level
     * @param x           block X
     * @param y           block Y
     * @param z           block Z
     * @param rotation    {@code none}, {@code clockwise_90},
     *                    {@code clockwise_180} or {@code counterclockwise_90}
     * @param mirror      {@code none}, {@code left_right} or {@code front_back}
     * @return true when the template was found and placed
     * @throws IllegalArgumentException on malformed ids or unknown
     *                                  rotation/mirror names
     */
    boolean placeTemplate(@NotNull String templateId, @NotNull String dimensionId,
                          int x, int y, int z,
                          @NotNull String rotation, @NotNull String mirror);
}
