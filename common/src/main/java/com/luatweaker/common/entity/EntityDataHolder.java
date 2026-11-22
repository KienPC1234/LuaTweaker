package com.luatweaker.common.entity;

import net.minecraft.nbt.CompoundTag;

/**
 * Accessor interface for entity persistent custom data, implemented by mixin.
 */
@FunctionalInterface
public interface EntityDataHolder {
    CompoundTag getPersistentData();
}
