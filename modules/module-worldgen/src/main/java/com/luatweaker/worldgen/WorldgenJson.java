package com.luatweaker.worldgen;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * Small JSON text helpers shared by the feature/modifier builders. The datapack emits
 * hand-built JSON (validated by the platform tests) instead of pulling a full serializer
 * into the hot registration path.
 */
final class WorldgenJson {

    private WorldgenJson() {}

    static @NotNull String escapeJson(@NotNull String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static @NotNull String sanitizeFileId(@NotNull String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    static void appendStringList(@NotNull StringBuilder target, @NotNull List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                target.append(',');
            }
            target.append('"').append(escapeJson(values.get(i))).append('"');
        }
    }

    static @NotNull String biomeSelector(@NotNull List<String> biomes) {
        StringBuilder list = new StringBuilder("[");
        appendStringList(list, biomes);
        return list.append(']').toString();
    }
}
