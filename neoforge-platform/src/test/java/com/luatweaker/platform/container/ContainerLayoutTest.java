package com.luatweaker.platform.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container GUI geometry must exactly match the vanilla chest layout:
 * 9-col grids start at x=8, smaller grids are centered, the player inventory
 * shifts down as rows grow and nothing may overlap the 176px panel.
 */
public class ContainerLayoutTest {

    @Test
    public void nineColumnGridStartsAtEight() {
        assertEquals(8, ContainerLayout.containerStartX(9));
    }

    @Test
    public void sixColumnGridIsCentered() {
        assertEquals(35, ContainerLayout.containerStartX(6));
    }

    @Test
    public void threeColumnGridMatchesBarrelPosition() {
        assertEquals(62, ContainerLayout.containerStartX(3));
    }

    @Test
    public void gridFitsInsidePanelForEveryWidth() {
        for (int cols = 1; cols <= 9; cols++) {
            int lastSlotX = ContainerLayout.containerStartX(cols) + (cols - 1) * 18;
            assertTrue(lastSlotX + 16 <= 176, cols + "-col grid must fit the panel");
        }
    }

    @Test
    public void imageHeightMatchesVanillaChestFormula() {
        assertEquals(168, ContainerLayout.imageHeight(3));
        assertEquals(186, ContainerLayout.imageHeight(4));
        assertEquals(222, ContainerLayout.imageHeight(6));
    }

    @Test
    public void playerInventorySitsBelowLastContainerRowForEveryHeight() {
        for (int rows = 1; rows <= 6; rows++) {
            int lastContainerBottom = ContainerLayout.containerY(rows - 1) + 16;
            int playerTop = ContainerLayout.playerY(0, rows) - 1;
            assertTrue(playerTop > lastContainerBottom,
                    rows + "-row container must not overlap the player inventory");
        }
    }

    @Test
    public void hotbarFitsInsidePanelForEveryHeight() {
        for (int rows = 1; rows <= 6; rows++) {
            assertTrue(ContainerLayout.hotbarY(rows) + 18 <= ContainerLayout.imageHeight(rows),
                    rows + "-row hotbar must fit the panel");
        }
    }

    @Test
    public void playerRowsShiftConsistently() {
        assertEquals(-18, ContainerLayout.playerShift(3));
        assertEquals(0, ContainerLayout.playerShift(4));
        assertEquals(36, ContainerLayout.playerShift(6));
        assertEquals(103, ContainerLayout.playerY(0, 4));
        assertEquals(161, ContainerLayout.hotbarY(4));
    }

    @Test
    public void inventoryLabelSitsBetweenContainerAndInventory() {
        for (int rows = 1; rows <= 6; rows++) {
            int labelY = ContainerLayout.inventoryLabelY(rows);
            int lastContainerBottom = ContainerLayout.containerY(rows - 1) + 16;
            int playerTop = ContainerLayout.playerY(0, rows) - 1;
            assertTrue(labelY >= lastContainerBottom && labelY + 9 <= playerTop,
                    rows + "-row inventory label must fit between container and inventory");
        }
    }
}
