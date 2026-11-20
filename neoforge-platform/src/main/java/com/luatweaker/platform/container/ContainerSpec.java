package com.luatweaker.platform.container;

import com.luatweaker.api.content.BooleanStateSpec;
import com.luatweaker.api.content.BoxSpec;
import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.MachineBarSpec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * All Lua-configured behaviour of a container or machine block, bundled into one immutable
 * record: grid geometry, drop rules, per-slot customization, machine features
 * (FE energy, fluid tank, GUI bars), block-state variants (boolean state,
 * pipe connections), facing orientation, custom voxel boxes, BER render callback,
 * and the per-tick Lua behavior handler.
 */
public record ContainerSpec(
        int rows,
        int cols,
        String dropMode,
        String texture,
        double useDistance,
        Map<Integer, int[]> slotPositions,
        Set<Integer> lockedSlots,
        String slotTexture,
        int energyCapacity,
        int energyMaxReceive,
        int energyMaxExtract,
        int fluidCapacity,
        List<MachineBarSpec> bars,
        BooleanStateSpec booleanState,
        boolean connections,
        BiConsumer<Object, Object> tickHandler,
        boolean isContainer,
        String facing,
        List<BoxSpec> boxes,
        BiConsumer<Object, Object> renderHandler,
        com.luatweaker.api.content.GeoModelSpec geoModel) {

    /** Defaults for blocks that have a block entity but no container grid (pipes, simple machines). */
    private static final int DEFAULT_ROWS = 1;
    private static final int DEFAULT_COLS = 1;
    private static final String DEFAULT_DROP_MODE = "none";

    public ContainerSpec(
            int rows,
            int cols,
            String dropMode,
            String texture,
            double useDistance,
            Map<Integer, int[]> slotPositions,
            Set<Integer> lockedSlots,
            String slotTexture,
            int energyCapacity,
            int energyMaxReceive,
            int energyMaxExtract,
            int fluidCapacity,
            List<MachineBarSpec> bars,
            BooleanStateSpec booleanState,
            boolean connections,
            BiConsumer<Object, Object> tickHandler) {
        this(rows, cols, dropMode, texture, useDistance, slotPositions, lockedSlots, slotTexture,
                energyCapacity, energyMaxReceive, energyMaxExtract, fluidCapacity, bars, booleanState,
                connections, tickHandler, rows > 0, "NONE", List.of(), null, null);
    }

    public ContainerSpec(
            int rows,
            int cols,
            String dropMode,
            String texture,
            double useDistance,
            Map<Integer, int[]> slotPositions,
            Set<Integer> lockedSlots,
            String slotTexture,
            int energyCapacity,
            int energyMaxReceive,
            int energyMaxExtract,
            int fluidCapacity,
            List<MachineBarSpec> bars,
            BooleanStateSpec booleanState,
            boolean connections,
            BiConsumer<Object, Object> tickHandler,
            boolean isContainer,
            String facing,
            List<BoxSpec> boxes,
            BiConsumer<Object, Object> renderHandler) {
        this(rows, cols, dropMode, texture, useDistance, slotPositions, lockedSlots, slotTexture,
                energyCapacity, energyMaxReceive, energyMaxExtract, fluidCapacity, bars, booleanState,
                connections, tickHandler, isContainer, facing, boxes, renderHandler, null);
    }

    public boolean hasEnergy() {
        return energyCapacity > 0;
    }

    public boolean hasFluid() {
        return fluidCapacity > 0;
    }

    /**
     * Builds the spec from a Lua builder. Pipes ({@code :ConnectionState(true)}) and
     * machines that only declare storage still get the 1x1 / "none" defaults.
     */
    public static ContainerSpec from(IBlockBuilder builder) {
        return new ContainerSpec(
                builder.isContainer() ? builder.getContainerRows() : DEFAULT_ROWS,
                builder.isContainer() ? builder.getContainerCols() : DEFAULT_COLS,
                builder.isContainer() ? builder.getContainerDropMode() : DEFAULT_DROP_MODE,
                builder.getContainerTexture(),
                builder.getContainerUseDistance(),
                builder.getSlotPositions(), builder.getLockedSlots(), builder.getSlotTexture(),
                builder.getEnergyCapacity(), builder.getEnergyMaxReceive(), builder.getEnergyMaxExtract(),
                builder.getFluidCapacity(), builder.getGuiBars(), builder.getBooleanState(),
                builder.isConnectionState(), builder.getTickHandler(),
                builder.isContainer(), builder.getFacing(), builder.getBoxes(), builder.getRenderHandler(),
                builder.getGeoModel());
    }

    /**
     * Adopts the runtime parts of a freshly loaded builder after {@code /lt reload}.
     *
     * <p>Registry entries and block states are frozen once the game starts, so geometry,
     * block-state properties, facing and shape must keep their original values. Everything
     * the engine reads per call (tick handler, item filter, capacities, GUI layout, BER
     * handler) is replaced.</p>
     */
    public static ContainerSpec adoptRuntime(ContainerSpec previous, ContainerSpec refreshed) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(refreshed, "refreshed");
        return new ContainerSpec(
                previous.rows(), previous.cols(),
                refreshed.dropMode(), refreshed.texture(), refreshed.useDistance(),
                refreshed.slotPositions(), refreshed.lockedSlots(), refreshed.slotTexture(),
                refreshed.energyCapacity(), refreshed.energyMaxReceive(), refreshed.energyMaxExtract(),
                refreshed.fluidCapacity(), refreshed.bars(),
                previous.booleanState(), previous.connections(),
                refreshed.tickHandler(), previous.isContainer(),
                previous.facing(), previous.boxes(), refreshed.renderHandler(),
                refreshed.geoModel());
    }

    /** True when a reload tried to change something a live block cannot rebuild. */
    public boolean hasFrozenDifference(ContainerSpec refreshed) {
        return rows != refreshed.rows
                || cols != refreshed.cols
                || connections != refreshed.connections
                || isContainer != refreshed.isContainer
                || !Objects.equals(booleanState, refreshed.booleanState)
                || !Objects.equals(facing, refreshed.facing)
                || !Objects.equals(boxes, refreshed.boxes);
    }
}
