package com.luatweaker.text;

import com.google.gson.JsonObject;
import com.luatweaker.api.text.TextStyle;
import org.jetbrains.annotations.Nullable;

/** Writes the style block of a Minecraft JSON text component. */
final class TextJsonStyleWriter {

    private TextJsonStyleWriter() {
    }

    static void addStyle(JsonObject json, TextStyle style) {
        putIfNotNull(json, "color", style.color());
        putIfNotNull(json, "bold", style.bold());
        putIfNotNull(json, "italic", style.italic());
        putIfNotNull(json, "underlined", style.underline());
        putIfNotNull(json, "strikethrough", style.strikethrough());
        putIfNotNull(json, "obfuscated", style.obfuscated());
        putIfNotNull(json, "font", style.font());
        putIfNotNull(json, "insertion", style.insertion());
        if (style.click() != null) {
            addClick(json, style);
        }
        if (style.hover() != null) {
            addHover(json, style);
        }
    }

    private static void addClick(JsonObject json, TextStyle style) {
        JsonObject click = new JsonObject();
        click.addProperty("action", style.click().action());
        click.addProperty("value", style.click().value());
        json.add("clickEvent", click);
    }

    private static void addHover(JsonObject json, TextStyle style) {
        JsonObject hover = new JsonObject();
        hover.addProperty("action", "show_text");
        hover.add("contents", TextJsonWriter.tree(style.hover()));
        json.add("hoverEvent", hover);
    }

    private static void putIfNotNull(JsonObject json, String key, @Nullable String value) {
        if (value != null) {
            json.addProperty(key, value);
        }
    }

    private static void putIfNotNull(JsonObject json, String key, @Nullable Boolean value) {
        if (value != null) {
            json.addProperty(key, value);
        }
    }
}
