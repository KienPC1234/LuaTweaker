package com.luatweaker.common.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.event.EventDispatcher;
import com.luatweaker.common.event.payload.PayloadEntities;
import com.luatweaker.common.event.payload.PayloadTables;
import com.luatweaker.core.bind.LuaBinder;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientChatEvent;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientRecipeUpdateEvent;
import dev.architectury.event.events.client.ClientSystemMessageEvent;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.architectury.event.events.common.InteractionEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Registers client-side Architectury event bridges into {@code Events}.
 */
final class ClientExtraEventForwarder {

    private static final String ITEM_TOOLTIP_EVENT = "ItemTooltip";
    private static final String SCREEN_INIT_PRE = "ScreenInitPre";
    private static final String SCREEN_INIT_POST = "ScreenInitPost";
    private static final String MESSAGE_KEY = "message";

    private ClientExtraEventForwarder() {
    }

    static void init() {
        registerClientChatEvents();
        registerClientSystemMessageEvent();
        registerClientPlayerEvents();
        registerClientGuiAndTooltip();
        registerClientLifecycleAndAirClick();
    }

    private static void registerClientChatEvents() {
        ClientChatEvent.SEND.register((message, component) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && EventDispatcher.hasListenersFor("ClientChatSend")) {
                ILuaTable payload = engine.createTable();
                PayloadTables.putBoth(payload, MESSAGE_KEY, engine.wrapString(message));
                if (!EventDispatcher.postCancellable("ClientChatSend", () -> payload)) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });

        ClientChatEvent.RECEIVED.register((type, message) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && message != null && EventDispatcher.hasListenersFor("ClientChatReceived")) {
                ILuaTable payload = engine.createTable();
                PayloadTables.putBoth(payload, MESSAGE_KEY, engine.wrapString(message.getString()));
                if (!EventDispatcher.postCancellable("ClientChatReceived", () -> payload)) {
                    return CompoundEventResult.interruptFalse(message);
                }
            }
            return CompoundEventResult.pass();
        });
    }

    private static void registerClientSystemMessageEvent() {
        ClientSystemMessageEvent.RECEIVED.register(message -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && message != null && EventDispatcher.hasListenersFor("ClientSystemMessage")) {
                ILuaTable payload = engine.createTable();
                PayloadTables.putBoth(payload, MESSAGE_KEY, engine.wrapString(message.getString()));
                if (!EventDispatcher.postCancellable("ClientSystemMessage", () -> payload)) {
                    return CompoundEventResult.interruptFalse(message);
                }
            }
            return CompoundEventResult.pass();
        });
    }

    private static void registerClientPlayerEvents() {
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null) {
                EventDispatcher.postLazy("ClientPlayerJoin", () -> {
                    ILuaTable table = engine.createTable();
                    PayloadEntities.putPlayer(engine, table, player);
                    return table;
                });
            }
        });

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null) {
                EventDispatcher.postLazy("ClientPlayerQuit", () -> {
                    ILuaTable table = engine.createTable();
                    PayloadEntities.putPlayer(engine, table, player);
                    return table;
                });
            }
        });

        ClientPlayerEvent.CLIENT_PLAYER_RESPAWN.register((oldPlayer, newPlayer) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && newPlayer != null) {
                EventDispatcher.postLazy("ClientPlayerRespawn", () -> {
                    ILuaTable table = engine.createTable();
                    PayloadEntities.putPlayer(engine, table, newPlayer);
                    return table;
                });
            }
        });
    }

    private static void registerClientGuiAndTooltip() {
        ClientTooltipEvent.ITEM.register((stack, lines, context, flag) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && stack != null && !stack.isEmpty()
                    && EventDispatcher.hasListenersFor(ITEM_TOOLTIP_EVENT)) {
                EventDispatcher.post(ITEM_TOOLTIP_EVENT, buildTooltipPayload(engine, stack, lines));
            }
        });

        ClientGuiEvent.INIT_PRE.register((screen, access) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && screen != null && EventDispatcher.hasListenersFor(SCREEN_INIT_PRE)) {
                boolean allowed = EventDispatcher.postCancellable(SCREEN_INIT_PRE,
                        () -> buildScreenPayload(engine, screen));
                if (!allowed) {
                    return EventResult.interruptFalse();
                }
            }
            return EventResult.pass();
        });

        ClientGuiEvent.INIT_POST.register((screen, access) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && screen != null && EventDispatcher.hasListenersFor(SCREEN_INIT_POST)) {
                EventDispatcher.postLazy(SCREEN_INIT_POST, () -> buildScreenPayload(engine, screen));
            }
        });
    }

    private static void registerClientLifecycleAndAirClick() {
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> EventDispatcher.post("ClientStarted", null));
        ClientLifecycleEvent.CLIENT_STOPPING.register(client -> EventDispatcher.post("ClientStopping", null));
        ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register(level -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && level != null) {
                EventDispatcher.postLazy("ClientLevelLoad", () -> {
                    ILuaTable table = engine.createTable();
                    PayloadTables.putBoth(table, "dimension",
                            engine.wrapString(level.dimension().location().toString()));
                    return table;
                });
            }
        });
        ClientRecipeUpdateEvent.EVENT.register(manager -> EventDispatcher.post("ClientRecipesUpdated", null));

        InteractionEvent.CLIENT_LEFT_CLICK_AIR.register((player, hand) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && EventDispatcher.hasListenersFor("ClientLeftClickAir")) {
                EventDispatcher.postLazy("ClientLeftClickAir", () -> {
                    ILuaTable table = engine.createTable();
                    PayloadEntities.putPlayer(engine, table, player);
                    PayloadEntities.putItem(engine, table, player.getItemInHand(hand));
                    PayloadTables.putBoth(table, "hand", engine.wrapString(hand.name()));
                    return table;
                });
            }
        });

        InteractionEvent.CLIENT_RIGHT_CLICK_AIR.register((player, hand) -> {
            ILuaEngine engine = LuaTweakerCommon.getActiveEngine();
            if (engine != null && player != null && EventDispatcher.hasListenersFor("ClientRightClickAir")) {
                EventDispatcher.postLazy("ClientRightClickAir", () -> {
                    ILuaTable table = engine.createTable();
                    PayloadEntities.putPlayer(engine, table, player);
                    PayloadEntities.putItem(engine, table, player.getItemInHand(hand));
                    PayloadTables.putBoth(table, "hand", engine.wrapString(hand.name()));
                    return table;
                });
            }
        });

        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES,
                (ResourceManagerReloadListener) rm -> EventDispatcher.post("ResourcePackReload", null),
                ResourceLocation.fromNamespaceAndPath(LuaTweakerCommon.MOD_ID, "client_reload_events"));
    }

    private static ILuaTable buildTooltipPayload(ILuaEngine engine, ItemStack stack, List<Component> lines) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putItem(engine, table, stack);
        ILuaTable lineList = engine.createTable();
        for (int i = 0; i < lines.size(); i++) {
            lineList.rawset(i + 1, engine.wrapString(lines.get(i).getString()));
        }
        PayloadTables.putBoth(table, "lines", lineList);
        ILuaFunction addLine = args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length > off && !args[off].isNil()) {
                lines.add(Component.literal(args[off].asString()));
            }
            return engine.nilValue();
        };
        ILuaFunction clearLines = args -> {
            lines.clear();
            return engine.nilValue();
        };
        PayloadTables.putBoth(table, "addLine", addLine);
        PayloadTables.putBoth(table, "clearLines", clearLines);
        return table;
    }

    private static ILuaTable buildScreenPayload(ILuaEngine engine, Screen screen) {
        ILuaTable table = engine.createTable();
        PayloadTables.putBoth(table, "screenClass", engine.wrapString(screen.getClass().getSimpleName()));
        PayloadTables.putBoth(table, "width", engine.wrapNumber(screen.width));
        PayloadTables.putBoth(table, "height", engine.wrapNumber(screen.height));
        return table;
    }
}
