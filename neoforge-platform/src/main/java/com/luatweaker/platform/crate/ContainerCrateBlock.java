package com.luatweaker.platform.crate;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Generic Lua-configured container block. The row/column count, display title
 * and drop behaviour all come from the {@code Content.NewBlock(...):Container(..)}
 * builder. Right-click opens the crate GUI (after the optional Lua handler);
 * breaking the block follows the configured drop mode:
 * <ul>
 *   <li>{@code packed}: only the crate item drops, contents packed into its NBT
 *       (place it again to restore everything)</li>
 *   <li>{@code spill}: contents drop like a vanilla chest</li>
 *   <li>{@code none}: nothing drops</li>
 * </ul>
 */
public class ContainerCrateBlock extends Block implements EntityBlock {

    public static final BooleanProperty OPENED = BlockStateProperties.OPEN;

    private final String crateId;
    private final String crateTitle;
    private final int rows;
    private final int cols;
    private final String dropMode;
    private final String texturePath;
    private final BiConsumer<Object, Object> rightClickHandler;
    private final java.util.function.BiFunction<Object, Object, Boolean> itemFilter;

    public ContainerCrateBlock(Properties properties, String crateId, String crateTitle,
                               int rows, int cols, String dropMode, @Nullable String texturePath,
                               @Nullable BiConsumer<Object, Object> rightClickHandler,
                               @Nullable java.util.function.BiFunction<Object, Object, Boolean> itemFilter) {
        super(properties);
        this.crateId = crateId;
        this.crateTitle = crateTitle;
        this.rows = rows;
        this.cols = cols;
        this.dropMode = dropMode;
        this.texturePath = texturePath;
        this.rightClickHandler = rightClickHandler;
        this.itemFilter = itemFilter;
        registerDefaultState(defaultBlockState().setValue(OPENED, false));
    }

    public String getCrateId() {
        return crateId;
    }

    public String getCrateTitle() {
        return crateTitle;
    }

    @Nullable
    public String getTexturePath() {
        return texturePath;
    }

    public int getSlotCount() {
        return rows * cols;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public String getDropMode() {
        return dropMode;
    }

    /** Lua-defined container rule: true = the item may enter a slot. */
    public boolean acceptsItem(net.minecraft.world.item.ItemStack stack) {
        if (itemFilter == null || stack == null || stack.isEmpty()) return true;
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        try {
            return Boolean.TRUE.equals(itemFilter.apply(itemId, stack.getCount()));
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM, "Crate itemFilter failed for " + crateId + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPENED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ContainerCrateBlockEntity(pos, state);
    }

    @Override
    @NotNull
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ContainerCrateBlockEntity crate) {
            if (rightClickHandler != null) {
                try {
                    rightClickHandler.accept(new NeoForgePlayerWrapper(player), null);
                } catch (Exception e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed crate right-click handler for " + crateId + ": " + e.getMessage());
                }
            }
            level.setBlock(pos, state.setValue(OPENED, !state.getValue(OPENED)), 3);
            player.openMenu(crate);
            final int fx = pos.getX();
            final int fy = pos.getY();
            final int fz = pos.getZ();
            CrateEvents.post("CrateOpened", engine -> {
                com.luatweaker.api.vm.ILuaTable payload = CrateEvents.basePayload(engine, this, fx, fy, fz);
                payload.rawset("Player", engine.wrapString(player.getName().getString()));
                return payload;
            });
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ContainerCrateBlockEntity crate) {
            switch (dropMode) {
                case "packed" -> {
                    ItemStack packed = new ItemStack(this);
                    crate.saveToItem(packed, level.registryAccess());
                    Block.popResource(level, pos, packed);
                }
                case "spill" -> {
                    for (int i = 0; i < crate.getContainerSize(); i++) {
                        ItemStack stack = crate.getItem(i);
                        if (!stack.isEmpty()) {
                            Block.popResource(level, pos, stack);
                        }
                    }
                }
                default -> {
                    // "none": crate is destroyed, contents vanish with it.
                }
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
}
