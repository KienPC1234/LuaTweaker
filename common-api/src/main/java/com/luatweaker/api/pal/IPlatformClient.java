package com.luatweaker.api.pal;

/**
 * Platform hooks that only exist on a client process.
 *
 * <p>Never resolved on a dedicated server: use {@link Platform#getClientOrNull()}.</p>
 */
public interface IPlatformClient extends IPlatformImGui {
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

    /**
     * Renderer backend behind this client. The reference implementation reports
     * "opengl" (1.21.1 ships GL only); a Vulkan future overrides this to "vulkan".
     * The default reports "unknown" so thin platforms stay honest.
     */
    default String getRendererBackend() { return "unknown"; }

    /**
     * Loads a vanilla post-chain resource (e.g. "luatweaker:shaders/post/lt_x.json")
     * through {@code GameRenderer#loadEffect}, replacing any active chain.
     * {@code programName} (e.g. "luatweaker:lt_x") identifies the chain's own
     * program so vec uniforms can be written through a mirror EffectInstance.
     * Default: no client rendering here, so nothing to do.
     */
    default void activateShaderPost(String chainLocation, String programName) {}

    /** Closes the active post chain, if any. Default: no-op. */
    default void deactivateShaderPost() {}

    /**
     * Sets one single-float uniform on the active post chain (mirrors the chain
     * API, which only exposes float). Missing uniforms are safe no-ops.
     * Default: no-op.
     */
    default void setShaderFloat(String uniformName, float value) {}

    /**
     * Sets one vec2/vec3/vec4 uniform on the active pass via a mirror
     * EffectInstance sharing the chain's GL program. Length must be 2-4.
     * Default: no-op.
     */
    default void setShaderFloatVec(String uniformName, float... values) {}

    default void handleClientSyncData(String dataJson) {}
}
