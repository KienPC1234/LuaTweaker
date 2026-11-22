package com.luatweaker.common.container;

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
    /** Custom slot cell texture per menu type (null = default slot.png). */
    public static final Map<MenuType<CustomContainerMenu>, String> CONTAINER_SLOT_TEXTURES = new ConcurrentHashMap<>();
    /** GUI bar elements per menu type (energy/fluid/progress), rendered on top of the panel. */
    public static final Map<MenuType<CustomContainerMenu>, java.util.List<com.luatweaker.api.content.MachineBarSpec>> CONTAINER_BARS = new ConcurrentHashMap<>();
    /** Interactive buttons per menu type. */
    public static final Map<MenuType<CustomContainerMenu>, java.util.List<com.luatweaker.api.content.MachineButtonSpec>> CONTAINER_BUTTONS = new ConcurrentHashMap<>();
    /** Text labels per menu type. */
    public static final Map<MenuType<CustomContainerMenu>, java.util.List<com.luatweaker.api.content.MachineLabelSpec>> CONTAINER_LABELS = new ConcurrentHashMap<>();
    /** Server-side button click callbacks per block. */
    public static final Map<net.minecraft.world.level.block.Block, java.util.function.BiConsumer<Object, String>> BUTTON_HANDLERS = new ConcurrentHashMap<>();
    /** Neighbor change callbacks per block. */
    public static final Map<net.minecraft.world.level.block.Block, java.util.function.BiConsumer<Object, Object>> NEIGHBOR_HANDLERS = new ConcurrentHashMap<>();
    /** Every block created from a Lua builder, keyed by registry id (reload rebinding). */
    public static final Map<ResourceLocation, net.minecraft.world.level.block.Block> CREATED_BLOCKS = new ConcurrentHashMap<>();
    /** Container blocks by registry id, used to rebind runtime behaviour after a reload. */
    public static final Map<ResourceLocation, CustomContainerBlock> BLOCKS = new ConcurrentHashMap<>();
    /** Container blocks by their block entity type (client BER resolves the live handler). */
    public static final Map<net.minecraft.world.level.block.entity.BlockEntityType<?>, CustomContainerBlock> BLOCK_BY_TYPE = new ConcurrentHashMap<>();
    /** Experience drops per block when broken: [min, max]. */
    public static final Map<net.minecraft.world.level.block.Block, int[]> EXP_DROPS = new ConcurrentHashMap<>();

    private CustomContainerRegistry() {}
}
