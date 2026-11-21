package com.luatweaker.platform.interaction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Scalar and list conversions used by {@link NbtCodec}. */
final class NbtValues {

    private NbtValues() {
    }

    @Nullable
    static Object scalarValue(@NotNull Tag tag) {
        int id = tag.getId();
        if (id >= Tag.TAG_BYTE && id <= Tag.TAG_DOUBLE) {
            return numericValue(tag, id);
        }
        if (id == Tag.TAG_STRING) {
            return ((net.minecraft.nbt.StringTag) tag).getAsString();
        }
        if (id == Tag.TAG_BYTE_ARRAY) {
            return ((net.minecraft.nbt.ByteArrayTag) tag).getAsByteArray();
        }
        if (id == Tag.TAG_INT_ARRAY) {
            return ((net.minecraft.nbt.IntArrayTag) tag).getAsIntArray();
        }
        if (id == Tag.TAG_LONG_ARRAY) {
            return ((net.minecraft.nbt.LongArrayTag) tag).getAsLongArray();
        }
        return null;
    }

    @Nullable
    private static Object numericValue(@NotNull Tag tag, int id) {
        return switch (id) {
            case Tag.TAG_BYTE -> ((net.minecraft.nbt.ByteTag) tag).getAsByte();
            case Tag.TAG_SHORT -> ((net.minecraft.nbt.ShortTag) tag).getAsShort();
            case Tag.TAG_INT -> ((net.minecraft.nbt.IntTag) tag).getAsInt();
            case Tag.TAG_LONG -> ((net.minecraft.nbt.LongTag) tag).getAsLong();
            case Tag.TAG_FLOAT -> ((net.minecraft.nbt.FloatTag) tag).getAsFloat();
            case Tag.TAG_DOUBLE -> ((net.minecraft.nbt.DoubleTag) tag).getAsDouble();
            default -> null;
        };
    }

    @NotNull
    static List<Object> listValues(@NotNull Collection<?> list) {
        List<Object> items = new ArrayList<>(list.size());
        for (Object item : list) {
            items.add(NbtCodec.toValue((Tag) item));
        }
        return items;
    }

    @Nullable
    static Tag fromScalar(@Nullable Object value) {
        if (value instanceof String s) {
            return net.minecraft.nbt.StringTag.valueOf(s);
        }
        if (value instanceof Boolean b) {
            return net.minecraft.nbt.ByteTag.valueOf(b);
        }
        if (value instanceof Byte b) {
            return net.minecraft.nbt.ByteTag.valueOf(b);
        }
        if (value instanceof Short s) {
            return net.minecraft.nbt.ShortTag.valueOf(s);
        }
        if (value instanceof Integer i) {
            return net.minecraft.nbt.IntTag.valueOf(i);
        }
        if (value instanceof Long l) {
            return net.minecraft.nbt.LongTag.valueOf(l);
        }
        if (value instanceof Float f) {
            return net.minecraft.nbt.FloatTag.valueOf(f);
        }
        if (value instanceof Double d) {
            return net.minecraft.nbt.DoubleTag.valueOf(d);
        }
        return null;
    }

    @Nullable
    static Tag fromArray(@NotNull Object value) {
        if (value instanceof byte[] arr) {
            return new net.minecraft.nbt.ByteArrayTag(arr);
        }
        if (value instanceof int[] arr) {
            return new net.minecraft.nbt.IntArrayTag(arr);
        }
        if (value instanceof long[] arr) {
            return new net.minecraft.nbt.LongArrayTag(arr);
        }
        return null;
    }

    @NotNull
    static Tag fromList(@NotNull List<?> list) {
        var listTag = new ListTag();
        for (Object item : list) {
            Tag converted = NbtCodec.fromValue(item);
            if (converted != null) {
                listTag.add(converted);
            }
        }
        return listTag;
    }

    @NotNull
    static Tag fromMapValue(@NotNull Map<?, ?> map) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Tag converted = NbtCodec.fromValue(entry.getValue());
            if (converted != null) {
                compound.put(String.valueOf(entry.getKey()), converted);
            }
        }
        return compound;
    }
}

