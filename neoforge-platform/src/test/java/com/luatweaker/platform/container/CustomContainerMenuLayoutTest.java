package com.luatweaker.platform.container;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Menu wiring: grid fills, manual mode adds only positioned slots. */
class CustomContainerMenuLayoutTest {

    private static final int ROWS = 3;
    private static final int COLS = 9;

    private static Map<Integer, int[]> positions() {
        return Map.of(0, new int[]{44, 30}, 1, new int[]{116, 30});
    }

    private static CustomContainerMenu gridMenu() {
        return new CustomContainerMenu(null, 0, new Inventory(null),
                new SimpleContainer(ROWS * COLS), ROWS, COLS, null, null, false);
    }

    private static CustomContainerMenu manualMenu() {
        return new CustomContainerMenu(null, 0, new Inventory(null),
                new SimpleContainer(ROWS * COLS), ROWS, COLS, positions(), null, true);
    }

    @Test
    void gridFillsEveryCell() {
        assertEquals(ROWS * COLS + 36, gridMenu().slots.size(),
                "grid mode must add every container cell plus the player inventory");
    }

    @Test
    void manualKeepsOnlyPositionedSlots() {
        assertEquals(2 + 36, manualMenu().slots.size(),
                "manual mode must add only the positioned slots plus the player inventory");
    }

    @Test
    void manualSlotsKeepPositions() {
        CustomContainerMenu menu = manualMenu();

        assertEquals(44, menu.slots.get(0).x,
                "the first manual slot must sit at its configured x");
    }

    @Test
    void manualSecondSlotKeepsPosition() {
        CustomContainerMenu menu = manualMenu();

        assertEquals(116, menu.slots.get(1).x,
                "the second manual slot must sit at its configured x");
    }

    @Test
    void manualLocksSurvive() {
        CustomContainerMenu menu = new CustomContainerMenu(null, 0, new Inventory(null),
                new SimpleContainer(ROWS * COLS), ROWS, COLS, positions(), Set.of(0), true);

        assertTrue(((ContainerFilteredSlot) menu.slots.get(0)).isLocked(),
                "a locked manual slot must stay locked");
    }
}
