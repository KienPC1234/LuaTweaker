package com.luatweaker.platform.interaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
        Map<String, Object> result = new LinkedHashMap<>();
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

    private static Object toValue(Tag tag) {
        if (tag == null) return null;
        return switch (tag.getId()) {
            case Tag.TAG_BYTE -> ((net.minecraft.nbt.ByteTag) tag).getAsByte();
            case Tag.TAG_SHORT -> ((net.minecraft.nbt.ShortTag) tag).getAsShort();
            case Tag.TAG_INT -> ((net.minecraft.nbt.IntTag) tag).getAsInt();
            case Tag.TAG_LONG -> ((net.minecraft.nbt.LongTag) tag).getAsLong();
            case Tag.TAG_FLOAT -> ((net.minecraft.nbt.FloatTag) tag).getAsFloat();
            case Tag.TAG_DOUBLE -> ((net.minecraft.nbt.DoubleTag) tag).getAsDouble();
            case Tag.TAG_STRING -> ((net.minecraft.nbt.StringTag) tag).getAsString();
            case Tag.TAG_BYTE_ARRAY -> ((net.minecraft.nbt.ByteArrayTag) tag).getAsByteArray();
            case Tag.TAG_INT_ARRAY -> ((net.minecraft.nbt.IntArrayTag) tag).getAsIntArray();
            case Tag.TAG_LONG_ARRAY -> ((net.minecraft.nbt.LongArrayTag) tag).getAsLongArray();
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                List<Object> items = new ArrayList<>(list.size());
                for (int i = 0; i < list.size(); i++) {
                    items.add(toValue(list.get(i)));
                }
                yield items;
            }
            case Tag.TAG_COMPOUND -> toMap((CompoundTag) tag);
            default -> null;
        };
    }

    private static Tag fromValue(Object value) {
        if (value instanceof Tag tag) return tag;
        if (value instanceof String s) return net.minecraft.nbt.StringTag.valueOf(s);
        if (value instanceof Byte b) return net.minecraft.nbt.ByteTag.valueOf(b);
        if (value instanceof Short s) return net.minecraft.nbt.ShortTag.valueOf(s);
        if (value instanceof Integer i) return net.minecraft.nbt.IntTag.valueOf(i);
        if (value instanceof Long l) return net.minecraft.nbt.LongTag.valueOf(l);
        if (value instanceof Float f) return net.minecraft.nbt.FloatTag.valueOf(f);
        if (value instanceof Double d) return net.minecraft.nbt.DoubleTag.valueOf(d);
        if (value instanceof Boolean b) return net.minecraft.nbt.ByteTag.valueOf(b);
        if (value instanceof byte[] arr) return new net.minecraft.nbt.ByteArrayTag(arr);
        if (value instanceof int[] arr) return new net.minecraft.nbt.IntArrayTag(arr);
        if (value instanceof long[] arr) return new net.minecraft.nbt.LongArrayTag(arr);
        if (value instanceof List<?> list) {
            ListTag listTag = new ListTag();
            for (Object item : list) {
                Tag converted = fromValue(item);
                if (converted != null) {
                    listTag.add(converted);
                }
            }
            return listTag;
        }
        if (value instanceof Map<?, ?> map) {
            CompoundTag compound = new CompoundTag();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Tag converted = fromValue(entry.getValue());
                if (converted != null) {
                    compound.put(String.valueOf(entry.getKey()), converted);
                }
            }
            return compound;
        }
        return null;
    }
}
