package com.luatweaker.api.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * One click action attached to a text component.
 *
 * @param action uppercase Minecraft {@code ClickEvent.Action} name
 * @param value  action payload (URL, command, clipboard text, page number, ...)
 */
public record ClickSpec(@NotNull String action, @NotNull String value) {

    private static final Set<String> SUPPORTED = Set.of(
            "OPEN_URL", "OPEN_FILE", "RUN_COMMAND", "SUGGEST_COMMAND",
            "COPY_TO_CLIPBOARD", "CHANGE_PAGE");

    public ClickSpec {
        action = action.toUpperCase(Locale.ROOT);
        if (!SUPPORTED.contains(action)) {
            throw new IllegalArgumentException(
                    "Unsupported text click action '" + action + "'; expected one of " + SUPPORTED);
        }
    }

    public static @Nullable ClickSpec from(@Nullable String action, @Nullable String value) {
        if (action == null || action.isBlank()) {
            return null;
        }
        if (value == null) {
            throw new IllegalArgumentException("Text click action '" + action + "' needs a value");
        }
        return new ClickSpec(action, value);
    }
}
