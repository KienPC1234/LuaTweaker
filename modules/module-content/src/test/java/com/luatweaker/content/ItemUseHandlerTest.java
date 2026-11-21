package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IItemBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Item use-handlers are stored for the platform to invoke. */
class ItemUseHandlerTest extends ContentFixture {

    private static final String WRENCH_CHUNK = "wrench_chunk";
    private static final String WRENCH_SCRIPT = """
            local Content = require("LuaTweaker.Content")
            local wrench = Content.NewItem("test_wrench")
                :MaxStackSize(1)
                :DisplayName("Test Wrench")
                :OnUseOnBlock(function(player, hit)
                    print("hit " .. hit.X .. "," .. hit.Y .. "," .. hit.Z .. " face=" .. hit.Face)
                    return true
                end)
                :Register()
        """;

    private IItemBuilder wrench() throws IOException {
        engine.executeScript(scriptFile(WRENCH_SCRIPT), WRENCH_CHUNK);
        return item("test_wrench");
    }

    @Test
    void keepsId() throws IOException {
        assertEquals("test_wrench", wrench().getId(), "the item id must be kept");
    }

    @Test
    void keepsUseOnBlockHandler() throws IOException {
        assertNotNull(wrench().getOnUseOnBlockHandler(), "onUseOnBlock handler must be stored");
    }
}
