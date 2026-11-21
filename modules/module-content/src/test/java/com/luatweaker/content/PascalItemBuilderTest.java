package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IItemBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** PascalCase builders behave exactly like their colon-call counterparts. */
class PascalItemBuilderTest extends ContentFixture {

    private static final String PASCAL_CHUNK = "pascal_chunk";
    private static final String PASCAL_SCRIPT = """
            local Content = require("LuaTweaker.Content")
            local ruby = Content.NewItem("pascal_ruby")
                :MaxStackSize(64)
                :Rarity("EPIC")
                :BurnTime(400)
                :DisplayName("Pascal Ruby Gem")
                :CreativeTab("ruby_tab")
                :Register()
        """;

    private IItemBuilder rubyItem() throws IOException {
        engine.executeScript(scriptFile(PASCAL_SCRIPT), PASCAL_CHUNK);
        return item("pascal_ruby");
    }

    @Test
    void oneItemRegistered() throws IOException {
        rubyItem();

        assertEquals(1, contentService.getRegisteredItems().size(),
                "exactly one item must be registered");
    }

    @Test
    void keepsId() throws IOException {
        assertEquals("pascal_ruby", rubyItem().getId(), "the item id must be kept");
    }

    @Test
    void keepsMaxStackSize() throws IOException {
        assertEquals(64, rubyItem().getMaxStackSize(), "the PascalCase stack size must be kept");
    }

    @Test
    void keepsRarity() throws IOException {
        assertEquals("EPIC", rubyItem().getRarity(), "the PascalCase rarity must be kept");
    }

    @Test
    void keepsBurnTime() throws IOException {
        assertEquals(400, rubyItem().getBurnTime(), "the PascalCase burn time must be kept");
    }

    @Test
    void keepsDisplayName() throws IOException {
        assertEquals("Pascal Ruby Gem", rubyItem().getDisplayName(),
                "the PascalCase display name must be kept");
    }
}
