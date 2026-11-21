package com.luatweaker.spawn;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Readers for id lists in spawn-rule tables (biome ids, block-below ids).
 * Entity ids must be plain resource locations; block lists additionally accept
 * block-tag references such as {@code #minecraft:dirt}.
 */
final class SpawnRuleListReaders {

    /** Minecraft resource-location charset (blocks, entities, biomes). */
    private static final Pattern RESOURCE_LOCATION =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    /** Block tag form, e.g. {@code #minecraft:dirt}. */
    private static final Pattern BLOCK_TAG =
            Pattern.compile("^#[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private SpawnRuleListReaders() {}

    static boolean isEntityId(@NotNull String id) {
        return RESOURCE_LOCATION.matcher(id).matches();
    }

    static boolean isBlockTag(@NotNull String id) {
        return BLOCK_TAG.matcher(id).matches();
    }

    @NotNull
    static List<String> stringList(@NotNull ILuaTable table, @NotNull String key,
                                   boolean allowTags, @NotNull String where) {
        ILuaValue value = table.rawget(key);
        if (value == null || value.isNil()) {
            return List.of();
        }
        if (!value.isTable()) {
            throw new IllegalArgumentException(where + "." + key + " must be an array of ids");
        }
        ILuaTable array = value.asTable();
        List<String> out = new ArrayList<>(array.length());
        for (int i = 1; i <= array.length(); i++) {
            out.add(readStringListItem(array, key, i, allowTags, where));
        }
        return List.copyOf(out);
    }

    @NotNull
    private static String readStringListItem(@NotNull ILuaTable array, @NotNull String key, int index,
                                             boolean allowTags, @NotNull String where) {
        ILuaValue entry = array.rawget(index);
        if (entry == null || entry.isNil() || !entry.isString()) {
            throw new IllegalArgumentException(where + "." + key + "[" + index + "] must be a string id");
        }
        String id = entry.asString();
        boolean valid = isEntityId(id) || (allowTags && isBlockTag(id));
        if (!valid) {
            throw new IllegalArgumentException(where + "." + key + "[" + index
                    + "] is not a valid id, got: " + id);
        }
        return id;
    }
}
