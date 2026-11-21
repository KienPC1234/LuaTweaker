package com.luatweaker.api.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Style fields for one {@link TextSpec}. {@code null} booleans mean "inherit from the parent";
 * {@code color} is a {@code #RRGGBB} string or {@code null}.
 */
public record TextStyle(@Nullable String color,
                        @Nullable Boolean bold,
                        @Nullable Boolean italic,
                        @Nullable Boolean underline,
                        @Nullable Boolean strikethrough,
                        @Nullable Boolean obfuscated,
                        @Nullable String font,
                        @Nullable String insertion,
                        @Nullable ClickSpec click,
                        @Nullable TextSpec hover) {

    public static final TextStyle EMPTY =
            new TextStyle(null, null, null, null, null, null, null, null, null, null);

    public @NotNull TextStyle withColor(@Nullable String newColor) {
        return new TextStyle(newColor, bold, italic, underline, strikethrough, obfuscated,
                font, insertion, click, hover);
    }

    public @NotNull TextStyle withBold(@Nullable Boolean value) {
        return new TextStyle(color, value, italic, underline, strikethrough, obfuscated,
                font, insertion, click, hover);
    }

    public @NotNull TextStyle withItalic(@Nullable Boolean value) {
        return new TextStyle(color, bold, value, underline, strikethrough, obfuscated,
                font, insertion, click, hover);
    }

    public @NotNull TextStyle withUnderline(@Nullable Boolean value) {
        return new TextStyle(color, bold, italic, value, strikethrough, obfuscated,
                font, insertion, click, hover);
    }

    public @NotNull TextStyle withStrikethrough(@Nullable Boolean value) {
        return new TextStyle(color, bold, italic, underline, value, obfuscated,
                font, insertion, click, hover);
    }

    public @NotNull TextStyle withObfuscated(@Nullable Boolean value) {
        return new TextStyle(color, bold, italic, underline, strikethrough, value,
                font, insertion, click, hover);
    }

    public @NotNull TextStyle withFont(@Nullable String newFont) {
        return new TextStyle(color, bold, italic, underline, strikethrough, obfuscated,
                newFont, insertion, click, hover);
    }

    public @NotNull TextStyle withInsertion(@Nullable String newInsertion) {
        return new TextStyle(color, bold, italic, underline, strikethrough, obfuscated,
                font, newInsertion, click, hover);
    }

    public @NotNull TextStyle withClick(@Nullable ClickSpec newClick) {
        return new TextStyle(color, bold, italic, underline, strikethrough, obfuscated,
                font, insertion, newClick, hover);
    }

    public @NotNull TextStyle withHover(@Nullable TextSpec newHover) {
        return new TextStyle(color, bold, italic, underline, strikethrough, obfuscated,
                font, insertion, click, newHover);
    }
}
