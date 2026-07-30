package com.luatweaker.api.pal;

public class Platform {
    private static IPlatformHelper helper;

    public static void set(IPlatformHelper instance) {
        helper = instance;
    }

    public static IPlatformHelper get() {
        if (helper == null) {
            throw new IllegalStateException("Platform helper is not initialized!");
        }
        return helper;
    }
    
    public static boolean isInitialized() {
        return helper != null;
    }
}
