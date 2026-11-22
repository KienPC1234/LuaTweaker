package com.luatweaker.common.container;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which container slots a menu opens, in menu order.
 *
 * <p>Pure math on purpose (no Minecraft runtime): the server menu and its tests
 * share it. Grid mode fills every {@code rows x cols} cell, honoring
 * per-slot position overrides; manual mode only keeps slots with an explicit
 * position, so machine GUIs with a few hand-placed slots do not grow phantom
 * cells that overlap bars and labels.</p>
 */
public final class ContainerSlotPlanner {

    /** One slot to add: storage index, GUI position and lock flag, in menu order. */
    public record PlacedSlot(int index, int x, int y, boolean locked) {
    }

    private ContainerSlotPlanner() {
    }

    /**
     * Plans the container slots for one menu open.
     *
     * @param rows      container rows (panel/player geometry, unchanged by this)
     * @param cols      container columns
     * @param positions explicit per-slot GUI positions (may be null or partial)
     * @param locked    locked slot indices (may be null)
     * @param manual    when true, only positioned slots are kept
     * @return slots in ascending storage-index order
     */
    public static @NotNull List<PlacedSlot> plan(int rows, int cols,
            @Nullable Map<Integer, int[]> positions, @Nullable Set<Integer> locked,
            boolean manual) {
        if (manual) {
            return planManual(positions, locked);
        }
        return planGrid(rows, cols, positions, locked);
    }

    private static @NotNull List<PlacedSlot> planManual(
            @Nullable Map<Integer, int[]> positions, @Nullable Set<Integer> locked) {
        List<PlacedSlot> planned = new ArrayList<>();
        if (positions != null) {
            positions.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .filter(entry -> hasPosition(entry.getValue()))
                    .forEach(entry -> planned.add(new PlacedSlot(
                            entry.getKey(), entry.getValue()[0], entry.getValue()[1],
                            locked != null && locked.contains(entry.getKey()))));
        }
        return List.copyOf(planned);
    }

    private static @NotNull List<PlacedSlot> planGrid(int rows, int cols,
            @Nullable Map<Integer, int[]> positions, @Nullable Set<Integer> locked) {
        List<PlacedSlot> planned = new ArrayList<>();
        int startX = ContainerLayout.containerStartX(cols);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = row * cols + col;
                int[] custom = positions != null ? positions.get(index) : null;
                int x = hasPosition(custom) ? custom[0] : startX + col * ContainerLayout.CELL;
                int y = hasPosition(custom) ? custom[1] : ContainerLayout.containerY(row);
                planned.add(new PlacedSlot(index, x, y, locked != null && locked.contains(index)));
            }
        }
        return List.copyOf(planned);
    }

    private static boolean hasPosition(int... custom) {
        return custom != null && custom.length >= 2;
    }
}
