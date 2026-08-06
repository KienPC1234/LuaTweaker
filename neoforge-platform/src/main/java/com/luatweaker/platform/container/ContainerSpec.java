package com.luatweaker.platform.container;

import com.luatweaker.api.content.BooleanStateSpec;
import com.luatweaker.api.content.MachineBarSpec;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * All Lua-configured behaviour of a container block, bundled into one immutable
 * record: grid geometry, drop rules, per-slot customization, machine features
 * (FE energy, fluid tank, GUI bars), block-state variants (boolean state,
 * pipe connections) and the per-tick Lua behavior handler. The engine only
 * provides these primitives - the actual machine/pipe logic is Lua code
 * ({@code :OnTick}), never built into Java. Consumed by
 * {@link CustomContainerBlock} (server) and by the menu/screen registries (client).
 *
 * @param rows             container grid rows
 * @param cols             container grid columns
 * @param dropMode         "packed" | "spill" | "none"
 * @param texture          custom GUI panel texture (null = default)
 * @param useDistance      max distance (blocks) a player may stand to use it
 * @param slotPositions    per-slot position overrides (index -> {x, y})
 * @param lockedSlots      read-only slot indices
 * @param slotTexture      custom slot cell texture (null = default)
 * @param energyCapacity   FE buffer capacity (0 = no energy)
 * @param energyMaxReceive FE per tick accepted (0 = none)
 * @param energyMaxExtract FE per tick extractable (0 = none)
 * @param fluidCapacity    fluid tank capacity in mB (0 = no tank)
 * @param bars             GUI bar elements
 * @param booleanState     boolean block-state pair (e.g. running/off), null = none
 * @param connections      pipe connections (north/east/south/west/up/down)
 * @param tickHandler      Lua per-tick behavior (data map, null); only blocks with a
 *                         handler tick, so plain containers cost nothing
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
        BiConsumer<Object, Object> tickHandler) {

    public boolean hasEnergy() {
        return energyCapacity > 0;
    }

    public boolean hasFluid() {
        return fluidCapacity > 0;
    }
}
