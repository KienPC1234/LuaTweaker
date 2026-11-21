package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.NotNull;

/** Block break/place payloads used by claim protection and tool scripts. */
final class LevelPayloads {

    private LevelPayloads() {
    }

    static void register(@NotNull PayloadRegistry registry) {
        registry.add(BlockEvent.BreakEvent.class, LevelPayloads::buildBreak);
        registry.add(BlockEvent.EntityPlaceEvent.class, LevelPayloads::buildPlace);
    }

    private static ILuaTable buildBreak(ILuaEngine engine, BlockEvent.BreakEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getPlayer());
        PayloadBlocks.putBlockAndPos(engine, table, event.getLevel(), event.getPos());
        return table;
    }

    private static ILuaTable buildPlace(ILuaEngine engine, BlockEvent.EntityPlaceEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        if (event.getEntity() != null) {
            PayloadEntities.putEntity(engine, table, "entity", "Entity", event.getEntity());
            if (event.getEntity() instanceof Player player) {
                PayloadEntities.putPlayer(engine, table, player);
            }
        }
        PayloadBlocks.putBlockAndPos(engine, table, event.getLevel(), event.getPos());
        return table;
    }
}
