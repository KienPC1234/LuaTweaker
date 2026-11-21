package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IBlockBuilder;

/**
 * Shared reactor arrange for the custom-bar tests.
 */
class ReactorFixture extends ContentFixture {

    private static final String REACTOR_CHUNK = "reactor_chunk";
    private static final String REACTOR_SCRIPT = """
            local startup = require("LuaTweaker.Startup")
            startup:createBlock("fusion_reactor", function(block)
                block:container(3, 3)
                     :customBar("heat_gauge", 10, 20, 16, 50, "CoreHeat", "MaxHeat", 0xFF4400, "Core: %d / %d C")
            end)
        """;

    IBlockBuilder reactor() throws IOException {
        engine.executeScript(scriptFile(REACTOR_SCRIPT), REACTOR_CHUNK);
        return block("fusion_reactor");
    }
}
