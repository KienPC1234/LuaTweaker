package com.luatweaker.text;

import com.luatweaker.api.text.ClickSpec;
import com.luatweaker.api.text.ITextService;
import com.luatweaker.api.text.TextSpec;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure implementation of {@link ITextService}: builds the engine-neutral tree, serializes it,
 * and validates every style/action value at the boundary.
 */
public final class TextServiceImpl implements ITextService {

    @Override
    public @NotNull TextSpec literal(@NotNull String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text:Literal requires a string");
        }
        return TextSpec.literal(text);
    }

    @Override
    public @NotNull TextSpec translatable(@NotNull String key, @Nullable ILuaTable args) {
        TextGuards.requireKey(key, "translation key");
        return TextSpec.translatable(key, TextSpecReader.readList(args, "Text:Translate args"));
    }

    @Override
    public @NotNull TextSpec keybind(@NotNull String key) {
        TextGuards.requireKey(key, "keybind name");
        return TextSpec.keybind(key);
    }

    @Override
    public @NotNull TextSpec score(@NotNull String name, @NotNull String objective) {
        TextGuards.requireKey(name, "score holder name");
        TextGuards.requireKey(objective, "score objective");
        return TextSpec.score(name, objective);
    }

    @Override
    public @NotNull TextSpec concat(@NotNull String separator, @NotNull ILuaTable parts) {
        if (separator == null) {
            throw new IllegalArgumentException("Text:Concat requires a separator string");
        }
        List<TextSpec> values = TextSpecReader.readList(parts, "Text:Concat parts");
        List<TextSpec> result = new ArrayList<>(Math.max(0, values.size() * 2 - 1));
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.add(TextSpec.literal(separator));
            }
            result.add(values.get(i));
        }
        return TextSpec.join(result);
    }

    @Override
    public @NotNull TextSpec style(@NotNull TextSpec node, @NotNull ILuaTable style) {
        TextGuards.requireNode(node);
        if (style == null) {
            throw new IllegalArgumentException("Text:Style requires a style table");
        }
        return node.withStyle(TextStyleReader.read(style, node.style()));
    }

    @Override
    public @NotNull TextSpec click(@NotNull TextSpec node, @NotNull String action,
                                   @NotNull String value) {
        TextGuards.requireNode(node);
        return node.withStyle(node.style().withClick(ClickSpec.from(action, value)));
    }

    @Override
    public @NotNull TextSpec hover(@NotNull TextSpec node, @NotNull TextSpec tooltip) {
        TextGuards.requireNode(node);
        if (tooltip == null) {
            throw new IllegalArgumentException("Text:Hover requires a tooltip TextSpec");
        }
        return node.withStyle(node.style().withHover(tooltip));
    }

    @Override
    public @NotNull String toLegacy(@NotNull TextSpec node) {
        TextGuards.requireNode(node);
        return TextLegacyWriter.write(node);
    }

    @Override
    public @NotNull String toJson(@NotNull TextSpec node, boolean pretty) {
        TextGuards.requireNode(node);
        return TextJsonWriter.write(node, pretty);
    }
}
