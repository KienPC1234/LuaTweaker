package com.luatweaker.common.text;

import com.luatweaker.api.text.ClickSpec;
import com.luatweaker.api.text.TextSpec;
import com.luatweaker.api.text.TextStyle;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Converts the engine-neutral {@link TextSpec} tree into a Minecraft {@link Component}.
 *
 * <p>This is the only place that touches Minecraft's text classes, so the builder and the
 * Lua surface stay testable in a plain JVM.</p>
 */
public final class TextComponentConverter {

    /** Prevents a pathological Lua-built tree from overflowing the render thread stack. */
    static final int MAX_DEPTH = 64;
    private static final int HEX_COLOR_LENGTH = 6;

    private TextComponentConverter() {
    }

    public static @NotNull Component toComponent(@NotNull TextSpec spec) {
        return build(spec, 0);
    }

    private static MutableComponent build(TextSpec spec, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "Text tree is deeper than " + MAX_DEPTH + " nodes");
        }
        MutableComponent component = buildBase(spec, depth);
        component.withStyle(toStyle(spec.style(), depth));
        appendExtra(component, spec, depth);
        return component;
    }

    private static MutableComponent buildBase(TextSpec spec, int depth) {
        return switch (spec.kind()) {
            case TextSpec.KIND_TRANSLATABLE -> buildTranslatable(spec, depth);
            case TextSpec.KIND_KEYBIND -> Component.keybind(spec.text());
            case TextSpec.KIND_SCORE -> Component.score(spec.text(), requireObjective(spec));
            case TextSpec.KIND_JOIN -> Component.empty();
            default -> Component.literal(spec.text());
        };
    }

    private static MutableComponent buildTranslatable(TextSpec spec, int depth) {
        if (spec.children().isEmpty()) {
            return Component.translatable(spec.text());
        }
        Object[] args = new Object[spec.children().size()];
        for (int i = 0; i < args.length; i++) {
            args[i] = build(spec.children().get(i), depth + 1);
        }
        return Component.translatable(spec.text(), args);
    }

    private static void appendExtra(MutableComponent component, TextSpec spec, int depth) {
        if (TextSpec.KIND_TRANSLATABLE.equals(spec.kind())) {
            return;
        }
        for (TextSpec child : spec.children()) {
            component.append(build(child, depth + 1));
        }
    }

    private static String requireObjective(TextSpec spec) {
        if (spec.secondary() == null) {
            throw new IllegalArgumentException(
                    "Score text '" + spec.text() + "' is missing its objective");
        }
        return spec.secondary();
    }

    private static Style toStyle(TextStyle style, int depth) {
        Style result = Style.EMPTY;
        if (style.color() != null) {
            result = result.withColor(TextColor.fromRgb(parseColor(style.color())));
        }
        result = result.withBold(style.bold());
        result = result.withItalic(style.italic());
        result = result.withUnderlined(style.underline());
        result = result.withStrikethrough(style.strikethrough());
        result = result.withObfuscated(style.obfuscated());
        if (style.font() != null) {
            result = result.withFont(parseFont(style.font()));
        }
        if (style.insertion() != null) {
            result = result.withInsertion(style.insertion());
        }
        if (style.click() != null) {
            result = result.withClickEvent(toClick(style.click()));
        }
        if (style.hover() != null) {
            result = result.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, build(style.hover(), depth + 1)));
        }
        return result;
    }

    private static int parseColor(@NotNull String color) {
        String digits = color.startsWith("#") ? color.substring(1) : color;
        if (digits.length() != HEX_COLOR_LENGTH) {
            throw new IllegalArgumentException("Text color must be #RRGGBB, got '" + color + "'");
        }
        try {
            return Integer.parseInt(digits, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Text color is not hexadecimal: '" + color + "'", e);
        }
    }

    private static ResourceLocation parseFont(@NotNull String font) {
        ResourceLocation location = ResourceLocation.tryParse(font);
        if (location == null) {
            throw new IllegalArgumentException("Text font is not a valid resource id: '" + font + "'");
        }
        return location;
    }

    private static ClickEvent toClick(@NotNull ClickSpec click) {
        return new ClickEvent(ClickEvent.Action.valueOf(click.action()), click.value());
    }
}
