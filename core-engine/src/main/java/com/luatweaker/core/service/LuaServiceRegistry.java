package com.luatweaker.core.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LuaServiceRegistry {
    private static final Map<String, Object> SERVICES = new ConcurrentHashMap<>();

    public static void register(String name, Object service) {
        SERVICES.put(name, service);
    }

    public static Object get(String name) {
        return SERVICES.get(name);
    }

    public static void clear() {
        SERVICES.clear();
    }
}
