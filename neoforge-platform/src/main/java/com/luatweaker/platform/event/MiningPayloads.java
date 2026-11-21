package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Mining payload: NeoForge fires {@code BreakSpeed} on every destroy-progress tick, which gives
 * scripts the real dig speed per block. Returning Lua {@code false} zeroes it (mining denied).
 */
final class MiningPayloads {

    private static final String SPEED = "speed";
    private static final String ORIGINAL_SPEED = "originalSpeed";
    private static final int MIN_ONE_ARG = 1;

    private MiningPayloads() {
    }

    static void register(@NotNull PayloadRegistry registry) {
        registry.add(PlayerEvent.BreakSpeed.class, MiningPayloads::buildBreakSpeed);
    }

    private static ILuaTable buildBreakSpeed(ILuaEngine engine, PlayerEvent.BreakSpeed event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getEntity());
        Optional<BlockPos> pos = event.getPosition();
        if (pos.isPresent()) {
            PayloadBlocks.putBlockAndPos(engine, table, event.getEntity().level(), pos.get());
        }
        PayloadTables.putBoth(table, SPEED, engine.wrapNumber(event.getNewSpeed()));
        PayloadTables.putBoth(table, ORIGINAL_SPEED, engine.wrapNumber(event.getOriginalSpeed()));
        table.rawset("setSpeed", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_ONE_ARG) {
                event.setNewSpeed((float) args[off].asDouble());
            }
            return engine.nilValue();
        });
        PayloadTables.putPascal(table, "setSpeed", table.rawget("setSpeed"));
        return table;
    }
}
