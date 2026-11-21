package com.luatweaker.network;

import com.luatweaker.api.network.IRocketNetworkService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/**
 * Lua surface for the network module. RemoteEvent firing is a one-way send; RemoteFunction
 * is a real request/response round trip (see {@link NetworkServiceImpl#RpcInvokeServer}).
 * The {@code _Rpc*} names are the transport plumbing used by the bootstrap
 * {@code RemoteFunction} class, which owns the yielding wait loop.
 */
public final class NetworkLuaBinding {

    private static final int MIN_ONE_ARG = 1;
    private static final int MIN_TWO_ARGS = 2;
    private static final int MIN_THREE_ARGS = 3;

    private NetworkLuaBinding() {}

    public static void registerBindings(ILuaEngine engine, IRocketNetworkService networkService) {
        ILuaTable network = engine.createTable();
        registerRemoteFactories(engine, networkService, network);
        registerRpcPlumbing(engine, networkService, network);

        engine.registerService("NetworkService", network);
        engine.registerService("Network", network);
        engine.registerService("NetworkServiceImpl", networkService);
        engine.registerGlobal("NetworkService", network);
        engine.registerGlobal("Network", network);
    }

    private static void registerRemoteFactories(ILuaEngine engine, IRocketNetworkService networkService,
                                                ILuaTable network) {
        network.rawset("GetOrCreateRemoteEvent", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.nilValue();
            }
            String name = args[off].asString();
            return networkService.GetOrCreateRemoteEvent(name);
        });
        network.rawset("GetOrCreateRemoteFunction", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.nilValue();
            }
            String name = args[off].asString();
            return networkService.GetOrCreateRemoteFunction(name);
        });
    }

    private static void registerRpcPlumbing(ILuaEngine engine, IRocketNetworkService networkService,
                                            ILuaTable network) {
        network.rawset("_RpcInvokeServer", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_TWO_ARGS) {
                throw new IllegalArgumentException(
                        "NetworkService:_RpcInvokeServer(functionName, args) requires a name and an args table");
            }
            String name = args[off].asString();
            ILuaValue[] passArgs = args[off + 1].isTable()
                    ? RemoteServiceTables.unpackTable(args[off + 1].asTable())
                    : new ILuaValue[0];
            return engine.wrapString(networkService.RpcInvokeServer(name, passArgs));
        });
        network.rawset("_RpcInvokeClient", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_THREE_ARGS) {
                throw new IllegalArgumentException(
                        "NetworkService:_RpcInvokeClient(functionName, playerUuid, args) requires three arguments");
            }
            String name = args[off].asString();
            String uuid = args[off + 1].asString();
            ILuaValue[] passArgs = args[off + 2].isTable()
                    ? RemoteServiceTables.unpackTable(args[off + 2].asTable())
                    : new ILuaValue[0];
            return engine.wrapString(networkService.RpcInvokeClient(name, uuid, passArgs));
        });
        network.rawset("_RpcPoll", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < MIN_ONE_ARG) {
                return engine.nilValue();
            }
            return networkService.RpcPoll(args[off].asString());
        });
        network.rawset("_RpcCancel", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off >= MIN_ONE_ARG) {
                networkService.RpcCancel(args[off].asString());
            }
            return engine.nilValue();
        });
    }
}
