package com.luatweaker.api.pal;

public final class Platform {
    private Platform() {}

    private static IPlatformNetwork network;
    private static IPlatformEntity entity;
    private static IPlatformInteraction interaction;
    private static IPlatformContent content;
    private static IPlatformStorage storage;
    private static IPlatformClient client;
    private static IPlatformDimension dimension;
    private static IPlatformStructures structures;
    private static IPlatformCapability capability;
    private static IPlatformWorldgen worldgen;
    private static IPlatformDatapack datapack;

    public static void setNetwork(IPlatformNetwork instance) { network = instance; }
    public static void setEntity(IPlatformEntity instance) { entity = instance; }
    public static void setInteraction(IPlatformInteraction instance) { interaction = instance; }
    public static void setContent(IPlatformContent instance) { content = instance; }
    public static void setStorage(IPlatformStorage instance) { storage = instance; }
    public static void setClient(IPlatformClient instance) { client = instance; }
    public static void setDimension(IPlatformDimension instance) { dimension = instance; }
    public static void setStructures(IPlatformStructures instance) { structures = instance; }
    public static void setCapability(IPlatformCapability instance) { capability = instance; }
    public static void setWorldgen(IPlatformWorldgen instance) { worldgen = instance; }
    public static void setDatapack(IPlatformDatapack instance) { datapack = instance; }

    public static IPlatformNetwork getNetwork() {
        if (network == null) { throw new IllegalStateException("Platform network helper is not initialized!"); }
        return network;
    }

    public static IPlatformEntity getEntity() {
        if (entity == null) { throw new IllegalStateException("Platform entity helper is not initialized!"); }
        return entity;
    }

    public static IPlatformInteraction getInteraction() {
        if (interaction == null) { throw new IllegalStateException("Platform interaction helper is not initialized!"); }
        return interaction;
    }

    public static IPlatformContent getContent() {
        if (content == null) { throw new IllegalStateException("Platform content helper is not initialized!"); }
        return content;
    }

    public static IPlatformStorage getStorage() {
        if (storage == null) { throw new IllegalStateException("Platform storage helper is not initialized!"); }
        return storage;
    }

    public static IPlatformClient getClient() {
        if (client == null) { throw new IllegalStateException("Platform client helper is not initialized!"); }
        return client;
    }

    /**
     * Nullable accessor for client-only helpers.
     *
     * <p>Dedicated servers never call {@link #setClient}, so probing with
     * {@code getClient() != null} throws instead of reporting "no client". Services that
     * are also registered on dedicated servers (client effects, GUI) must use this method.</p>
     */
    public static IPlatformClient getClientOrNull() {
        return client;
    }

    public static IPlatformDimension getDimension() {
        if (dimension == null) { throw new IllegalStateException("Platform dimension helper is not initialized!"); }
        return dimension;
    }

    public static IPlatformStructures getStructures() {
        if (structures == null) { throw new IllegalStateException("Platform structures helper is not initialized!"); }
        return structures;
    }

    public static IPlatformCapability getCapability() {
        if (capability == null) { throw new IllegalStateException("Platform capability helper is not initialized!"); }
        return capability;
    }

    /** Nullable accessor: lightweight test platforms may not bridge block capabilities at all. */
    public static IPlatformCapability getCapabilityOrNull() {
        return capability;
    }

    public static IPlatformWorldgen getWorldgen() {
        if (worldgen == null) { throw new IllegalStateException("Platform worldgen helper is not initialized!"); }
        return worldgen;
    }

    public static IPlatformDatapack getDatapack() {
        if (datapack == null) { throw new IllegalStateException("Platform datapack helper is not initialized!"); }
        return datapack;
    }

    /** Nullable accessor: unit tests and early startup may have no loaded packs. */
    public static IPlatformDatapack getDatapackOrNull() {
        return datapack;
    }

    public static boolean isInitialized() {
        return content != null;
    }
}
