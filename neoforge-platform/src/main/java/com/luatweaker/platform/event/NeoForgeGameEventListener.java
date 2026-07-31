package com.luatweaker.platform.event;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.LuaTweakerMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class NeoForgeGameEventListener {

    private void fireEvent(String eventName, java.util.function.Consumer<ILuaTable> payloadBuilder) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) return;
        IEventService eventService = (IEventService) LuaServiceRegistry.get("Events");
        if (eventService != null) {
            ILuaTable payload = engine.createTable();
            payloadBuilder.accept(payload);
            eventService.fireEvent(eventName, payload);
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        fireEvent("player.join", payload -> {
            payload.rawset("username", player.getName().getString());
            payload.rawset("uuid", player.getUUID().toString());
            payload.rawset("x", player.getX());
            payload.rawset("y", player.getY());
            payload.rawset("z", player.getZ());
        });
        fireEvent("player.login", payload -> {
            payload.rawset("username", player.getName().getString());
            payload.rawset("uuid", player.getUUID().toString());
            payload.rawset("x", player.getX());
            payload.rawset("y", player.getY());
            payload.rawset("z", player.getZ());
        });
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        fireEvent("player.leave", payload -> {
            payload.rawset("username", player.getName().getString());
            payload.rawset("uuid", player.getUUID().toString());
        });
        fireEvent("player.logout", payload -> {
            payload.rawset("username", player.getName().getString());
            payload.rawset("uuid", player.getUUID().toString());
        });
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        Player player = event.getPlayer();
        fireEvent("player.chat", payload -> {
            payload.rawset("sender", player.getName().getString());
            payload.rawset("message", event.getMessage().getString());
            payload.rawset("rawText", event.getRawText());
        });
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        fireEvent("block.break", payload -> {
            payload.rawset("player", player != null ? player.getName().getString() : "Unknown");
            payload.rawset("block", event.getState().getBlock().toString());
            payload.rawset("x", event.getPos().getX());
            payload.rawset("y", event.getPos().getY());
            payload.rawset("z", event.getPos().getZ());
            int exp = 0;
            try {
                for (java.lang.reflect.Method m : event.getClass().getMethods()) {
                    if (m.getName().toLowerCase().contains("exp") && m.getParameterCount() == 0) {
                        Object res = m.invoke(event);
                        if (res instanceof Number num) {
                            exp = num.intValue();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
            payload.rawset("exp", exp);
        });
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        fireEvent("block.place", payload -> {
            payload.rawset("block", event.getState().getBlock().toString());
            payload.rawset("x", event.getPos().getX());
            payload.rawset("y", event.getPos().getY());
            payload.rawset("z", event.getPos().getZ());
            payload.rawset("entity", entity != null ? entity.getName().getString() : "Unknown");
        });
    }

    @SubscribeEvent
    public void onEntitySpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        fireEvent("entity.spawn", payload -> {
            payload.rawset("entityId", entity.getId());
            payload.rawset("x", entity.getX());
            payload.rawset("y", entity.getY());
            payload.rawset("z", entity.getZ());
            ILuaEngine engine = LuaTweakerMod.getActiveEngine();
            if (engine != null) {
                payload.rawset("rawEntity", engine.wrapUserdata(entity));
            }
        });

        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine != null && entity != null) {
            Object serviceObj = LuaServiceRegistry.get("EntityService");
            if (serviceObj instanceof ILuaTable entityService) {
                ILuaValue signal = entityService.rawget("EntitySpawned");
                if (signal != null && !signal.isNil()) {
                    ILuaValue fireFn = signal.asTable().rawget("Fire");
                    if (fireFn != null && !fireFn.isNil()) {
                        ILuaValue wrapped = com.luatweaker.interaction.InteractionLuaBinding.getWrappedEntity(engine, entity);
                        if (wrapped != null && !wrapped.isNil()) {
                            try {
                                engine.callFunction(fireFn, signal, wrapped);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine != null) {
            ILuaValue uisValue = engine.getGlobalEnvironment().rawget("UserInputService");
            if (uisValue != null && !uisValue.isNil()) {
                ILuaTable uis = uisValue.asTable();
                String signalName = (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) ? "InputBegan" :
                                   (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_RELEASE) ? "InputEnded" : null;
                if (signalName != null) {
                    ILuaValue signal = uis.rawget(signalName);
                    if (signal != null && !signal.isNil()) {
                        ILuaValue fireFn = signal.asTable().rawget("Fire");
                        if (fireFn != null && !fireFn.isNil()) {
                            try {
                                engine.callFunction(fireFn, signal, engine.wrapNumber(event.getKey()), engine.wrapBoolean(false));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) return;
        ILuaValue task = engine.getGlobalEnvironment().rawget("task");
        if (task != null && !task.isNil() && task.isTable()) {
            ILuaValue tickFn = task.asTable().rawget("_tick");
            if (tickFn != null && !tickFn.isNil() && tickFn.isFunction()) {
                try {
                    engine.callFunction(tickFn);
                } catch (Exception e) {
                    com.luatweaker.api.log.LuaTweakerLog.get().error(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[Task Tick Error] Exception running task._tick: " + e.getMessage()
                    );
                }
            }
        }
    }
}
