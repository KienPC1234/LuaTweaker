package com.luatweaker.common.interaction;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Component/NBT read-write helpers for {@link MinecraftInteractableItem}. */
final class ItemStackAccess {

    private ItemStackAccess() {
    }

    static void setNbt(@NotNull ItemStack stack, @NotNull String nbtJson) {
        if (stack.isEmpty()) {
            return;
        }
        try {
            CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(nbtJson);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.merge(tag));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "Failed to parse item NBT JSON: " + e.getMessage());
        }
    }

    @Nullable
    static Object getCustomData(@NotNull ItemStack stack, @NotNull String key) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(key)) {
            return null;
        }
        return toLuaValue(customData.copyTag().get(key));
    }

    static void setCustomData(@NotNull ItemStack stack, @NotNull String key, @Nullable Object value) {
        if (stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (value == null) {
                tag.remove(key);
            } else if (value instanceof Boolean bool) {
                tag.putBoolean(key, bool);
            } else if (value instanceof Number number) {
                tag.putDouble(key, number.doubleValue());
            } else {
                tag.putString(key, value.toString());
            }
        });
    }

    @Nullable
    static String getAttribute(@NotNull ItemStack stack, @NotNull String key) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(key)) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        if (tag.contains(key, Tag.TAG_STRING)) {
            return tag.getString(key);
        }
        return tag.get(key).getAsString();
    }

    static void setAttribute(@NotNull ItemStack stack, @NotNull String key, @NotNull String value) {
        if (stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(key, value));
    }

    @Nullable
    private static Object toLuaValue(@Nullable Tag value) {
        if (value instanceof NumericTag numeric) {
            if (value instanceof net.minecraft.nbt.ByteTag byteTag
                    && (byteTag.getAsByte() == 0 || byteTag.getAsByte() == 1)) {
                return byteTag.getAsByte() == 1;
            }
            return numeric.getAsDouble();
        }
        return value == null ? null : value.getAsString();
    }
}
