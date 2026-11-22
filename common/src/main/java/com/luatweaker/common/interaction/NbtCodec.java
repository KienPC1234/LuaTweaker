package com.luatweaker.common.interaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Minecraft NBT to/from plain nested maps (String/Object) so the Lua
 * layer can read and write arbitrary block-entity data without touching Java.
 */
public final class NbtCodec {

    private NbtCodec() {}

    @NotNull
    public static Map<String, Object> toMap(@NotNull CompoundTag tag) {
        var result = new LinkedHashMap<String, Object>();
        for (String key : tag.getAllKeys()) {
            result.put(key, toValue(tag.get(key)));
        }
        return result;
    }

    @NotNull
    public static CompoundTag fromMap(@NotNull Map<String, Object> data) {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Tag value = fromValue(entry.getValue());
            if (value != null) {
                tag.put(entry.getKey(), value);
            }
        }
        return tag;
    }

    @Nullable
    static Object toValue(@Nullable Tag tag) {
        if (tag == null) {
            return null;
        }
        if (tag instanceof CompoundTag compound) {
            return toMap(compound);
        }
        if (tag instanceof Collection<?> list) {
            return NbtValues.listValues(list);
        }
        return NbtValues.scalarValue(tag);
    }

    @Nullable
    static Tag fromValue(@Nullable Object value) {
        if (value instanceof Tag tag) {
            return tag;
        }
        if (value instanceof List<?> list) {
            return NbtValues.fromList(list);
        }
        if (value instanceof Map<?, ?> map) {
            return NbtValues.fromMapValue(map);
        }
        if (value instanceof byte[] || value instanceof int[] || value instanceof long[]) {
            return NbtValues.fromArray(value);
        }
        return NbtValues.fromScalar(value);
    }
}

