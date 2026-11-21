package com.luatweaker.text;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Serialization tests for {@code Text:ToLegacy} and {@code Text:ToJson}. */
class TextSerializationTest {

    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(true);
        TextLuaBinding.registerBindings(engine, new TextServiceImpl());
    }

    @Test
    void legacySerializationCarriesColorAndFormatting() {
        TextAssertions.assertLegacy(runAndRead("""
                _G._s = Text:ToLegacy(Text:Style(Text:Literal('Hello'), {
                    Color = '#00FF00',
                    Bold = true,
                }))
                """));
    }

    @Test
    void jsonSerializationKeepsStructureAndStyle() {
        TextAssertions.assertJson(runAndRead("""
                _G._s = Text:ToJson(Text:Style(Text:Literal('Hello'), {
                    Color = '#FF0000',
                    Bold = true,
                }), true)
                """));
    }

    private String runAndRead(String script) {
        engine.executeStringOrThrow(script, "TextTest");
        return engine.getGlobalEnvironment().rawget("_s").asString();
    }
}
