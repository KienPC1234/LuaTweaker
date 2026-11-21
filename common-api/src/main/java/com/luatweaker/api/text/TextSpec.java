package com.luatweaker.api.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Engine-neutral description of a Minecraft text component tree.
 *
 * <p>Lua builds this tree through {@code ITextService}; the platform converts it to a real
 * {@code Component}. Keeping the tree here (instead of handing Minecraft objects to Lua)
 * lets the text API stay testable without a game runtime.</p>
 *
 * @param kind      one of {@link #KIND_LITERAL}, {@link #KIND_TRANSLATABLE}, {@link #KIND_KEYBIND},
 *                  {@link #KIND_SCORE}, {@link #KIND_JOIN}
 * @param text      literal text, translation key, keybind name, or score holder name
 * @param secondary score objective (only for {@link #KIND_SCORE}); {@code null} otherwise
 * @param children  child components; translation arguments for translatable nodes
 * @param style     style applied to this node (never {@code null})
 */
public record TextSpec(@NotNull String kind, @NotNull String text, @Nullable String secondary,
                       @NotNull List<TextSpec> children, @NotNull TextStyle style) {

    public static final String KIND_LITERAL = "literal";
    public static final String KIND_TRANSLATABLE = "translatable";
    public static final String KIND_KEYBIND = "keybind";
    public static final String KIND_SCORE = "score";
    public static final String KIND_JOIN = "join";

    public TextSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        children = children == null ? List.of() : List.copyOf(children);
        style = style == null ? TextStyle.EMPTY : style;
    }

    public static @NotNull TextSpec literal(@NotNull String text) {
        return new TextSpec(KIND_LITERAL, text, null, List.of(), TextStyle.EMPTY);
    }

    public static @NotNull TextSpec translatable(@NotNull String key, @NotNull List<TextSpec> args) {
        return new TextSpec(KIND_TRANSLATABLE, key, null, args, TextStyle.EMPTY);
    }

    public static @NotNull TextSpec keybind(@NotNull String key) {
        return new TextSpec(KIND_KEYBIND, key, null, List.of(), TextStyle.EMPTY);
    }

    public static @NotNull TextSpec score(@NotNull String name, @NotNull String objective) {
        return new TextSpec(KIND_SCORE, name, objective, List.of(), TextStyle.EMPTY);
    }

    public static @NotNull TextSpec join(@NotNull List<TextSpec> children) {
        return new TextSpec(KIND_JOIN, "", null, children, TextStyle.EMPTY);
    }

    public @NotNull TextSpec withStyle(@NotNull TextStyle newStyle) {
        return new TextSpec(kind, text, secondary, children, newStyle);
    }

    public @NotNull TextSpec withChildren(@NotNull List<TextSpec> newChildren) {
        return new TextSpec(kind, text, secondary, newChildren, style);
    }

    /** Concatenates the literal text of this node and its children (translations keep their key). */
    public @NotNull String plainText() {
        StringBuilder builder = new StringBuilder(text);
        for (TextSpec child : children) {
            builder.append(child.plainText());
        }
        return builder.toString();
    }
}
