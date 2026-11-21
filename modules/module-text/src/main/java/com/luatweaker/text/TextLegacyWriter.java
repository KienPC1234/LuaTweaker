package com.luatweaker.text;

import com.luatweaker.api.text.TextSpec;
import com.luatweaker.api.text.TextStyle;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Legacy section-sign serializer for {@code Text:ToLegacy}. */
final class TextLegacyWriter {

    private TextLegacyWriter() {
    }

    static String write(TextSpec node) {
        StringBuilder builder = new StringBuilder();
        append(builder, node);
        return builder.append("\u00a7r").toString();
    }

    private static void append(StringBuilder builder, TextSpec node) {
        appendStyle(builder, node.style());
        builder.append(node.text());
        appendChildren(builder, node.children());
    }

    private static void appendChildren(StringBuilder builder, List<TextSpec> children) {
        for (TextSpec child : children) {
            append(builder, child);
        }
    }

    private static void appendStyle(StringBuilder builder, TextStyle style) {
        if (style.color() != null) {
            builder.append("\u00a7x");
            String hex = style.color().substring(1).toUpperCase(Locale.ROOT);
            for (int i = 0; i < hex.length(); i++) {
                builder.append('\u00a7').append(hex.charAt(i));
            }
        }
        appendFlag(builder, 'l', style.bold());
        appendFlag(builder, 'o', style.italic());
        appendFlag(builder, 'n', style.underline());
        appendFlag(builder, 'm', style.strikethrough());
        appendFlag(builder, 'k', style.obfuscated());
    }

    private static void appendFlag(StringBuilder builder, char code, @Nullable Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            builder.append('\u00a7').append(code);
        }
    }
}
