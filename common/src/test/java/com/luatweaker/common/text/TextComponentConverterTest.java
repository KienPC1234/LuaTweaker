package com.luatweaker.common.text;

import com.luatweaker.api.text.ClickSpec;
import com.luatweaker.api.text.TextSpec;
import com.luatweaker.api.text.TextStyle;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Converts the pure text tree into real Minecraft components (runs with the MC classpath). */
class TextComponentConverterTest {

    @Test
    void literalStyleAndColorAreApplied() {
        TextSpec spec = TextSpec.literal("Hello").withStyle(
                new TextStyle("#FF0000", true, false, false, false, false, null, null, null, null));

        Component component = TextComponentConverter.toComponent(spec);

        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals("Hello", component.getString(), "the literal text must survive conversion"),
                () -> assertEquals(0xFF0000, component.getStyle().getColor().getValue(), "the RGB color must be applied"),
                () -> assertTrue(component.getStyle().isBold(), "bold must be applied")
        );
    }

    @Test
    void clickAndHoverBecomeRealEvents() {
        TextSpec tooltip = TextSpec.literal("tip");
        TextSpec spec = TextSpec.literal("Run").withStyle(
                TextStyle.EMPTY
                        .withClick(new ClickSpec("RUN_COMMAND", "/say hi"))
                        .withHover(tooltip));

        Component component = TextComponentConverter.toComponent(spec);

        ClickEvent click = component.getStyle().getClickEvent();
        HoverEvent hover = component.getStyle().getHoverEvent();
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals(ClickEvent.Action.RUN_COMMAND, click.getAction(), "the click action must be converted"),
                () -> assertEquals("/say hi", click.getValue(), "the click value must be converted"),
                () -> assertEquals(HoverEvent.Action.SHOW_TEXT, hover.getAction(), "the hover must become a show_text event"),
                () -> assertEquals("tip", hover.getValue(HoverEvent.Action.SHOW_TEXT).getString(), "the hover tooltip text must survive conversion")
        );
    }

    @Test
    void translatableUsesArguments() {
        TextSpec spec = TextSpec.translatable("item.example.name",
                List.of(TextSpec.literal("Ruby")));

        Component component = TextComponentConverter.toComponent(spec);

        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertInstanceOf(TranslatableContents.class, component.getContents(),
                        "a translatable node must produce TranslatableContents"),
                () -> assertEquals("item.example.name", ((TranslatableContents) component.getContents()).getKey(),
                        "the key must be kept"),
                () -> assertEquals(1, ((TranslatableContents) component.getContents()).getArgs().length,
                        "the translation argument must be kept")
        );
    }

    @Test
    void joinAppendsEveryChild() {
        TextSpec spec = TextSpec.join(List.of(
                TextSpec.literal("a"), TextSpec.literal("b"), TextSpec.literal("c")));

        Component component = TextComponentConverter.toComponent(spec);

        assertEquals("abc", component.getString(), "join must append every child in order");
    }

    @Test
    void invalidColorAndFontFailLoudly() {
        TextSpec badColor = TextSpec.literal("x").withStyle(
                new TextStyle("red", null, null, null, null, null, null, null, null, null));
        TextSpec badFont = TextSpec.literal("x").withStyle(
                new TextStyle(null, null, null, null, null, null, "not a resource id", null, null, null));
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> TextComponentConverter.toComponent(badColor),
                        "an invalid color must fail instead of silently rendering white"),
                () -> assertThrows(IllegalArgumentException.class, () -> TextComponentConverter.toComponent(badFont),
                        "an invalid font id must fail instead of silently using the default font")
        );
    }

    @Test
    void overlyDeepTreeIsRejected() {
        TextSpec node = TextSpec.literal("leaf");
        for (int i = 0; i <= TextComponentConverter.MAX_DEPTH; i++) {
            node = TextSpec.join(List.of(node));
        }
        TextSpec deep = node;

        assertThrows(IllegalArgumentException.class,
                () -> TextComponentConverter.toComponent(deep),
                "a tree deeper than the guard must be rejected");
    }
}
