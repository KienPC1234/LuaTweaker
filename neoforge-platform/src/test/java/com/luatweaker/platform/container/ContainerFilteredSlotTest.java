package com.luatweaker.platform.container;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locked slots must refuse placements and pickups at the slot level (GUI),
 * while every other path is guarded by the block entity (setItem/removeItem/
 * insertItem/extractItem/isItemValid).
 */
public class ContainerFilteredSlotTest {

    /** ItemStack.EMPTY has a null holder - no registry access on a plain JVM. */
    private static final ItemStack STACK = ItemStack.EMPTY;

    @Test
    public void lockedSlotRefusesPlacementAndPickup() {
        ContainerFilteredSlot locked = new ContainerFilteredSlot(new SimpleContainer(2), 0, 0, 0, true);

        assertTrue(locked.isLocked());
        assertFalse(locked.mayPlace(STACK), "locked slot must reject placements");
        assertFalse(locked.mayPickup(null), "locked slot must reject pickups");
    }

    @Test
    public void unlockedSlotBehavesNormally() {
        ContainerFilteredSlot open = new ContainerFilteredSlot(new SimpleContainer(2), 1, 0, 0, false);

        assertFalse(open.isLocked());
        assertTrue(open.mayPlace(STACK), "unlocked slot accepts items");
        assertTrue(open.mayPickup(null));
    }

    @Test
    public void defaultConstructorIsUnlocked() {
        ContainerFilteredSlot slot = new ContainerFilteredSlot(new SimpleContainer(2), 1, 0, 0);
        assertFalse(slot.isLocked());
    }
}
