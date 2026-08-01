package com.luatweaker.api.pal;

public class Platform {
    private static IPlatformNetwork network;
    private static IPlatformEntity entity;
    private static IPlatformInteraction interaction;
    private static IPlatformContent content;
    private static IPlatformStorage storage;

    public static void setNetwork(IPlatformNetwork instance) { network = instance; }
    public static void setEntity(IPlatformEntity instance) { entity = instance; }
    public static void setInteraction(IPlatformInteraction instance) { interaction = instance; }
    public static void setContent(IPlatformContent instance) { content = instance; }
    public static void setStorage(IPlatformStorage instance) { storage = instance; }

    public static IPlatformNetwork getNetwork() {
        if (network == null) throw new IllegalStateException("Platform network helper is not initialized!");
        return network;
    }

    public static IPlatformEntity getEntity() {
        if (entity == null) throw new IllegalStateException("Platform entity helper is not initialized!");
        return entity;
    }

    public static IPlatformInteraction getInteraction() {
        if (interaction == null) throw new IllegalStateException("Platform interaction helper is not initialized!");
        return interaction;
    }

    public static IPlatformContent getContent() {
        if (content == null) throw new IllegalStateException("Platform content helper is not initialized!");
        return content;
    }

    public static IPlatformStorage getStorage() {
        if (storage == null) throw new IllegalStateException("Platform storage helper is not initialized!");
        return storage;
    }
    
    public static boolean isInitialized() {
        return content != null;
    }
}
