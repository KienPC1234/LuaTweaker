package com.luatweaker.platform.container;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locked slots must refuse placements and pickups at the slot level (GUI),
 * while every other path is guarded by the block entity (setItem/removeItem/
 * insertItem/extractItem/isItemValid).
 */
class ContainerFilteredSlotTest {

    /** ItemStack.EMPTY has a null holder - no registry access on a plain JVM. */
    private static final ItemStack STACK = ItemStack.EMPTY;

    private static ContainerFilteredSlot lockedSlot() {
        return new ContainerFilteredSlot(new SimpleContainer(2), 0, 0, 0, true);
    }

    private static ContainerFilteredSlot openSlot() {
        return new ContainerFilteredSlot(new SimpleContainer(2), 1, 0, 0, false);
    }

    @Test
    void lockedSlotReportsLocked() {
        assertTrue(lockedSlot().isLocked(),
                "a slot built with locked=true must report locked");
    }

    @Test
    void lockedSlotRefusesPlacement() {
        assertFalse(lockedSlot().mayPlace(STACK),
                "locked slot must reject placements");
    }

    @Test
    void lockedSlotRefusesPickup() {
        assertFalse(lockedSlot().mayPickup(null),
                "locked slot must reject pickups");
    }

    @Test
    void unlockedSlotReportsUnlocked() {
        assertFalse(openSlot().isLocked(),
                "a slot built with locked=false must report unlocked");
    }

    @Test
    void unlockedSlotAcceptsPlacement() {
        assertTrue(openSlot().mayPlace(STACK),
                "unlocked slot accepts items");
    }

    @Test
    void unlockedSlotAcceptsPickup() {
        assertTrue(openSlot().mayPickup(null),
                "unlocked slot accepts pickups");
    }

    @Test
    void defaultConstructorIsUnlocked() {
        ContainerFilteredSlot slot = new ContainerFilteredSlot(new SimpleContainer(2), 1, 0, 0);

        assertFalse(slot.isLocked(),
                "the default constructor must build an unlocked slot");
    }
}
