package com.luatweaker.common.container;

import com.luatweaker.content.BlockBuilderImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A crate block must keep its container geometry and storage. */
class ContainerCrateSpecTest {

    private static ContainerSpec crateSpec() {
        BlockBuilderImpl crate = new BlockBuilderImpl("mymod:crate");
        crate.container(3, 9, "spill")
                .energyStorage(1000, 10, 20)
                .fluidStorage(4000);
        return ContainerSpec.from(crate);
    }

    @Test
    void crateKeepsRowCount() {
        assertEquals(3, crateSpec().rows(),
                "a crate must keep its configured row count");
    }

    @Test
    void crateKeepsColumnCount() {
        assertEquals(9, crateSpec().cols(),
                "a crate must keep its configured column count");
    }

    @Test
    void crateKeepsDropMode() {
        assertEquals("spill", crateSpec().dropMode(),
                "a crate must keep its configured drop mode");
    }

    @Test
    void crateIsAContainerGrid() {
        assertTrue(crateSpec().isContainer(),
                "a crate with a container section is a container grid");
    }

    @Test
    void crateKeepsEnergyCapacity() {
        assertEquals(1000, crateSpec().energyCapacity(),
                "a crate must keep its configured energy capacity");
    }

    @Test
    void crateKeepsEnergyReceiveRate() {
        assertEquals(10, crateSpec().energyMaxReceive(),
                "a crate must keep its configured energy receive rate");
    }

    @Test
    void crateKeepsEnergyExtractRate() {
        assertEquals(20, crateSpec().energyMaxExtract(),
                "a crate must keep its configured energy extract rate");
    }

    @Test
    void crateKeepsFluidCapacity() {
        assertEquals(4000, crateSpec().fluidCapacity(),
                "a crate must keep its configured fluid capacity");
    }
}
