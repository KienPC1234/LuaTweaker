package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.entity.MinecraftPlayerWrapper;
import com.luatweaker.common.util.CallbackFirewall;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Lua right-click hook plus {@code Dimensions:RegisterPortal} consumer for Lua blocks. */
final class BlockUseHandler {

    private static final String DEFAULT_NAMESPACE_PREFIX = "luatweaker:";

    private BlockUseHandler() {
    }

    /**
     * Returns {@code null} when the block has no handler (caller falls back to the block's
     * default behavior), otherwise the interaction result.
     *
     * <p>A registered portal mapping ({@code Dimensions:RegisterPortal}) takes precedence over
     * the Lua handler so the mapping is never dead: using the block teleports the player.
     * Server-side only.
     */
    @Nullable
    static InteractionResult handleUse(Level level, BlockPos pos, Player player, IBlockBuilder builder) {
        if (level.isClientSide()) {
            return null;
        }
        if (tryPortalTeleport(player, builder)) {
            return InteractionResult.sidedSuccess(false);
        }
        if (builder.getRightClickHandler() == null) {
            return null;
        }
        CallbackFirewall.run(() -> builder.getRightClickHandler().accept(new MinecraftPlayerWrapper(player),
                        new com.luatweaker.common.interaction.MinecraftInteractableBlock(
                                level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ())),
                LogStage.SYSTEM,
                () -> "Failed block right-click handler for " + builder.getId());
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * Engine-level portal consumer for {@code Dimensions:RegisterPortal}. Returns true when a
     * portal matched (click consumed), even if the teleport itself failed (failure is logged).
     *
     * <p>Limitation: container blocks bypass this path (CustomContainerBlock opens the GUI
     * instead), so portal sources should be plain blocks.
     */
    private static boolean tryPortalTeleport(@Nullable Player player, IBlockBuilder builder) {
        if (player == null || builder == null || builder.getId() == null) {
            return false;
        }
        Object serviceObject = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        if (!(serviceObject instanceof com.luatweaker.dimension.DimensionServiceImpl service)) {
            return false;
        }
        String rawId = builder.getId();
        String matched = matchPortal(service, rawId);
        if (matched == null) {
            return false;
        }
        CallbackFirewall.run(() -> service.tryUsePortal(new MinecraftPlayerWrapper(player), matched),
                LogStage.SYSTEM, () -> "Portal teleport failed for block '" + rawId + "'");
        return true;
    }

    /**
     * Builder ids are stored verbatim, while short ids without a namespace register under the
     * engine default {@code luatweaker:} namespace, so both forms are tried.
     */
    @Nullable
    private static String matchPortal(com.luatweaker.dimension.DimensionServiceImpl service, @NotNull String rawId) {
        for (String candidate : candidateIds(rawId)) {
            try {
                if (service.getPortalTarget(candidate) != null) {
                    return candidate;
                }
            } catch (IllegalArgumentException badId) {
                LuaTweakerLog.get().debug(LogStage.SYSTEM,
                        "Ignored unresolvable portal candidate " + candidate);
            }
        }
        return null;
    }

    @NotNull
    private static String[] candidateIds(@NotNull String rawId) {
        return rawId.startsWith(DEFAULT_NAMESPACE_PREFIX)
                ? new String[]{rawId, rawId.substring(DEFAULT_NAMESPACE_PREFIX.length())}
                : new String[]{rawId, rawId.contains(":") ? rawId : DEFAULT_NAMESPACE_PREFIX + rawId};
    }
}



