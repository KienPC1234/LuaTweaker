package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IBlockBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Machine GUI bars and boolean states keep every visual property. */
class MachineGuiTest extends ContentFixture {

    private IBlockBuilder machine() throws IOException {
        runMachineScript();
        return block("test_machine");
    }

    @Test
    void keepsEnergyBarId() throws IOException {
        assertEquals("energy_bar", machine().getGuiBars().get(0).id(),
                "the energy bar id must be kept");
    }

    @Test
    void keepsEnergyBarWidth() throws IOException {
        assertEquals(90, machine().getGuiBars().get(0).width(),
                "the energy bar width must be kept");
    }

    @Test
    void keepsEnergyBarSource() throws IOException {
        assertEquals("energy", machine().getGuiBars().get(0).source(),
                "the energy bar source must be kept");
    }

    @Test
    void keepsEnergyBarColor() throws IOException {
        assertEquals(0xFF00E676, machine().getGuiBars().get(0).color(),
                "the energy bar color must be kept");
    }

    @Test
    void keepsFluidBarSource() throws IOException {
        assertEquals("fluid", machine().getGuiBars().get(1).source(),
                "the fluid bar source must be kept");
    }

    @Test
    void keepsBooleanState() throws IOException {
        assertNotNull(machine().getBooleanState(), "the boolean state must be kept");
    }

    @Test
    void keepsRunningProperty() throws IOException {
        assertEquals("running", machine().getBooleanState().property(),
                "the boolean state property must be kept");
    }

    @Test
    void keepsOnTexture() throws IOException {
        assertEquals("luatweaker:block/test_machine_running", machine().getBooleanState().onTexture(),
                "the boolean state on-texture must be kept");
    }
}
