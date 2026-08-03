package com.luatweaker.platform.crate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry of Lua-configured crate menu types and block entity types, keyed by block. */
public final class ContainerCrateRegistry {

    public static final Map<ResourceLocation, MenuType<ContainerCrateMenu>> CRATE_MENUS = new ConcurrentHashMap<>();
    public static final Map<ContainerCrateBlock, net.minecraft.world.level.block.entity.BlockEntityType<ContainerCrateBlockEntity>> TYPE_BY_BLOCK = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, net.minecraft.world.level.block.entity.BlockEntityType<ContainerCrateBlockEntity>> CRATE_BE_TYPES = new ConcurrentHashMap<>();
    /** Custom GUI background texture per menu type (null = default crate panel). */
    public static final Map<MenuType<ContainerCrateMenu>, String> CRATE_TEXTURES = new ConcurrentHashMap<>();

    private ContainerCrateRegistry() {}
}
