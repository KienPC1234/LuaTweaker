package com.luatweaker.common.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The player inventory, hotbar and label must stack below the container grid. */
class ContainerLayoutPlayerTest {

    @Test
    void playerInventorySitsBelowLastContainerRowForEveryHeight() {
        for (int rows = 1; rows <= 6; rows++) {
            int lastContainerBottom = ContainerLayout.containerY(rows - 1) + 16;
            int playerTop = ContainerLayout.playerY(0, rows) - 1;

            assertTrue(playerTop > lastContainerBottom,
                    rows + "-row container must not overlap the player inventory");
        }
    }

    @Test
    void hotbarFitsInsidePanelForEveryHeight() {
        for (int rows = 1; rows <= 6; rows++) {
            assertTrue(ContainerLayout.hotbarY(rows) + 18 <= ContainerLayout.imageHeight(rows),
                    rows + "-row hotbar must fit the panel");
        }
    }

    @Test
    void threeRowsShiftPlayerUpBy18() {
        assertEquals(-18, ContainerLayout.playerShift(3),
                "a small 3-row container must pull the player inventory up");
    }

    @Test
    void fourRowsNeedNoShift() {
        assertEquals(0, ContainerLayout.playerShift(4),
                "a vanilla-sized 4-row container needs no player shift");
    }

    @Test
    void sixRowsShiftPlayerDownBy36() {
        assertEquals(36, ContainerLayout.playerShift(6),
                "a tall 6-row container must push the player inventory down");
    }

    @Test
    void playerYMatchesVanillaChest() {
        assertEquals(103, ContainerLayout.playerY(0, 4),
                "the player inventory top must match the vanilla chest layout");
    }

    @Test
    void hotbarYMatchesVanillaChest() {
        assertEquals(161, ContainerLayout.hotbarY(4),
                "the hotbar top must match the vanilla chest layout");
    }

    @Test
    void inventoryLabelSitsBetweenContainerAndInventory() {
        for (int rows = 1; rows <= 6; rows++) {
            int labelY = ContainerLayout.inventoryLabelY(rows);
            int lastContainerBottom = ContainerLayout.containerY(rows - 1) + 16;
            int playerTop = ContainerLayout.playerY(0, rows) - 1;

            assertTrue(labelY >= lastContainerBottom && labelY + 9 <= playerTop,
                    rows + "-row inventory label must fit between container and inventory");
        }
    }
}
