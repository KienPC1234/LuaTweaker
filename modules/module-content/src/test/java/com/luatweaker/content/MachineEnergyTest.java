package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IBlockBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Machine energy/fluid storage capacities round-trip through Lua. */
class MachineEnergyTest extends ContentFixture {

    private IBlockBuilder machine() throws IOException {
        runMachineScript();
        return block("test_machine");
    }

    @Test
    void threeBlocksRegistered() throws IOException {
        runMachineScript();

        assertEquals(3, contentService.getRegisteredBlocks().size(),
                "machine, pipe and tick machine must all register");
    }

    @Test
    void keepsEnergyCapacity() throws IOException {
        assertEquals(10_000, machine().getEnergyCapacity(), "the energy capacity must be kept");
    }

    @Test
    void keepsEnergyReceive() throws IOException {
        assertEquals(500, machine().getEnergyMaxReceive(), "the energy receive rate must be kept");
    }

    @Test
    void keepsEnergyExtract() throws IOException {
        assertEquals(250, machine().getEnergyMaxExtract(), "the energy extract rate must be kept");
    }

    @Test
    void keepsFluidCapacity() throws IOException {
        assertEquals(8000, machine().getFluidCapacity(), "the fluid capacity must be kept");
    }

    @Test
    void keepsTwoGuiBars() throws IOException {
        assertEquals(2, machine().getGuiBars().size(), "both GUI bars must be kept");
    }

    @Test
    void machineHasNoConnectionState() throws IOException {
        assertFalse(machine().isConnectionState(),
                "the plain machine must not be a connection block");
    }
}
