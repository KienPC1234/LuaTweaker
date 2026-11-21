package com.luatweaker.platform.interaction;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Id/side parsing and tank-drain helpers behind {@link NeoForgeCapabilityPlatform}. */
final class CapabilitySupport {

    private CapabilitySupport() {
    }

    @Nullable
    static Direction parseSide(@Nullable String side) {
        if (side == null || side.isBlank()) {
            return null;
        }
        try {
            return Direction.valueOf(side.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Capability] unknown side '" + side + "'; using the unsided lookup");
            return null;
        }
    }

    @Nullable
    static Item parseItem(@NotNull String itemId) {
        ResourceLocation id = parseId(itemId, "item");
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "[Capability] unknown item '" + itemId + "'");
        }
        return item;
    }

    @Nullable
    static net.minecraft.world.level.material.Fluid parseFluid(@Nullable String fluidId) {
        ResourceLocation id = parseId(fluidId, "fluid");
        if (id == null) {
            return null;
        }
        net.minecraft.world.level.material.Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (fluid == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "[Capability] unknown fluid '" + fluidId + "'");
        }
        return fluid;
    }

    @Nullable
    private static ResourceLocation parseId(@Nullable String raw, @NotNull String kind) {
        if (raw == null || raw.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "[Capability] empty " + kind + " id");
            return null;
        }
        String trimmed = raw.trim();
        ResourceLocation id = trimmed.contains(":")
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.tryParse("minecraft:" + trimmed);
        if (id == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Capability] malformed " + kind + " id '" + raw + "'");
        }
        return id;
    }

    /** Drains the first tank holding {@code wanted} (any tank when {@code wanted} is null). */
    static int drainFirstMatching(@NotNull ServerLevel level, int x, int y, int z, @Nullable String side,
                                  @Nullable net.minecraft.world.level.material.Fluid wanted, int amount) {
        IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                new BlockPos(x, y, z), parseSide(side));
        if (handler == null) {
            return 0;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack current = handler.getFluidInTank(tank);
            if (!current.isEmpty() && (wanted == null || current.getFluid().isSame(wanted))) {
                return handler.drain(current.copyWithAmount(amount), IFluidHandler.FluidAction.EXECUTE).getAmount();
            }
        }
        return 0;
    }
}
