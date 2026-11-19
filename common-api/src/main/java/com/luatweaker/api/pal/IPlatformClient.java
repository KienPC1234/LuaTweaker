package com.luatweaker.api.pal;

/**
 * Platform hooks that only exist on a client process.
 *
 * <p>Never resolved on a dedicated server: use {@link Platform#getClientOrNull()}.</p>
 */
public interface IPlatformClient {
    void spawnParticle(String particleId, double x, double y, double z, double vx, double vy, double vz);

    /**
     * Plays a non-positional UI sound.
     *
     * @param source sound category that owns the volume slider (never {@code null})
     * @param loop   keeps the sound running until it is stopped
     * @return a handle for looping sounds, or {@code 0} for one-shot sounds
     */
    long playSound(String soundId, double volume, double pitch, String source, boolean loop);

    /**
     * Plays a positional world sound.
     *
     * @return a handle for looping sounds, or {@code 0} for one-shot sounds
     */
    long playSoundAt(String soundId, double x, double y, double z,
                     double volume, double pitch, String source, boolean loop);

    /** Stops a looping sound by handle. Returns {@code false} for stale handles. */
    boolean stopSound(long handle);

    /** Stops every instance of one sound id inside one category. */
    void stopSounds(String soundId, String source);

    /** Stops every sound the game is currently playing. Returns the number of Lua handles dropped. */
    int stopAllSounds();

    /** Number of looping sounds still tracked by Lua handles. */
    int getActiveSoundCount();

    int getGuiScaledWidth();

    int getGuiScaledHeight();

    default void openScreen(String screenId, String title, @org.jetbrains.annotations.Nullable com.luatweaker.api.vm.ILuaTable screenTable, @org.jetbrains.annotations.Nullable com.luatweaker.api.vm.ILuaEngine engine, boolean pauseGame) {}

    default void closeScreen() {}

    default boolean isScreenOpen() { return false; }

    @org.jetbrains.annotations.Nullable
    default com.luatweaker.api.entity.IPlayer getLocalPlayer() { return null; }

    default void handleClientSyncData(String dataJson) {}
}
