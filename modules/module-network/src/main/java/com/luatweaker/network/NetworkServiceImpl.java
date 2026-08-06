package com.luatweaker.network;

import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkServiceImpl implements IRocketNetworkService {
    private final ILuaEngine engine;
    private final Map<String, ILuaTable> remoteEvents = new ConcurrentHashMap<>();
    private final Map<String, ILuaTable> remoteFunctions = new ConcurrentHashMap<>();

    public NetworkServiceImpl(ILuaEngine engine) {
        this.engine = engine;
    }

    private ILuaTable createServiceTable() {
        ILuaTable table = engine.createTable();
        table.rawset("FireServer", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 2) {
                String name = args[off].asString();
                ILuaValue[] passArgs = args[off + 1].isTable() ? unpackTable(args[off + 1].asTable()) : new ILuaValue[0];
                this.FireServer(name, passArgs);
            }
            return engine.nilValue();
        });
        table.rawset("FireClient", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 3) {
                String name = args[off].asString();
                String uuid = args[off + 1].asString();
                ILuaValue[] passArgs = args[off + 2].isTable() ? unpackTable(args[off + 2].asTable()) : new ILuaValue[0];
                this.FireClient(name, uuid, passArgs);
            }
            return engine.nilValue();
        });
        table.rawset("FireAllClients", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off >= 2) {
                String name = args[off].asString();
                ILuaValue[] passArgs = args[off + 1].isTable() ? unpackTable(args[off + 1].asTable()) : new ILuaValue[0];
                this.FireAllClients(name, passArgs);
            }
            return engine.nilValue();
        });
        return table;
    }

    /**
     * Unpacks a Lua vararg-style table into a positional array, preserving order.
     * (asMap() is a HashMap whose iteration order scrambles positional args.)
     */
    private ILuaValue[] unpackTable(ILuaTable tbl) {
        java.util.ArrayList<ILuaValue> list = new java.util.ArrayList<>();
        for (int i = 1; i <= tbl.length(); i++) {
            ILuaValue val = tbl.rawget(i);
            if (val == null || val.isNil()) break;
            list.add(val);
        }
        return list.toArray(new ILuaValue[0]);
    }

    @Override
    public ILuaTable GetOrCreateRemoteEvent(String channelName) {
        ILuaTable resTable = remoteEvents.computeIfAbsent(channelName, name -> {
            ILuaValue remoteEventClass = engine.getGlobalEnvironment().rawget("RemoteEvent");
            if (remoteEventClass == null || remoteEventClass.isNil()) {
                throw new IllegalStateException("RemoteEvent Lua class not initialized. Did bootstrap run?");
            }
            ILuaValue newFn = remoteEventClass.asTable().rawget("new");
            ILuaValue res = engine.callFunction(newFn, remoteEventClass, engine.wrapString(name), createServiceTable());
            com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[Network Registry] [NetService@" + System.identityHashCode(this) + " / Engine@" + System.identityHashCode(engine) + "] Registered RemoteEvent '" + channelName + "'. All channels in this instance: " + remoteEvents.keySet()
            );
            return res.asTable();
        });
        return resTable;
    }

    @Override
    public ILuaTable GetOrCreateRemoteFunction(String functionName) {
        return remoteFunctions.computeIfAbsent(functionName, name -> {
            ILuaValue remoteFnClass = engine.getGlobalEnvironment().rawget("RemoteFunction");
            if (remoteFnClass == null || remoteFnClass.isNil()) {
                throw new IllegalStateException("RemoteFunction Lua class not initialized. Did bootstrap run?");
            }
            ILuaValue newFn = remoteFnClass.asTable().rawget("new");
            ILuaValue res = engine.callFunction(newFn, remoteFnClass, engine.wrapString(name), createServiceTable());
            return res.asTable();
        });
    }

    /** Java-side diagnostic only: names of all registered remote event channels. */
    public java.util.Set<String> getRegisteredRemoteEventNames() {
        return java.util.Set.copyOf(remoteEvents.keySet());
    }

    @Override
    public ILuaValue InvokeServer(String functionName, ILuaValue[] args) {
        ILuaTable remoteFn = remoteFunctions.get(functionName);
        if (remoteFn != null) {
            ILuaValue callback = remoteFn.rawget("OnServerInvoke");
            if (callback != null && !callback.isNil()) {
                return engine.callFunction(callback, args);
            }
        }
        return engine.nilValue();
    }

    @Override
    public ILuaValue InvokeClient(String functionName, String playerUuid, ILuaValue[] args) {
        ILuaTable remoteFn = remoteFunctions.get(functionName);
        if (remoteFn != null) {
            ILuaValue callback = remoteFn.rawget("OnClientInvoke");
            if (callback != null && !callback.isNil()) {
                return engine.callFunction(callback, args);
            }
        }
        return engine.nilValue();
    }

    @Override
    public void FireClient(String channelName, String playerUuid, ILuaValue[] args) {
        String json = serializeArgs(args);
        Platform.getNetwork().sendPayloadPacket(playerUuid, channelName, json);
    }

    @Override
    public void FireAllClients(String channelName, ILuaValue[] args) {
        String json = serializeArgs(args);
        Platform.getNetwork().broadcastPayloadPacket(channelName, json);
    }

    @Override
    public void FireServer(String channelName, ILuaValue[] args) {
        String json = serializeArgs(args);
        Platform.getNetwork().sendPayloadPacketToServer(channelName, json);
    }

    @Override
    public void OnClientFired(String channelName, String playerUuid, ILuaValue[] args) {
        com.luatweaker.api.log.LuaTweakerLog.get().info(
            com.luatweaker.api.log.LogStage.SYSTEM,
            "[Network OnClientFired] [NetService@" + System.identityHashCode(this) + " / Engine@" + System.identityHashCode(engine) + "] Looking up channel '" + channelName + "'. Available channels: " + remoteEvents.keySet()
        );
        ILuaTable remoteEvent = remoteEvents.get(channelName);
        if (remoteEvent != null) {
            ILuaValue onServerEvent = remoteEvent.rawget("OnServerEvent");
            if (onServerEvent != null && !onServerEvent.isNil()) {
                ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
                if (signalClass != null && signalClass.isTable()) {
                    ILuaValue fireFn = signalClass.asTable().rawget("Fire");
                    if (fireFn != null && !fireFn.isNil()) {
                        ILuaValue player = engine.nilValue();
                        com.luatweaker.api.entity.IPlayer p = null;
                        if (Platform.isInitialized()) {
                            p = Platform.getEntity().getPlayer(playerUuid);
                            if (p != null) {
                                player = com.luatweaker.entities.EntitiesLuaBinding.createPlayerLuaTable(engine, p);
                            } else {
                                com.luatweaker.api.log.LuaTweakerLog.get().warn(
                                    com.luatweaker.api.log.LogStage.SYSTEM,
                                    "[Network Error] OnClientFired aborted: Player UUID '" + playerUuid + "' not found on server."
                                );
                                return;
                            }
                        }

                        com.luatweaker.api.log.LuaTweakerLog.get().info(
                            com.luatweaker.api.log.LogStage.SYSTEM,
                            "[Network] OnClientFired firing RemoteEvent '" + channelName + "' for player: " + (p != null ? p.getName() : "Unknown")
                        );

                        ILuaValue[] fireArgs = new ILuaValue[args.length + 1];
                        fireArgs[0] = player;
                        System.arraycopy(args, 0, fireArgs, 1, args.length);
                        engine.callFunction(fireFn, appendThis(onServerEvent, fireArgs));
                    }
                }
            } else {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.SYSTEM,
                    "[Network Error] RemoteEvent '" + channelName + "' exists but OnServerEvent is NIL!"
                );
            }
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "[Network Error] RemoteEvent '" + channelName + "' was NOT registered in remoteEvents map! Registered channels: " + remoteEvents.keySet()
            );
        }
    }

    @Override
    public void OnServerFired(String channelName, String playerUuid, ILuaValue[] args) {
        ILuaTable remoteEvent = remoteEvents.get(channelName);
        if (remoteEvent != null) {
            ILuaValue onClientEvent = remoteEvent.rawget("OnClientEvent");
            if (onClientEvent != null && !onClientEvent.isNil()) {
                ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
                if (signalClass != null && signalClass.isTable()) {
                    ILuaValue fireFn = signalClass.asTable().rawget("Fire");
                    if (fireFn != null && !fireFn.isNil()) {
                        engine.callFunction(fireFn, appendThis(onClientEvent, args));
                    }
                }
            }
        }
    }

    private ILuaValue[] appendThis(ILuaValue self, ILuaValue[] args) {
        ILuaValue[] result = new ILuaValue[args.length + 1];
        result[0] = self;
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
    }

    private String serializeArgs(ILuaValue[] args) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        for (ILuaValue val : args) {
            array.add(convertToTree(val));
        }
        return new com.google.gson.Gson().toJson(array);
    }

    private com.google.gson.JsonElement convertToTree(ILuaValue val) {
        if (val == null || val.isNil()) return com.google.gson.JsonNull.INSTANCE;
        if (val.isTable()) {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            for (Map.Entry<ILuaValue, ILuaValue> entry : val.asTable().asMap().entrySet()) {
                obj.add(entry.getKey().asString(), convertToTree(entry.getValue()));
            }
            return obj;
        }
        Object raw = val.toJavaObject();
        if (raw instanceof String s) return new com.google.gson.JsonPrimitive(s);
        if (raw instanceof Number n) return new com.google.gson.JsonPrimitive(n);
        if (raw instanceof Boolean b) return new com.google.gson.JsonPrimitive(b);
        return new com.google.gson.JsonPrimitive(val.asString());
    }
}
