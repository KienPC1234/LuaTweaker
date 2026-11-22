package com.luatweaker.common.container;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Slot planning: grid fill, overrides, manual filtering and locks. */
class ContainerSlotPlannerTest {

    private static final int ROWS = 3;
    private static final int COLS = 9;

    @Nested
    class GridCells {

        @Test
        void fullGridHasEveryCell() {
            List<ContainerSlotPlanner.PlacedSlot> planned =
                    ContainerSlotPlanner.plan(ROWS, COLS, null, null, false);

            assertEquals(ROWS * COLS, planned.size(),
                    "grid mode must fill every cell");
        }

        @Test
        void firstCellStartsAtVanillaOrigin() {
            List<ContainerSlotPlanner.PlacedSlot> planned =
                    ContainerSlotPlanner.plan(ROWS, COLS, null, null, false);

            assertEquals(8, planned.get(0).x(),
                    "the grid must start at the vanilla x=8 margin");
        }

        @Test
        void firstRowSitsAtVanillaHeight() {
            List<ContainerSlotPlanner.PlacedSlot> planned =
                    ContainerSlotPlanner.plan(ROWS, COLS, null, null, false);

            assertEquals(18, planned.get(0).y(),
                    "the first grid row must sit at the vanilla y=18");
        }

        @Test
        void cellsAdvanceByOneCell() {
            List<ContainerSlotPlanner.PlacedSlot> planned =
                    ContainerSlotPlanner.plan(ROWS, COLS, null, null, false);

            assertEquals(8 + ContainerLayout.CELL, planned.get(1).x(),
                    "columns must advance by one cell");
        }

        @Test
        void rowsAdvanceByOneCell() {
            List<ContainerSlotPlanner.PlacedSlot> planned =
                    ContainerSlotPlanner.plan(ROWS, COLS, null, null, false);

            assertEquals(18 + ContainerLayout.CELL, planned.get(COLS).y(),
                    "rows must advance by one cell");
        }
    }

    @Nested
    class Overrides {

        @Test
        void positionedSlotMoves() {
            List<ContainerSlotPlanner.PlacedSlot> planned = ContainerSlotPlanner.plan(
                    ROWS, COLS, Map.of(0, new int[]{44, 30}), null, false);

            assertEquals(44, planned.get(0).x(),
                    "an explicit position must win over the grid");
        }

        @Test
        void positionedSlotKeepsIndex() {
            List<ContainerSlotPlanner.PlacedSlot> planned = ContainerSlotPlanner.plan(
                    ROWS, COLS, Map.of(0, new int[]{44, 30}), null, false);

            assertEquals(0, planned.get(0).index(),
                    "an override must not renumber the storage index");
        }

        @Test
        void shortArrayFallsBackToGrid() {
            List<ContainerSlotPlanner.PlacedSlot> planned = ContainerSlotPlanner.plan(
                    ROWS, COLS, Map.of(5, new int[]{44}), null, false);

            assertEquals(8 + 5 * ContainerLayout.CELL, planned.get(5).x(),
                    "a truncated position array must fall back to the grid cell");
        }

        @Test
        void lockFlagSurvives() {
            List<ContainerSlotPlanner.PlacedSlot> planned = ContainerSlotPlanner.plan(
                    ROWS, COLS, null, Set.of(3), false);

            assertTrue(planned.get(3).locked(),
                    "the lock flag must reach the planned slot");
        }
    }

    @Nested
    class Manual {

        @Test
        void keepsOnlyPositionedSlots() {
            List<ContainerSlotPlanner.PlacedSlot> planned = ContainerSlotPlanner.plan(
                    ROWS, COLS, Map.of(0, new int[]{44, 30}, 1, new int[]{116, 30}),
                    null, true);

            assertEquals(2, planned.size(),
                    "manual mode must drop every unpositioned cell");
        }

        @Test
        void manualSlotsKeepIndices() {
            List<ContainerSlotPlanner.PlacedSlot> planned = ContainerSlotPlanner.plan(
                    ROWS, COLS, Map.of(0, new int[]{44, 30}, 1, new int[]{116, 30}),
                    null, true);

            assertEquals(1, planned.get(1).index(),
                    "manual slots must keep their storage indices for Lua access");
        }

        @Test
        void emptyPositionsMeansNoSlots() {
            List<ContainerSlotPlanner.PlacedSlot> planned =
                    ContainerSlotPlanner.plan(ROWS, COLS, null, null, true);

            assertTrue(planned.isEmpty(),
                    "manual mode with no positions must add no clickable cells");
        }

        @Test
        void manualLocksStillApply() {
            List<ContainerSlotPlanner.PlacedSlot> planned = ContainerSlotPlanner.plan(
                    ROWS, COLS, Map.of(7, new int[]{44, 30}), Set.of(7), true);

            assertTrue(planned.get(0).locked() && planned.get(0).index() == 7,
                    "locks must apply to the kept manual slot");
        }
    }
}
