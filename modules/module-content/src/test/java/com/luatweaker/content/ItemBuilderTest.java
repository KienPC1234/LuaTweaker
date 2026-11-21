package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IItemBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Lua-defined items keep every builder property. */
class ItemBuilderTest extends ContentFixture {

    private static final String ITEM_CHUNK = "item_chunk";
    private static final String ITEM_SCRIPT = """
            startup:createItem("custom_ruby", function(item)
                item:maxStackSize(16)
                    :rarity("EPIC")
                    :burnTime(400)
                    :displayName("Enchanted Ruby Gem")
            end)
        """;

    private IItemBuilder rubyItem() throws IOException {
        engine.executeScript(scriptFile(ITEM_SCRIPT), ITEM_CHUNK);
        return item("custom_ruby");
    }

    @Test
    void oneItemRegistered() throws IOException {
        rubyItem();

        assertEquals(1, contentService.getRegisteredItems().size(),
                "exactly one item must be registered");
    }

    @Test
    void keepsId() throws IOException {
        assertEquals("custom_ruby", rubyItem().getId(), "the item id must be kept");
    }

    @Test
    void keepsMaxStackSize() throws IOException {
        assertEquals(16, rubyItem().getMaxStackSize(), "the stack size must be kept");
    }

    @Test
    void keepsRarity() throws IOException {
        assertEquals("EPIC", rubyItem().getRarity(), "the rarity must be kept");
    }

    @Test
    void keepsBurnTime() throws IOException {
        assertEquals(400, rubyItem().getBurnTime(), "the burn time must be kept");
    }

    @Test
    void keepsDisplayName() throws IOException {
        assertEquals("Enchanted Ruby Gem", rubyItem().getDisplayName(),
                "the display name must be kept");
    }
}
