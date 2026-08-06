package com.luatweaker.platform.container;

/**
 * Pure geometry for Lua-configured container GUIs, mirroring the vanilla chest
 * layout from {@code ChestMenu} (1.21.1): a 176-wide panel whose container
 * grid grows downward and the player inventory/hotbar shift by
 * {@code (rows - 4) * 18} so larger containers never overlap them.
 *
 * <p>Pure math on purpose: unit-testable without a Minecraft runtime and shared
 * by both the server menu (slot positions) and the client screen (panel size,
 * labels, cell rendering).</p>
 */
public final class ContainerLayout {

    /** Standard panel width used by every vanilla inventory GUI. */
    public static final int PANEL_WIDTH = 176;
    /** Left margin where the 9-column player grid starts (vanilla). */
    public static final int LEFT_MARGIN = 8;
    /** Horizontal span of the 9-column player inventory grid (8..170). */
    private static final int GRID_SPAN = 162;
    /** Slot cell size (18px grid, 16px icon). */
    public static final int CELL = 18;

    private ContainerLayout() {}

    /** Panel height for the given container row count (vanilla: 114 + rows*18). */
    public static int imageHeight(int rows) {
        return 114 + rows * CELL;
    }

    /** Y of the "Inventory" label: 94px above the panel bottom (vanilla ChestScreen). */
    public static int inventoryLabelY(int rows) {
        return imageHeight(rows) - 94;
    }

    /**
     * X of the first container slot column. The container grid is centered on
     * the 9-column span: 9 cols start at 8, 6 cols at 35, 3 cols at 62 (the
     * vanilla barrel position).
     */
    public static int containerStartX(int cols) {
        return LEFT_MARGIN + (GRID_SPAN - cols * CELL) / 2;
    }

    /** Y of container slot row (vanilla chest: slots start at 18). */
    public static int containerY(int row) {
        return 18 + row * CELL;
    }

    /** Y of player inventory row (vanilla: 103 + shift). */
    public static int playerY(int row, int rows) {
        return 103 + row * CELL + (rows - 4) * CELL;
    }

    /** Y of the hotbar row (vanilla: 161 + shift). */
    public static int hotbarY(int rows) {
        return 161 + (rows - 4) * CELL;
    }

    /** Vertical shift applied to the player inventory for rows != 4. */
    public static int playerShift(int rows) {
        return (rows - 4) * CELL;
    }
}
