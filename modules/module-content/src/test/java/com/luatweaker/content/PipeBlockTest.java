package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IBlockBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pipes keep connection state, textures and their Lua tick handler. */
class PipeBlockTest extends ContentFixture {

    private IBlockBuilder pipe() throws IOException {
        runMachineScript();
        return block("test_pipe");
    }

    @Test
    void keepsConnectionState() throws IOException {
        assertTrue(pipe().isConnectionState(), "the pipe must be a connection block");
    }

    @Test
    void hasNoBooleanState() throws IOException {
        assertNull(pipe().getBooleanState(), "the pipe must not gain a boolean state");
    }

    @Test
    void keepsTexture() throws IOException {
        assertEquals("luatweaker:block/test_pipe", pipe().getTexture(),
                "the pipe texture must be kept");
    }

    @Test
    void keepsEnergyCapacity() throws IOException {
        assertEquals(2000, pipe().getEnergyCapacity(), "the pipe energy capacity must be kept");
    }

    @Test
    void keepsFluidCapacity() throws IOException {
        assertEquals(4000, pipe().getFluidCapacity(), "the pipe fluid capacity must be kept");
    }

    @Test
    void keepsTickHandler() throws IOException {
        assertNotNull(pipe().getTickHandler(), "pipe transport must be Lua-defined via onTick");
    }
}
