package com.luatweaker.text;

import com.luatweaker.api.text.ClickSpec;
import com.luatweaker.api.text.TextStyle;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Reads the style table used by {@code Text:Style}. */
final class TextStyleReader {

    private static final String HEX_PREFIX = "#";
    private static final int HEX_LENGTH = 6;

    private TextStyleReader() {
    }

    static TextStyle read(ILuaTable table, TextStyle base) {
        TextStyle style = base;
        ILuaValue color = first(table, "Color", "color");
        if (present(color)) {
            style = style.withColor(normalizeColor(color));
        }
        style = style.withBold(readOptionalBoolean(table, style.bold(), "Bold", "bold"));
        style = style.withItalic(readOptionalBoolean(table, style.italic(), "Italic", "italic"));
        style = style.withUnderline(
                readOptionalBoolean(table, style.underline(), "Underline", "underline"));
        style = style.withStrikethrough(
                readOptionalBoolean(table, style.strikethrough(), "Strikethrough", "strikethrough"));
        style = style.withObfuscated(
                readOptionalBoolean(table, style.obfuscated(), "Obfuscated", "obfuscated"));
        style = style.withFont(readOptionalString(table, style.font(), "Font", "font"));
        style = style.withInsertion(
                readOptionalString(table, style.insertion(), "Insertion", "insertion"));
        ILuaValue click = first(table, "Click", "click");
        if (present(click)) {
            style = style.withClick(readClickTable(click));
        }
        ILuaValue hover = first(table, "Hover", "hover");
        if (present(hover)) {
            style = style.withHover(TextSpecReader.readValue(hover, "Text:Style Hover"));
        }
        return style;
    }

    private static ClickSpec readClickTable(ILuaValue value) {
        if (!value.isTable()) {
            throw new IllegalArgumentException("Text:Style Click must be a table { Action, Value }");
        }
        ILuaTable table = value.asTable();
        ILuaValue action = first(table, "Action", "action");
        ILuaValue clickValue = first(table, "Value", "value");
        if (!present(action) || !present(clickValue)) {
            throw new IllegalArgumentException("Text:Style Click needs Action and Value fields");
        }
        return new ClickSpec(action.asString(), clickValue.asString());
    }

    private static @Nullable String readOptionalString(ILuaTable table, @Nullable String current,
                                                       String upper, String lower) {
        ILuaValue value = first(table, upper, lower);
        if (!present(value)) {
            return current;
        }
        String text = value.asString();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Text:Style " + upper + " must not be blank");
        }
        return text;
    }

    private static @Nullable Boolean readOptionalBoolean(ILuaTable table, @Nullable Boolean current,
                                                         String upper, String lower) {
        ILuaValue value = first(table, upper, lower);
        if (!present(value)) {
            return current;
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("Text:Style " + upper + " must be a boolean");
        }
        return value.asBoolean();
    }

    private static String normalizeColor(ILuaValue value) {
        if (value.isNumber()) {
            int rgb = Math.clamp((int) value.asDouble(), 0, 0xFFFFFF);
            return String.format(Locale.ROOT, "#%06X", rgb);
        }
        String text = value.asString().trim();
        if (text.startsWith(HEX_PREFIX)) {
            text = text.substring(1);
        }
        if (text.length() != HEX_LENGTH || !text.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException(
                    "Text:Style Color must be '#RRGGBB' or a 24-bit integer (got '" + text + "')");
        }
        return HEX_PREFIX + text.toUpperCase(Locale.ROOT);
    }

    private static @Nullable ILuaValue first(ILuaTable table, String upper, String lower) {
        ILuaValue value = table.rawget(upper);
        if (value == null || value.isNil()) {
            value = table.rawget(lower);
        }
        return value;
    }

    private static boolean present(@Nullable ILuaValue value) {
        return value != null && !value.isNil();
    }
}
