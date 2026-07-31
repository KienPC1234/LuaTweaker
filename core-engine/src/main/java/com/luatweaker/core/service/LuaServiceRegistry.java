package com.luatweaker.core.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LuaServiceRegistry {
    private static final Map<String, Object> SERVICES = new ConcurrentHashMap<>();

    public static void register(String name, Object service) {
        SERVICES.put(name, service);
        com.luatweaker.api.log.LuaTweakerLog.get().info(
            com.luatweaker.api.log.LogStage.SYSTEM,
            "[LuaServiceRegistry] Registered service '" + name + "' -> Instance@" + System.identityHashCode(service)
        );
    }

    public static Object get(String name) {
        Object res = SERVICES.get(name);
        return res;
    }

    public static void clear() {
        com.luatweaker.api.log.LuaTweakerLog.get().info(
            com.luatweaker.api.log.LogStage.SYSTEM,
            "[LuaServiceRegistry] Cleared all registered services."
        );
        SERVICES.clear();
    }
}
