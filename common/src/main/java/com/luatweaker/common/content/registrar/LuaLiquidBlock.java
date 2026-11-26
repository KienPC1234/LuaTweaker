package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.common.entity.MinecraftPlayerWrapper;
import com.luatweaker.common.util.CallbackFirewall;
import dev.architectury.core.block.ArchitecturyLiquidBlock;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Custom {@link ArchitecturyLiquidBlock} that dispatches server-side {@code onTouch}
 * callbacks when a player enters the fluid block.
 */
final class LuaLiquidBlock extends ArchitecturyLiquidBlock {

    private final IFluidBuilder builder;

    LuaLiquidBlock(@NotNull RegistrySupplier<FlowingFluid> fluid,
                   @NotNull Properties properties,
                   @NotNull IFluidBuilder builder) {
        super(fluid, properties);
        this.builder = builder;
    }

    @NotNull
    static LiquidBlock create(@NotNull RegistrySupplier<FlowingFluid> fluid,
                              @NotNull IFluidBuilder builder) {
        return new LuaLiquidBlock(fluid,
                BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)
                        .lightLevel(state -> builder.getLightLevel()),
                builder);
    }

    @Override
    protected void entityInside(@NotNull BlockState state, @NotNull Level level,
                                @NotNull BlockPos pos, @NotNull Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!level.isClientSide() && entity instanceof Player player && builder.getTouchHandler() != null) {
            dispatchTouch(builder, false, new MinecraftPlayerWrapper(player));
        }
    }

    static void dispatchTouch(@NotNull IFluidBuilder builder, boolean clientSide, @Nullable IPlayer player) {
        if (clientSide || player == null) {
            return;
        }
        Consumer<IPlayer> handler = builder.getTouchHandler();
        if (handler != null) {
            CallbackFirewall.run(() -> handler.accept(player), LogStage.SYSTEM,
                    () -> "Failed to run fluid onTouch callback for " + builder.getId());
        }
    }
}
