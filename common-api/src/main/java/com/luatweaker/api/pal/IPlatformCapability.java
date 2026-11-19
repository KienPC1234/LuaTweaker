package com.luatweaker.api.pal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Platform bridge for the standard NeoForge block capabilities.
 *
 * <p>These are the primitives Lua needs to talk to another mod's machines instead of
 * guessing their NBT layout: energy ({@code IEnergyStorage}), items ({@code IItemHandler})
 * and fluids ({@code IFluidHandler}). Every method looks the capability up on the block at
 * the given position; the platform never moves anything on its own.</p>
 *
 * <p>Return conventions: {@code null} means "there is no such block entity / capability"
 * (or the dimension is not loaded), while {@code 0} / an empty list means "the capability
 * exists but refused the operation". Callers must distinguish the two.</p>
 */
public interface IPlatformCapability {

    /**
     * Reads an energy buffer.
     *
     * @param side {@code "up"}, {@code "down"}, {@code "north"}, {@code "south"},
     *             {@code "west"}, {@code "east"}, or {@code null} for the unsided lookup
     * @return {@code {Stored, Max, CanExtract, CanReceive}}, or {@code null}
     */
    @Nullable
    Map<String, Object> getEnergy(@NotNull String dimension, int x, int y, int z, @Nullable String side);

    /** Feeds energy into the block. Returns the amount actually accepted (never over the block's rate). */
    int insertEnergy(@NotNull String dimension, int x, int y, int z, int amount, @Nullable String side);

    /** Pulls energy out of the block. Returns the amount actually extracted. */
    int extractEnergy(@NotNull String dimension, int x, int y, int z, int amount, @Nullable String side);

    /**
     * Snapshots every occupied item slot.
     *
     * @return a list of {@code {Slot, Id, Count}} (empty when the handler has no items),
     *         or {@code null} when the block has no item handler
     */
    @Nullable
    List<Map<String, Object>> getItemSlots(@NotNull String dimension, int x, int y, int z,
                                           @Nullable String side);

    /** Inserts into the first slots that accept the stack. Returns the number of items inserted. */
    int insertItem(@NotNull String dimension, int x, int y, int z,
                   @NotNull String itemId, int count, @Nullable String side);

    /**
     * Extracts from one specific slot.
     *
     * @return {@code {Slot, Id, Count}} describing what was removed, or {@code null}
     */
    @Nullable
    Map<String, Object> extractItem(@NotNull String dimension, int x, int y, int z,
                                    int slot, int count, @Nullable String side);

    /**
     * Reads tank 0.
     *
     * @return {@code {Id, Amount, Capacity}} ({@code Id} is empty for an empty tank),
     *         or {@code null} when the block has no fluid handler
     */
    @Nullable
    Map<String, Object> getFluid(@NotNull String dimension, int x, int y, int z, @Nullable String side);

    /** Fills the block. Returns the amount of fluid actually accepted. */
    int fillFluid(@NotNull String dimension, int x, int y, int z,
                  @NotNull String fluidId, int amount, @Nullable String side);

    /**
     * Drains a fluid from the first tank that holds it. An empty id or {@code "*"} drains
     * whatever the first non-empty tank contains.
     *
     * @return the amount actually drained
     */
    int drainFluid(@NotNull String dimension, int x, int y, int z,
                   @Nullable String fluidId, int amount, @Nullable String side);
}
