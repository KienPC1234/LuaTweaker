package com.luatweaker.platform.event;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Player lifecycle, advancement and container payloads. */
final class PlayerPayloads {

    private static final String MENU_TYPE = "menuType";
    private static final String SLOTS = "slots";
    private static final String CONTAINER_ID = "containerId";

    private PlayerPayloads() {
    }

    static void register(@NotNull PayloadRegistry registry) {
        registry.add(PlayerEvent.PlayerLoggedInEvent.class, PlayerPayloads::buildJoin);
        registry.add(PlayerEvent.PlayerLoggedOutEvent.class, PlayerPayloads::buildLeave);
        registry.add(PlayerEvent.PlayerRespawnEvent.class, PlayerPayloads::buildRespawn);
        registry.add(AdvancementEvent.AdvancementEarnEvent.class, PlayerPayloads::buildAdvancement);
        registry.add(PlayerContainerEvent.Open.class,
                (engine, event) -> buildContainer(engine, event, "OPEN"));
        registry.add(PlayerContainerEvent.Close.class,
                (engine, event) -> buildContainer(engine, event, "CLOSE"));
    }

    private static ILuaTable buildJoin(ILuaEngine engine, PlayerEvent.PlayerLoggedInEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getEntity());
        return table;
    }

    private static ILuaTable buildLeave(ILuaEngine engine, PlayerEvent.PlayerLoggedOutEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getEntity());
        return table;
    }

    private static ILuaTable buildRespawn(ILuaEngine engine, PlayerEvent.PlayerRespawnEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getEntity());
        PayloadTables.putBoth(table, "isEndConquered", engine.wrapBoolean(event.isEndConquered()));
        return table;
    }

    private static ILuaTable buildAdvancement(ILuaEngine engine, AdvancementEvent.AdvancementEarnEvent event) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getEntity());
        PayloadTables.putBoth(table, "advancementId", engine.wrapString(event.getAdvancement().id().toString()));
        return table;
    }

    /**
     * Container open/close snapshot: enough for a script to rate-limit chest access
     * (chest-stealer detection) without any packet-level hook.
     */
    private static ILuaTable buildContainer(ILuaEngine engine, PlayerContainerEvent event, String phase) {
        ILuaTable table = PayloadTables.base(engine, event);
        PayloadEntities.putPlayer(engine, table, event.getEntity());
        AbstractContainerMenu menu = event.getContainer();
        PayloadTables.putBoth(table, MENU_TYPE, engine.wrapString(menuId(menu)));
        PayloadTables.putBoth(table, SLOTS, engine.wrapNumber(menu.slots.size()));
        PayloadTables.putBoth(table, CONTAINER_ID, engine.wrapNumber(menu.containerId));
        PayloadTables.putBoth(table, "phase", engine.wrapString(phase));
        PayloadEntities.putItem(engine, table, menu.getCarried());
        return table;
    }

    private static String menuId(@Nullable AbstractContainerMenu menu) {
        if (menu == null || menu.getType() == null) {
            return "";
        }
        ResourceLocation key = BuiltInRegistries.MENU.getKey(menu.getType());
        return key == null ? "" : key.toString();
    }
}
