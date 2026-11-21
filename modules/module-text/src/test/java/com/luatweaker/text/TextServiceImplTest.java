package com.luatweaker.text;

import com.luatweaker.api.text.TextSpec;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the Lua text builder through a real engine and checks the produced TextSpec tree. */
class TextServiceImplTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(true);
        TextLuaBinding.registerBindings(engine, new TextServiceImpl());
    }

    @Test
    void literalAndPlainTextAreStable() {
        TextAssertions.assertLiteral(runAndRead("_G._t = Text:Literal('Hello')"));
    }

    @Test
    void styleReadsColorAndBooleans() {
        TextAssertions.assertStyle(runAndRead("""
                _G._t = Text:Style(Text:Literal('Hello'), {
                    Color = '#ff0000',
                    Bold = true,
                    Italic = false,
                })
                """));
    }

    @Test
    void clickAndHoverAreAttachedToTheStyle() {
        TextAssertions.assertClickHover(runAndRead("""
                local base = Text:Literal('Run')
                _G._t = Text:Hover(Text:Click(base, 'RUN_COMMAND', '/say hi'),
                        Text:Literal('Click me'))
                """));
    }

    @Test
    void concatInsertsTheSeparatorBetweenParts() {
        TextSpec spec = runAndRead("""
                _G._t = Text:Concat('-', { Text:Literal('a'), Text:Literal('b'), Text:Literal('c') })
                """);

        assertEquals("a-b-c", spec.plainText(), "the separator must appear between every part");
    }

    @Test
    void translatableKeepsTheKeyAndArguments() {
        TextAssertions.assertTranslation(runAndRead("""
                _G._t = Text:Translatable('item.example.name', { Text:Literal('Ruby'), 5 })
                """));
    }

    @Test
    void invalidClickActionFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow(
                        "Text:Click(Text:Literal('x'), 'BOGUS', 'value')", "TextBadClick"),
                "an unknown click action must raise through the binding");
    }

    @Test
    void invalidColorFailsLoudly() {
        assertThrows(IllegalStateException.class,
                () -> engine.executeStringOrThrow(
                        "Text:Style(Text:Literal('x'), { Color = 'red' })", "TextBadColor"),
                "a non-hex color must raise through the binding");
    }

    private TextSpec runAndRead(String script) {
        engine.executeStringOrThrow(script, "TextTest");
        ILuaValue value = engine.getGlobalEnvironment().rawget("_t");
        Object raw = value.toJavaObject();
        assertTrue(raw instanceof TextSpec, "the binding must return a TextSpec handle");
        return (TextSpec) raw;
    }
}
