package com.luatweaker.platform.container;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry of Lua-configured container menu types and block entity types, keyed by block. */
public final class CustomContainerRegistry {

    public static final Map<ResourceLocation, MenuType<CustomContainerMenu>> CONTAINER_MENUS = new ConcurrentHashMap<>();
    public static final Map<CustomContainerBlock, net.minecraft.world.level.block.entity.BlockEntityType<CustomContainerBlockEntity>> TYPE_BY_BLOCK = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, net.minecraft.world.level.block.entity.BlockEntityType<CustomContainerBlockEntity>> CONTAINER_BE_TYPES = new ConcurrentHashMap<>();
    /** Custom GUI background texture per menu type (null = default container panel). */
    public static final Map<MenuType<CustomContainerMenu>, String> CONTAINER_TEXTURES = new ConcurrentHashMap<>();

    private CustomContainerRegistry() {}
}
