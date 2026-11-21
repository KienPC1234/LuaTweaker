package com.luatweaker.platform.client;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.platform.sound.SoundHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class NeoForgeClientPlatform implements IPlatformClient {
    private static final String SYNC_TYPE_NULL = "null";
    private static final String SYNC_TYPE_BOOL = "bool";
    private static final String SYNC_TYPE_NUMBER = "num";
    private static final long NO_SOUND_HANDLE = 0L;
    private static final float MIN_PROGRESS_BAR_EXPLICIT_WIDTH = 0.0f;
    private static final float AUTO_PROGRESS_BAR_WIDTH = -1.0f;


    /**
     * Safety cap for looping sounds. A script that starts a loop every tick would
     * otherwise grow this map forever; the oldest handle is stopped and evicted.
     */
    private static final int MAX_TRACKED_LOOPING_SOUNDS = 256;

    private final AtomicLong nextSoundHandle = new AtomicLong(1L);
    private final Map<Long, LuaSoundInstance> loopingSounds = new ConcurrentHashMap<>();
    private boolean capWarningLogged;

    @Override
    public void spawnParticle(String particleId, double x, double y, double z, double vx, double vy, double vz) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        ResourceLocation location = parseParticleId(particleId);
        if (location == null) {
            return;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(location);
        if (!(type instanceof net.minecraft.core.particles.ParticleOptions options)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Particle] '" + particleId + "' cannot be spawned by id because it needs"
                            + " particle options (dust/block/item/vibration style particles)");
            return;
        }
        Minecraft.getInstance().level.addParticle(options, x, y, z, vx, vy, vz);
    }

    private static @org.jetbrains.annotations.Nullable ResourceLocation parseParticleId(String particleId) {
        if (particleId == null || particleId.isBlank()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "[Particle] empty particle id");
            return null;
        }
        String trimmed = particleId.trim();
        ResourceLocation location = trimmed.contains(":")
                ? ResourceLocation.tryParse(trimmed)
                : ResourceLocation.tryParse("minecraft:" + trimmed);
        if (location == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM, "[Particle] malformed particle id '" + particleId + "'");
        }
        return location;
    }

    @Override
    public long playSound(String soundId, double volume, double pitch, String source, boolean loop) {
        return startSound(soundId, null, volume, pitch, source, loop, SoundSource.MASTER);
    }

    @Override
    public long playSoundAt(String soundId, double x, double y, double z,
                            double volume, double pitch, String source, boolean loop) {
        return startSound(soundId, new double[] {x, y, z}, volume, pitch, source, loop, SoundSource.NEUTRAL);
    }

    private long startSound(String soundId, double[] position, double volume, double pitch,
                            String source, boolean loop, SoundSource fallbackSource) {
        ResourceLocation location = SoundHelper.parseId(soundId);
        if (location == null) {
            return 0L;
        }
        SoundSource category = SoundHelper.parseSource(source, fallbackSource);
        float safeVolume = SoundHelper.volume(volume);
        float safePitch = SoundHelper.pitch(pitch);
        LuaSoundInstance instance = position == null
                ? LuaSoundInstance.uiSound(location, category, safeVolume, safePitch, loop)
                : LuaSoundInstance.world(location, category, safeVolume, safePitch, loop,
                        position[0], position[1], position[2]);

        long handle = loop ? registerLooping(instance) : 0L;

        // SoundManager is client-thread only; execute() runs inline on that thread and
        // is queued when Lua runs on the integrated server thread.
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().getSoundManager().getSoundEvent(location) == null) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Sound] no sounds.json entry for '" + soundId + "'");
                if (handle != NO_SOUND_HANDLE) {
                    loopingSounds.remove(handle);
                }
                return;
            }
            Minecraft.getInstance().getSoundManager().play(instance);
        });
        return handle;
    }

    private long registerLooping(LuaSoundInstance instance) {
        if (loopingSounds.size() >= MAX_TRACKED_LOOPING_SOUNDS) {
            Long oldest = loopingSounds.keySet().stream().min(Long::compare).orElse(null);
            if (oldest != null) {
                stopSound(oldest);
            }
            if (!capWarningLogged) {
                capWarningLogged = true;
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Sound] more than " + MAX_TRACKED_LOOPING_SOUNDS
                                + " looping sounds: stopping the oldest handle (call StopSound when done)");
            }
        }
        long handle = nextSoundHandle.getAndIncrement();
        loopingSounds.put(handle, instance);
        return handle;
    }

    @Override
    public boolean stopSound(long handle) {
        LuaSoundInstance instance = loopingSounds.remove(handle);
        if (instance == null) {
            return false;
        }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().stop(instance));
        return true;
    }

    @Override
    public void stopSounds(String soundId, String source) {
        ResourceLocation location = SoundHelper.parseId(soundId);
        if (location == null) {
            return;
        }
        SoundSource category = SoundHelper.parseSource(source, SoundSource.MASTER);
        loopingSounds.values().removeIf(sound ->
                sound.getLocation().equals(location) && sound.getSource() == category);
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().stop(location, category));
    }

    @Override
    public int stopAllSounds() {
        int dropped = loopingSounds.size();
        loopingSounds.clear();
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().getSoundManager().stop());
        return dropped;
    }

    @Override
    public int getActiveSoundCount() {
        return loopingSounds.size();
    }

    @Override
    public int getGuiScaledWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public int getGuiScaledHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    @Override
    public void openScreen(String screenId, String title,
                           @org.jetbrains.annotations.Nullable com.luatweaker.api.vm.ILuaTable screenTable,
                           @org.jetbrains.annotations.Nullable com.luatweaker.api.vm.ILuaEngine engine,
                           boolean pauseGame) {
        Minecraft.getInstance().tell(() -> {
            net.minecraft.network.chat.Component titleComp = net.minecraft.network.chat.Component.literal(title != null ? title : "");
            Minecraft.getInstance().setScreen(new LuaCanvasScreen(screenId, titleComp, engine, screenTable, pauseGame));
        });
    }

    @Override
    public void closeScreen() {
        Minecraft.getInstance().tell(() -> {
            if (Minecraft.getInstance().screen instanceof LuaCanvasScreen) {
                Minecraft.getInstance().setScreen(null);
            }
        });
    }

    @Override
    public boolean isScreenOpen() {
        return Minecraft.getInstance().screen instanceof LuaCanvasScreen;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public com.luatweaker.api.entity.IPlayer getLocalPlayer() {
        if (Minecraft.getInstance().player != null) {
            return new com.luatweaker.platform.entity.NeoForgePlayerWrapper(Minecraft.getInstance().player);
        }
        return null;
    }

    /**
     * Chain location passed to the last activateShaderPost; uniforms only apply while it is live.
     * Written from the Lua thread and read on the render thread, hence the atomic holder.
     */
    private final java.util.concurrent.atomic.AtomicReference<String> expectedShaderChain =
            new java.util.concurrent.atomic.AtomicReference<>();
    /** Own program of the last activated chain, for the vec-uniform mirror (cross-thread, atomic). */
    private final java.util.concurrent.atomic.AtomicReference<String> expectedShaderProgram =
            new java.util.concurrent.atomic.AtomicReference<>();
    /** Chain location -> mirror EffectInstance sharing the chain's GL program. */
    private final Map<String, net.minecraft.client.renderer.EffectInstance> vecMirrors =
            new ConcurrentHashMap<>();

    @Override
    public String getRendererBackend() {
        // 1.21.1 ships an OpenGL renderer only; a Vulkan future overrides this.
        return Minecraft.getInstance().getWindow() != null ? "opengl" : "unknown";
    }

    @Override
    public void activateShaderPost(String chainLocation, String programName) {
        ResourceLocation location = ResourceLocation.tryParse(chainLocation);
        if (location == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] refusing to load malformed chain location '" + chainLocation + "'.");
            return;
        }
        // GameRenderer is render-thread owned; execute() runs inline on that thread
        // and is queued when Lua runs on the integrated server thread.
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gameRenderer == null) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Shaders] game renderer not ready; pass '" + chainLocation + "' not loaded.");
                return;
            }
            // loadEffect closes any previous chain first, then warns (not throws)
            // when our virtual files are missing — so a failure stays visible.
            Minecraft.getInstance().gameRenderer.loadEffect(location);
            expectedShaderChain.set(chainLocation);
            expectedShaderProgram.set(programName);
            vecMirrors.remove(chainLocation);
            try {
                // Mirror sharing the chain's GL program (EffectProgram cache): setting
                // uniforms on it only marks them dirty; the upload happens when the
                // chain's own pass renders. Safe to write any time on this thread.
                vecMirrors.put(chainLocation,
                        new net.minecraft.client.renderer.EffectInstance(
                                Minecraft.getInstance().getResourceManager(), programName));
            } catch (java.io.IOException e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Shaders] vec-uniform mirror unavailable for '" + chainLocation
                                + "' (" + e.getMessage() + "); float uniforms still work.");
            }
        });
        // Runs after the load above (same thread, FIFO): the driver reports GLSL
        // compile errors as IOExceptions that loadEffect swallows into a warn.
        // This check turns "nothing changed on screen" into a named failure:
        // which pass died, and where the driver's line numbers live.
        Minecraft.getInstance().execute(() -> {
            net.minecraft.client.renderer.PostChain live =
                    Minecraft.getInstance().gameRenderer != null ? Minecraft.getInstance().gameRenderer.currentEffect() : null;
            if (live != null && live.getName().equals(chainLocation)) {
                LuaTweakerLog.get().debug(LogStage.SYSTEM,
                        "[Shaders] pass '" + chainLocation + "' is live.");
            } else {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Shaders] pass '" + chainLocation + "' failed to activate — the driver"
                                + " rejected the chain. Look above for \"Couldn't compile\" with the"
                                + " driver's line numbers, then fix the fragment and re-register.");
            }
        });
    }

    @Override
    public void deactivateShaderPost() {
        expectedShaderChain.set(null);
        expectedShaderProgram.set(null);
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gameRenderer != null) {
                Minecraft.getInstance().gameRenderer.shutdownEffect();
            }
        });
    }

    @Override
    public void setShaderFloat(String uniformName, float value) {
        if (uniformName == null || uniformName.isBlank()) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gameRenderer == null) {
                return;
            }
            net.minecraft.client.renderer.PostChain chain = Minecraft.getInstance().gameRenderer.currentEffect();
            // Only touch our own chain: vanilla spectator/death shaders own theirs,
            // and a resource reload may have replaced ours with nothing.
            if (chain != null && chain.getName().equals(expectedShaderChain.get())) {
                chain.setUniform(uniformName, value);
            }
        });
    }

    @Override
    public void setShaderFloatVec(String uniformName, float[] values) {
        if (uniformName == null || uniformName.isBlank() || values == null) {
            return;
        }
        float[] copy = values.clone();
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gameRenderer == null) {
                return;
            }
            String chainLocation = expectedShaderChain.get();
            net.minecraft.client.renderer.PostChain chain = Minecraft.getInstance().gameRenderer.currentEffect();
            if (chain == null || !chain.getName().equals(chainLocation)) {
                return;
            }
            net.minecraft.client.renderer.EffectInstance mirror = vecMirrorFor(chainLocation);
            if (mirror == null) {
                return;
            }
            try {
                // Missing names resolve to a dummy uniform (safe no-op); a wrong
                // arity throws, so guard loudly instead of crashing the tick.
                mirror.safeGetUniform(uniformName).set(copy);
            } catch (IllegalArgumentException | IllegalStateException e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Shaders] setShaderFloatVec('" + uniformName + "') failed"
                                + " (declare it as vec" + copy.length + " in your fragment): "
                                + e.getMessage());
            }
        });
    }

    @Override
    public void handleClientSyncData(String dataJson) {
        if (!hasSyncTarget(dataJson)) {
            return;
        }
        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(dataJson).getAsJsonObject();
            if (!hasSyncFields(obj)) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Ignoring malformed client sync payload (missing key/val/type): " + dataJson);
                return;
            }
            applySyncedValue(Minecraft.getInstance().player.getPersistentData(),
                    obj.get("key").getAsString(),
                    obj.get("val").getAsString(),
                    obj.get("type").getAsString());
        } catch (com.google.gson.JsonParseException | IllegalStateException | NumberFormatException
                 | UnsupportedOperationException e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed to apply synced data (" + dataJson + "): " + e.getMessage());
        }
    }

    private static boolean hasSyncTarget(@org.jetbrains.annotations.Nullable String dataJson) {
        return Minecraft.getInstance().player != null && dataJson != null && !dataJson.isBlank();
    }

    private static boolean hasSyncFields(@org.jetbrains.annotations.NotNull com.google.gson.JsonObject obj) {
        return obj.has("key") && obj.has("val") && obj.has("type");
    }

    /** Writes one synced key into the player's persistent data, honouring the wire type tag. */
    private static void applySyncedValue(@org.jetbrains.annotations.NotNull net.minecraft.nbt.CompoundTag tag,
                                         @org.jetbrains.annotations.NotNull String key,
                                         @org.jetbrains.annotations.NotNull String value,
                                         @org.jetbrains.annotations.NotNull String type) {
        if (SYNC_TYPE_NULL.equals(type) || SYNC_TYPE_NULL.equals(value)) {
            tag.remove(key);
            return;
        }
        if (SYNC_TYPE_BOOL.equals(type)) {
            tag.putBoolean(key, Boolean.parseBoolean(value));
            return;
        }
        if (SYNC_TYPE_NUMBER.equals(type)) {
            tag.putDouble(key, Double.parseDouble(value));
            return;
        }
        tag.putString(key, value);
    }

    // ==== IMGUI BRIDGE (render thread only) ====
    //
    // Frame lifecycle is engine-owned: the render listener opens the frame,
    // fires ImGui.OnFrame synchronously, then closes it. Widgets called
    // anywhere else throw a Lua-readable error naming the signal.
    // Item icons are recorded during the frame and drawn with the vanilla
    // item renderer right after the ImGui pass (flushImGuiItems), because
    // ImGui has no access to Minecraft's sprite atlas.

    private boolean imguiFrameOpen;
    private int imguiWindowDepth;
    private int imguiChildDepth;
    private int imguiTreeDepth;
    private int imguiTableDepth;
    private int imguiTabBarDepth;
    private int imguiTabItemDepth;
    private int imguiMenuDepth;
    private int imguiPopupDepth;
    private int imguiDisabledDepth;
    private int imguiTooltipDepth;
    private int imguiComboDepth;
    private int imguiItemWidthDepth;
    private int imguiDragSourceDepth;
    private int imguiDragTargetDepth;
    private boolean imguiFrameHadWindow;
    private boolean imguiLastWantCaptureMouse;
    private boolean imguiLastWantCaptureKeyboard;
    private boolean imguiLastWantTextInput;
    private final java.util.List<PendingImGuiItem> pendingImGuiItems = new java.util.ArrayList<>();
    private final java.util.Set<String> warnedUnknownImGuiItems = new java.util.HashSet<>();

    private record PendingImGuiItem(String itemId, int x, int y, int sizePx, int count) {}

    /**
     * Render-thread scratch buffers. Every widget runs synchronously inside
     * the frame on the render thread (the input hook never calls widgets), so
     * reusing these instead of allocating per widget per frame is safe and
     * keeps the hottest path allocation-free. Values are copied out before
     * each method returns, so sequential (even nested) Lua calls cannot alias.
     */
    private final float[] imguiScratchF = new float[4];
    private final int[] imguiScratchI = new int[1];
    private final imgui.type.ImInt imguiScratchImInt = new imgui.type.ImInt();
    private final imgui.type.ImFloat imguiScratchImFloat = new imgui.type.ImFloat();

    /**
     * Parsed flag/color caches. Flag strings are re-parsed once per window per
     * frame otherwise (split + switch on the hot path); the mapping is a pure
     * function of the string, so a bounded LRU is exact. Render thread only.
     */
    private final Map<String, Integer> imguiWindowFlagCache = boundedImGuiCache();
    private final Map<String, Integer> imguiTableFlagCache = boundedImGuiCache();
    private final Map<String, Integer> imguiTableColumnFlagCache = boundedImGuiCache();
    private final Map<String, Integer> imguiInputFlagCache = boundedImGuiCache();
    private final Map<String, Integer> imguiColorCache = boundedImGuiCache();

    private static Map<String, Integer> boundedImGuiCache() {
        return new java.util.LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                return size() > 256;
            }
        };
    }

    /** Bounded pure-function cache: render thread only, no locking needed. */
    private static int cachedFlags(Map<String, Integer> cache, String key,
                                   java.util.function.Function<String, Integer> parse) {
        String normalized = key == null ? "" : key;
        Integer hit = cache.get(normalized);
        if (hit != null) {
            return hit;
        }
        int bits = parse.apply(normalized);
        cache.put(normalized, bits);
        return bits;
    }

    private void requireImGuiFrame(String widget) {
        if (!imguiFrameOpen) {
            throw new IllegalStateException(
                    "[ImGui] " + widget + " must be called inside ImGui.OnFrame"
                    + " (connect with ImGui.OnFrame:Connect(function() ... end)).");
        }
    }

    @Override
    public void imguiBeginFrame() {
        if (Minecraft.getInstance().getWindow() == null) {
            return;
        }
        long handle = Minecraft.getInstance().getWindow().getWindow();
        com.luatweaker.platform.client.gui.imgui.ImGuiManager.ensureInitialized(handle);
        int scaledW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int scaledH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        float scaleX = scaledW > 0 ? (float) Minecraft.getInstance().getWindow().getScreenWidth() / scaledW : 1.0f;
        float scaleY = scaledH > 0 ? (float) Minecraft.getInstance().getWindow().getScreenHeight() / scaledH : 1.0f;
        com.luatweaker.platform.client.gui.imgui.ImGuiManager.startFrame(scaledW, scaledH, scaleX, scaleY);
        imguiFrameOpen = true;
        imguiFrameHadWindow = false;
        pendingImGuiItems.clear();
    }


    /**
     * Best-effort unwind of one leaked ImGui stack left open by Lua code.
     *
     * <p>WHY BROAD: {@code ImGui.endX()} calls into native ImGui state that a Lua
     * script may have corrupted (unbalanced Begin/End); they fail with unchecked
     * exceptions only. Letting one propagate would abort frame cleanup and leak
     * every remaining stack, so the failure is logged (or deliberately silenced
     * for stacks whose warning would be noise) and unwinding stops. This is the
     * single choke point for these firewalls instead of a scattered
     * {@code catch (RuntimeException)} per stack.
     */
    private void drainImGuiStack(java.util.function.BooleanSupplier remaining, Runnable decrement,
            Runnable endCall, String stackName, boolean warn) {
        while (remaining.getAsBoolean()) {
            decrement.run();
            boolean closed = ImGuiNativeGuard.run(endCall, failure -> {
                if (warn) {
                    LuaTweakerLog.get().warn(LogStage.SYSTEM,
                            "[ImGui] auto-closing " + stackName + " failed: " + failure.getMessage());
                }
            });
            if (!closed) {
                break;
            }
        }
    }

    /**
     * Mirror {@code EffectInstance} for the live chain, created on first use. Sharing the
     * chain's GL program means uniform writes only mark it dirty.
     *
     * @return the mirror, or {@code null} when the chain/program is unknown or unloadable
     */
    private @org.jetbrains.annotations.Nullable net.minecraft.client.renderer.EffectInstance vecMirrorFor(
            @org.jetbrains.annotations.NotNull String chainLocation) {
        net.minecraft.client.renderer.EffectInstance existing = vecMirrors.get(chainLocation);
        if (existing != null) {
            return existing;
        }
        String programName = expectedShaderProgram.get();
        if (programName == null) {
            return null;
        }
        try {
            net.minecraft.client.renderer.EffectInstance mirror = new net.minecraft.client.renderer.EffectInstance(
                    Minecraft.getInstance().getResourceManager(), programName);
            vecMirrors.put(chainLocation, mirror);
            return mirror;
        } catch (java.io.IOException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] vec-uniform mirror unavailable for '" + chainLocation
                            + "' (" + e.getMessage() + ").");
            return null;
        }
    }

    @Override
    public void imguiEndFrame() {
        if (!imguiFrameOpen) {
            return;
        }
        int leakedWindows = imguiWindowDepth;
        int leakedChildren = imguiChildDepth;
        int leakedTables = imguiTableDepth;
        int leakedTabBars = imguiTabBarDepth;
        int leakedMenus = imguiMenuDepth + imguiTreeDepth + imguiTabItemDepth;
        int leakedPopups = imguiPopupDepth + imguiComboDepth;
        drainImGuiStack(() -> imguiChildDepth > 0, () -> imguiChildDepth--, imgui.ImGui::endChild,
                "a child region", true);
        drainImGuiStack(() -> imguiWindowDepth > 0, () -> imguiWindowDepth--, imgui.ImGui::end,
                "a window", true);
        // Innermost-first would be ideal; in practice a leaked frame is already
        // broken Lua, so close the remaining stacks best-effort and warn once.
        drainImGuiStack(() -> imguiTableDepth > 0, () -> imguiTableDepth--, imgui.ImGui::endTable,
                "a table", true);
        drainImGuiStack(() -> imguiTabItemDepth > 0, () -> imguiTabItemDepth--, imgui.ImGui::endTabItem,
                "a tab item", false);
        drainImGuiStack(() -> imguiTabBarDepth > 0, () -> imguiTabBarDepth--, imgui.ImGui::endTabBar,
                "a tab bar", true);
        drainImGuiStack(() -> imguiMenuDepth > 0, () -> imguiMenuDepth--, imgui.ImGui::endMenu,
                "a menu", false);
        drainImGuiStack(() -> imguiTreeDepth > 0, () -> imguiTreeDepth--, imgui.ImGui::treePop,
                "a tree node", false);
        drainImGuiStack(() -> imguiPopupDepth > 0, () -> imguiPopupDepth--, imgui.ImGui::endPopup,
                "a popup", false);
        drainImGuiStack(() -> imguiComboDepth > 0, () -> imguiComboDepth--, imgui.ImGui::endCombo,
                "a combo", false);
        drainImGuiStack(() -> imguiTooltipDepth > 0, () -> imguiTooltipDepth--, imgui.ImGui::endTooltip,
                "a tooltip", false);
        drainImGuiStack(() -> imguiDisabledDepth > 0, () -> imguiDisabledDepth--, imgui.ImGui::endDisabled,
                "a disabled scope", false);
        drainImGuiStack(() -> imguiItemWidthDepth > 0, () -> imguiItemWidthDepth--, imgui.ImGui::popItemWidth,
                "an item width", false);
        drainImGuiStack(() -> imguiDragSourceDepth > 0, () -> imguiDragSourceDepth--, imgui.ImGui::endDragDropSource,
                "a drag source", false);
        drainImGuiStack(() -> imguiDragTargetDepth > 0, () -> imguiDragTargetDepth--, imgui.ImGui::endDragDropTarget,
                "a drag target", false);
        // Counters never go negative, so "any leaked" is just a non-zero sum of
        // the pre-drain snapshots and the stacks that only drain (never open).
        int leakedTotal = leakedWindows + leakedChildren + leakedTables + leakedTabBars
                + leakedMenus + leakedPopups + imguiDisabledDepth + imguiTooltipDepth
                + imguiItemWidthDepth + imguiDragSourceDepth + imguiDragTargetDepth;
        if (leakedTotal > 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ImGui] unbalanced Begin/End detected (" + leakedWindows + " window(s), "
                            + leakedChildren + " child region(s), " + leakedTables + " table(s), "
                            + leakedTabBars + " tab bar(s), " + leakedMenus + " menu/tree/tab item(s), "
                            + leakedPopups + " popup/combo(s), " + imguiDisabledDepth + " disabled scope(s), "
                            + imguiTooltipDepth + " tooltip(s), " + imguiItemWidthDepth + " item width(s), "
                            + imguiDragSourceDepth + " drag source(s), " + imguiDragTargetDepth
                            + " drag target(s) left open); the frame auto-closed the leftovers."
                            + " Pair every Begin with End (and BeginChild with EndChild, etc.).");
        }
        imguiFrameOpen = false;
        imguiLastWantCaptureMouse = imgui.ImGui.getIO().getWantCaptureMouse() && imguiFrameHadWindow;
        imguiLastWantCaptureKeyboard = imgui.ImGui.getIO().getWantCaptureKeyboard() && imguiFrameHadWindow;
        imguiLastWantTextInput = imgui.ImGui.getIO().getWantTextInput() && imguiFrameHadWindow;
        com.luatweaker.platform.client.gui.imgui.ImGuiManager.endFrame();
    }

    /**
     * Draws item icons recorded by {@link #imguiItemIcon} during the frame.
     * Runs right after the ImGui pass with the event's GuiGraphics, in scaled
     * GUI coordinates (the ImGui display size is the scaled size, so the
     * recorded rects line up 1:1).
     */
    public void flushImGuiItems(net.minecraft.client.gui.GuiGraphics graphics) {
        if (pendingImGuiItems.isEmpty() || graphics == null) {
            return;
        }
        for (PendingImGuiItem pending : pendingImGuiItems) {
            ResourceLocation location =
                    ResourceLocation.tryParse(pending.itemId());
            net.minecraft.world.item.Item item = location == null
                    ? null : BuiltInRegistries.ITEM.get(location);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                if (warnedUnknownImGuiItems.add(pending.itemId())) {
                    LuaTweakerLog.get().debug(LogStage.SYSTEM,
                            "[ImGui] ItemIcon skipped: unknown item '" + pending.itemId() + "'");
                }
                continue;
            }
            renderImGuiItem(graphics, pending, item);
        }
        pendingImGuiItems.clear();
    }

    private void renderImGuiItem(net.minecraft.client.gui.GuiGraphics graphics, PendingImGuiItem pending,
                                 net.minecraft.world.item.Item item) {
        net.minecraft.world.item.ItemStack stack =
                new net.minecraft.world.item.ItemStack(item, pending.count());
        graphics.renderItem(stack, pending.x(), pending.y());
        graphics.renderItemDecorations(Minecraft.getInstance().font, stack, pending.x(), pending.y());
    }

    /** Latched at endFrame: safe to consult from input-event threads. */
    public boolean imguiWantsMouseForGame() {
        return imguiLastWantCaptureMouse;
    }

    /** Latched at endFrame: safe to consult from input-event threads. */
    public boolean imguiWantsKeyboardForGame() {
        return imguiLastWantCaptureKeyboard;
    }

    /** Latched at endFrame: true only while the user is typing in a field. */
    public boolean imguiWantsTextInput() {
        return imguiLastWantTextInput;
    }

    @Override
    public boolean imguiIsFrameOpen() {
        return imguiFrameOpen;
    }

    @Override
    public boolean imguiBegin(String title, boolean open, String windowFlagsCsv) {
        requireImGuiFrame("Begin");
        imgui.type.ImBoolean openState = new imgui.type.ImBoolean(open);
        imgui.ImGui.begin(title, openState, cachedFlags(imguiWindowFlagCache, windowFlagsCsv, NeoForgeClientPlatform::parseImGuiWindowFlags));
        imguiWindowDepth++;
        imguiFrameHadWindow = true;
        return openState.get();
    }

    @Override
    public void imguiEnd() {
        requireImGuiFrame("End");
        if (imguiWindowDepth <= 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ImGui] End() with no open window; call ignored (pair every Begin with End).");
            return;
        }
        imguiWindowDepth--;
        imgui.ImGui.end();
    }

    @Override
    public boolean imguiBeginChild(String id, float width, float height) {
        requireImGuiFrame("BeginChild");
        boolean open = imgui.ImGui.beginChild(id, width, height);
        imguiChildDepth++;
        return open;
    }

    @Override
    public void imguiEndChild() {
        requireImGuiFrame("EndChild");
        if (imguiChildDepth <= 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ImGui] EndChild() with no open child; call ignored.");
            return;
        }
        imguiChildDepth--;
        imgui.ImGui.endChild();
    }

    @Override
    public void imguiSetNextWindowSize(float width, float height) {
        requireImGuiFrame("SetNextWindowSize");
        imgui.ImGui.setNextWindowSize(width, height);
    }

    @Override
    public void imguiSetNextWindowPos(float x, float y) {
        requireImGuiFrame("SetNextWindowPos");
        imgui.ImGui.setNextWindowPos(x, y);
    }

    @Override
    public void imguiSameLine() {
        requireImGuiFrame("SameLine");
        imgui.ImGui.sameLine();
    }

    @Override
    public void imguiNewLine() {
        requireImGuiFrame("NewLine");
        imgui.ImGui.newLine();
    }

    @Override
    public void imguiSpacing() {
        requireImGuiFrame("Spacing");
        imgui.ImGui.spacing();
    }

    @Override
    public void imguiSeparator() {
        requireImGuiFrame("Separator");
        imgui.ImGui.separator();
    }

    @Override
    public void imguiIndent() {
        requireImGuiFrame("Indent");
        imgui.ImGui.indent();
    }

    @Override
    public void imguiUnindent() {
        requireImGuiFrame("Unindent");
        imgui.ImGui.unindent();
    }

    @Override
    public void imguiSetCursorPosX(float x) {
        requireImGuiFrame("SetCursorPosX");
        imgui.ImGui.setCursorPosX(x);
    }

    @Override
    public void imguiText(String message) {
        requireImGuiFrame("Text");
        imgui.ImGui.text(message);
    }

    @Override
    public void imguiBulletText(String message) {
        requireImGuiFrame("BulletText");
        imgui.ImGui.bulletText(message);
    }

    @Override
    public boolean imguiButton(String label) {
        requireImGuiFrame("Button");
        return imgui.ImGui.button(label);
    }

    @Override
    public boolean imguiSmallButton(String label) {
        requireImGuiFrame("SmallButton");
        return imgui.ImGui.smallButton(label);
    }

    @Override
    public boolean imguiCheckbox(String label, boolean checked) {
        requireImGuiFrame("Checkbox");
        // Explicit wrapper (not the boolean overload): the returned value is
        // the new state by contract of the canonical overload.
        imgui.type.ImBoolean state = new imgui.type.ImBoolean(checked);
        imgui.ImGui.checkbox(label, state);
        return state.get();
    }

    @Override
    public boolean imguiRadioButton(String label, boolean active) {
        requireImGuiFrame("RadioButton");
        return imgui.ImGui.radioButton(label, active);
    }

    @Override
    public double imguiSliderFloat(String label, double value, double min, double max) {
        requireImGuiFrame("SliderFloat");
        imguiScratchF[0] = (float) value;
        imgui.ImGui.sliderFloat(label, imguiScratchF, (float) min, (float) max);
        return imguiScratchF[0];
    }

    @Override
    public int imguiSliderInt(String label, int value, int min, int max) {
        requireImGuiFrame("SliderInt");
        imguiScratchI[0] = value;
        imgui.ImGui.sliderInt(label, imguiScratchI, min, max);
        return imguiScratchI[0];
    }

    @Override
    public double imguiDragFloat(String label, double value, double speed, double min, double max) {
        requireImGuiFrame("DragFloat");
        imguiScratchF[0] = (float) value;
        imgui.ImGui.dragFloat(label, imguiScratchF, (float) speed, (float) min, (float) max);
        return imguiScratchF[0];
    }

    @Override
    public int imguiDragInt(String label, int value, double speed, int min, int max) {
        requireImGuiFrame("DragInt");
        imguiScratchI[0] = value;
        imgui.ImGui.dragInt(label, imguiScratchI, (float) speed, min, max);
        return imguiScratchI[0];
    }

    @Override
    public String imguiInputText(String label, String current, boolean multiline) {
        requireImGuiFrame(multiline ? "InputTextMultiline" : "InputText");
        String seed = current == null ? "" : current;
        imgui.type.ImString buffer = new imgui.type.ImString(seed, Math.max(seed.length() + 128, 256));
        if (multiline) {
            imgui.ImGui.inputTextMultiline(label, buffer);
        } else {
            imgui.ImGui.inputText(label, buffer);
        }
        return buffer.get();
    }

    @Override
    public int imguiInputInt(String label, int value) {
        requireImGuiFrame("InputInt");
        imguiScratchImInt.set(value);
        imgui.ImGui.inputInt(label, imguiScratchImInt);
        return imguiScratchImInt.get();
    }

    @Override
    public double imguiInputFloat(String label, double value) {
        requireImGuiFrame("InputFloat");
        imguiScratchImFloat.set((float) value);
        imgui.ImGui.inputFloat(label, imguiScratchImFloat);
        return imguiScratchImFloat.get();
    }

    @Override
    public int imguiCombo(String label, int currentOneBased, String[] items) {
        requireImGuiFrame("Combo");
        imguiScratchImInt.set(currentOneBased - 1);
        imgui.ImGui.combo(label, imguiScratchImInt, items);
        int clamped = Math.min(Math.max(imguiScratchImInt.get(), 0), items.length - 1);
        return clamped + 1;
    }

    @Override
    public java.util.List<Double> imguiColorEdit(String label, double r, double g, double b, double a) {
        requireImGuiFrame("ColorEdit");
        float[] v = { (float) r, (float) g, (float) b, (float) a };
        imgui.ImGui.colorEdit4(label, v);
        return java.util.List.of((double) v[0], (double) v[1], (double) v[2], (double) v[3]);
    }

    @Override
    public void imguiProgressBar(double fraction, String label) {
        imguiProgressBar(fraction, label, 0.0, 0.0);
    }

    @Override
    public void imguiProgressBar(double fraction, String label, double width, double height) {
        requireImGuiFrame("ProgressBar");
        float w = (float) width;
        float h = (float) height;
        if (w <= MIN_PROGRESS_BAR_EXPLICIT_WIDTH) {
            w = AUTO_PROGRESS_BAR_WIDTH;
        }
        String overlay = label == null ? "" : label;
        imgui.ImGui.progressBar((float) fraction, w, h, overlay);
    }

    @Override
    public boolean imguiCollapsingHeader(String label) {
        requireImGuiFrame("CollapsingHeader");
        return imgui.ImGui.collapsingHeader(label);
    }

    @Override
    public boolean imguiTreeNode(String label) {
        requireImGuiFrame("TreeNode");
        // Dear ImGui pushes the tree stack ONLY when this returns true, so the
        // depth counter follows the result — TreePop on a closed node is a
        // no-op instead of a stack underflow.
        boolean open = imgui.ImGui.treeNode(label);
        if (open) {
            imguiTreeDepth++;
        }
        return open;
    }

    @Override
    public void imguiTreePop() {
        requireImGuiFrame("TreePop");
        if (imguiTreeDepth <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] TreePop() with no open TreeNode; call ignored"
                    + " (call TreePop only inside 'if TreeNode then ... end').");
            return;
        }
        imguiTreeDepth--;
        imgui.ImGui.treePop();
    }

    @Override
    public boolean imguiSelectable(String label, boolean selected) {
        requireImGuiFrame("Selectable");
        return imgui.ImGui.selectable(label, selected);
    }

    @Override
    public boolean imguiIsItemHovered() {
        requireImGuiFrame("IsItemHovered");
        return imgui.ImGui.isItemHovered();
    }

    @Override
    public boolean imguiIsItemClicked() {
        requireImGuiFrame("IsItemClicked");
        return imgui.ImGui.isItemClicked();
    }

    @Override
    public boolean imguiIsWindowHovered() {
        requireImGuiFrame("IsWindowHovered");
        return imgui.ImGui.isWindowHovered();
    }

    @Override
    public boolean imguiWantCaptureMouse() {
        return imguiFrameOpen && imgui.ImGui.getIO().getWantCaptureMouse();
    }

    @Override
    public boolean imguiWantCaptureKeyboard() {
        return imguiFrameOpen && imgui.ImGui.getIO().getWantCaptureKeyboard();
    }

    @Override
    public void imguiPushStyleColor(String colorName, int argb) {
        requireImGuiFrame("PushStyleColor");
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        imgui.ImGui.pushStyleColor(cachedFlags(imguiColorCache, colorName, NeoForgeClientPlatform::parseImGuiColor), r, g, b, a);
    }

    @Override
    public void imguiPopStyleColor(int count) {
        requireImGuiFrame("PopStyleColor");
        imgui.ImGui.popStyleColor(count);
    }

    @Override
    public boolean imguiItemIcon(String itemId, int sizePx, int count) {
        requireImGuiFrame("ItemIcon");
        boolean clicked = imgui.ImGui.invisibleButton("##lt_item_" + itemId + "_" + sizePx, sizePx, sizePx);
        imgui.ImVec2 min = imgui.ImGui.getItemRectMin();
        pendingImGuiItems.add(new PendingImGuiItem(itemId, (int) min.x, (int) min.y, sizePx, count));
        return clicked;
    }

    @Override
    public boolean imguiShowDemoWindow(boolean open) {
        requireImGuiFrame("ShowDemoWindow");
        imgui.type.ImBoolean state = new imgui.type.ImBoolean(open);
        imgui.ImGui.showDemoWindow(state);
        return state.get();
    }

    @Override
    public String imguiGetVersion() {
        return imgui.ImGui.getVersion();
    }

    @Override
    public boolean imguiBeginTable(String id, int columns, String flagsCsv) {
        requireImGuiFrame("BeginTable");
        boolean open = imgui.ImGui.beginTable(id, columns, cachedFlags(imguiTableFlagCache, flagsCsv, NeoForgeClientPlatform::parseImGuiTableFlags));
        imguiTableDepth++;
        imguiFrameHadWindow = true;
        return open;
    }

    @Override
    public void imguiEndTable() {
        requireImGuiFrame("EndTable");
        if (imguiTableDepth <= 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ImGui] EndTable() with no open table; call ignored.");
            return;
        }
        imguiTableDepth--;
        imgui.ImGui.endTable();
    }

    @Override
    public void imguiTableNextRow() {
        requireImGuiFrame("TableNextRow");
        imgui.ImGui.tableNextRow();
    }

    @Override
    public boolean imguiTableNextColumn() {
        requireImGuiFrame("TableNextColumn");
        return imgui.ImGui.tableNextColumn();
    }

    @Override
    public void imguiTableSetupColumn(String name, float width, String flagsCsv) {
        requireImGuiFrame("TableSetupColumn");
        imgui.ImGui.tableSetupColumn(name,
                cachedFlags(imguiTableColumnFlagCache, flagsCsv, NeoForgeClientPlatform::parseImGuiTableColumnFlags),
                width);
    }

    @Override
    public void imguiTableHeadersRow() {
        requireImGuiFrame("TableHeadersRow");
        imgui.ImGui.tableHeadersRow();
    }

    @Override
    public boolean imguiBeginTabBar(String id) {
        requireImGuiFrame("BeginTabBar");
        boolean open = imgui.ImGui.beginTabBar(id);
        imguiTabBarDepth++;
        return open;
    }

    @Override
    public void imguiEndTabBar() {
        requireImGuiFrame("EndTabBar");
        if (imguiTabBarDepth <= 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ImGui] EndTabBar() with no open tab bar; call ignored.");
            return;
        }
        imguiTabBarDepth--;
        imgui.ImGui.endTabBar();
    }

    @Override
    public boolean imguiBeginTabItem(String label) {
        requireImGuiFrame("BeginTabItem");
        // Like TreeNode: the stack pushes ONLY on true, so EndTabItem runs
        // only for selected tabs instead of underflowing.
        boolean selected = imgui.ImGui.beginTabItem(label);
        if (selected) {
            imguiTabItemDepth++;
        }
        return selected;
    }

    @Override
    public void imguiEndTabItem() {
        requireImGuiFrame("EndTabItem");
        if (imguiTabItemDepth <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] EndTabItem() with no selected tab; call ignored"
                    + " (call EndTabItem only inside 'if BeginTabItem then ... end').");
            return;
        }
        imguiTabItemDepth--;
        imgui.ImGui.endTabItem();
    }

    @Override
    public boolean imguiBeginMenuBar() {
        requireImGuiFrame("BeginMenuBar");
        return imgui.ImGui.beginMenuBar();
    }

    @Override
    public void imguiEndMenuBar() {
        requireImGuiFrame("EndMenuBar");
        imgui.ImGui.endMenuBar();
    }

    @Override
    public boolean imguiBeginMenu(String label) {
        requireImGuiFrame("BeginMenu");
        // Pushes ONLY on true, mirroring TreeNode/TabItem guards.
        boolean open = imgui.ImGui.beginMenu(label);
        if (open) {
            imguiMenuDepth++;
        }
        return open;
    }

    @Override
    public void imguiEndMenu() {
        requireImGuiFrame("EndMenu");
        if (imguiMenuDepth <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] EndMenu() with no open menu; call ignored"
                    + " (call EndMenu only inside 'if BeginMenu then ... end').");
            return;
        }
        imguiMenuDepth--;
        imgui.ImGui.endMenu();
    }

    @Override
    public boolean imguiMenuItem(String label, String shortcut, boolean selected, boolean enabled) {
        requireImGuiFrame("MenuItem");
        return imgui.ImGui.menuItem(label, shortcut, selected, enabled);
    }

    @Override
    public int imguiListBox(String label, int currentOneBased, String[] items) {
        requireImGuiFrame("ListBox");
        imguiScratchImInt.set(currentOneBased - 1);
        imgui.ImGui.listBox(label, imguiScratchImInt, items);
        int clamped = Math.min(Math.max(imguiScratchImInt.get(), 0), items.length - 1);
        return clamped + 1;
    }

    @Override
    public void imguiImage(String textureId, float width, float height) {
        requireImGuiFrame("Image");
        if (width <= 0 || height <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] Image skipped: width and height must be positive.");
            return;
        }
        int glId = resolveImGuiTextureId(textureId);
        if (glId < 0) {
            return;
        }
        // No V flip: ImGui maps uv0 to the quad's top-left, vanilla GUI blits
        // map (u0,v0) to the top-left too, and the first uploaded row is the
        // PNG's top row in both — so default UVs render upright. Visually
        // UNVERIFIED in-game (headless CI cannot open a GL context); if the
        // in-game check shows an inverted image, flip to (0,1)-(1,0) here.
        imgui.ImGui.image(glId, width, height);
    }

    @Override
    public void imguiImageSub(String textureId, float width, float height,
                              float u0, float v0, float u1, float v1) {
        requireImGuiFrame("ImageSub");
        if (width <= 0 || height <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] ImageSub skipped: width and height must be positive.");
            return;
        }
        int glId = resolveImGuiTextureId(textureId);
        if (glId < 0) {
            return;
        }
        // Raw pass-through: these are the exact UVs vanilla sampling uses, so
        // atlas regions keep the orientation the game gives them.
        imgui.ImGui.image(glId, width, height, u0, v0, u1, v1);
    }

    /**
     * Resolves a registry texture to its GL id, loading it on first use.
     * Vanilla {@code getTexture} auto-registers a {@code SimpleTexture} and
     * uploads synchronously on the calling (render) thread — one hitch per
     * unique texture, then cached. Returns -1 for unknown ids (one debug line
     * per id so broken paths are visible without spamming the log).
     */
    private int resolveImGuiTextureId(String textureId) {
        if (textureId == null || textureId.isBlank() || !textureServicesReady()) {
            return -1;
        }
        ResourceLocation location = ResourceLocation.tryParse(textureId);
        if (location == null || !textureLoaded(location)) {
            warnUnknownTexture(textureId, "unknown texture");
            return -1;
        }
        int glId = Minecraft.getInstance().getTextureManager().getTexture(location).getId();
        if (glId < 0) {
            warnUnknownTexture(textureId, "texture has no GL id yet");
        }
        return glId;
    }

    private static boolean textureServicesReady() {
        return Minecraft.getInstance().getTextureManager() != null
                && Minecraft.getInstance().getResourceManager() != null;
    }

    private static boolean textureLoaded(@org.jetbrains.annotations.NotNull ResourceLocation location) {
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
    }

    /** Debug-logs once per unknown id so broken paths stay visible without spamming the log. */
    private void warnUnknownTexture(@org.jetbrains.annotations.NotNull String textureId,
                                    @org.jetbrains.annotations.NotNull String reason) {
        if (warnedUnknownImGuiTextures.add(textureId)) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] Image skipped: " + reason + " '" + textureId + "'");
        }
    }

    @Override
    public @org.jetbrains.annotations.Nullable java.util.List<Double> imguiSpriteUv(String atlasId, String spriteId) {
        requireImGuiFrame("GetSpriteUv");
        if (Minecraft.getInstance() == null || Minecraft.getInstance().getModelManager() == null) {
            return null;
        }
        ResourceLocation atlasLocation =
                ResourceLocation.tryParse(atlasId);
        ResourceLocation spriteLocation =
                ResourceLocation.tryParse(spriteId);
        if (atlasLocation == null || spriteLocation == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] GetSpriteUv skipped: malformed atlas or sprite id.");
            return null;
        }
        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite =
                ImGuiNativeGuard.supply(
                        () -> Minecraft.getInstance().getModelManager().getAtlas(atlasLocation).getSprite(spriteLocation),
                        null,
                        ImGuiNativeGuard.debugLogger(
                                () -> "[ImGui] GetSpriteUv failed for '" + spriteId + "'"));
        if (sprite == null) {
            return null;
        }
        // Missing sprites resolve to the missing-texture sprite (never null),
        // so callers always get a drawable box — check the pixels, not nil.
        return java.util.List.of((double) sprite.getU0(), (double) sprite.getV0(),
                (double) sprite.getU1(), (double) sprite.getV1());
    }

    @Override
    public void imguiOpenPopup(String id) {
        requireImGuiFrame("OpenPopup");
        imgui.ImGui.openPopup(id);
    }

    @Override
    public boolean imguiBeginPopup(String id) {
        requireImGuiFrame("BeginPopup");
        // Pushes ONLY on true — EndPopup runs only for open popups.
        boolean open = imgui.ImGui.beginPopup(id);
        if (open) {
            imguiPopupDepth++;
        }
        return open;
    }

    @Override
    public void imguiEndPopup() {
        requireImGuiFrame("EndPopup");
        if (imguiPopupDepth <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] EndPopup() with no open popup; call ignored"
                    + " (call EndPopup only inside 'if BeginPopup then ... end').");
            return;
        }
        imguiPopupDepth--;
        imgui.ImGui.endPopup();
    }

    @Override
    public boolean imguiBeginPopupModal(String title, boolean open) {
        requireImGuiFrame("BeginPopupModal");
        imgui.type.ImBoolean state = new imgui.type.ImBoolean(open);
        boolean shown = imgui.ImGui.beginPopupModal(title, state);
        if (shown) {
            imguiPopupDepth++;
        }
        return state.get();
    }

    @Override
    public void imguiCloseCurrentPopup() {
        requireImGuiFrame("CloseCurrentPopup");
        imgui.ImGui.closeCurrentPopup();
    }

    @Override
    public boolean imguiBeginPopupContextItem(String id) {
        requireImGuiFrame("BeginPopupContextItem");
        boolean open = id == null || id.isEmpty()
                ? imgui.ImGui.beginPopupContextItem()
                : imgui.ImGui.beginPopupContextItem(id);
        if (open) {
            imguiPopupDepth++;
        }
        return open;
    }

    @Override
    public void imguiOpenPopupOnItemClick(String id) {
        requireImGuiFrame("OpenPopupOnItemClick");
        if (id == null || id.isEmpty()) {
            imgui.ImGui.openPopupOnItemClick();
        } else {
            imgui.ImGui.openPopupOnItemClick(id);
        }
    }

    @Override
    public void imguiBeginTooltip() {
        requireImGuiFrame("BeginTooltip");
        imgui.ImGui.beginTooltip();
        imguiTooltipDepth++;
    }

    @Override
    public void imguiEndTooltip() {
        requireImGuiFrame("EndTooltip");
        if (imguiTooltipDepth <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] EndTooltip() with no open tooltip; call ignored.");
            return;
        }
        imguiTooltipDepth--;
        imgui.ImGui.endTooltip();
    }

    @Override
    public void imguiSetTooltip(String text) {
        requireImGuiFrame("SetTooltip");
        imgui.ImGui.setTooltip(text);
    }

    @Override
    public void imguiBeginDisabled(boolean disabled) {
        requireImGuiFrame("BeginDisabled");
        imgui.ImGui.beginDisabled(disabled);
        imguiDisabledDepth++;
    }

    @Override
    public void imguiEndDisabled() {
        requireImGuiFrame("EndDisabled");
        if (imguiDisabledDepth <= 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ImGui] EndDisabled() with no open scope; call ignored.");
            return;
        }
        imguiDisabledDepth--;
        imgui.ImGui.endDisabled();
    }

    @Override
    public void imguiSetScrollHereY() {
        requireImGuiFrame("SetScrollHereY");
        imgui.ImGui.setScrollHereY();
    }

    @Override
    public void imguiSetKeyboardFocusHere() {
        requireImGuiFrame("SetKeyboardFocusHere");
        imgui.ImGui.setKeyboardFocusHere();
    }

    @Override
    public void imguiSetNextItemOpen(boolean open) {
        requireImGuiFrame("SetNextItemOpen");
        imgui.ImGui.setNextItemOpen(open);
    }

    @Override
    public void imguiTextColored(float r, float g, float b, String message) {
        requireImGuiFrame("TextColored");
        imgui.ImGui.textColored(r, g, b, 1.0f, message);
    }

    @Override
    public void imguiTextWrapped(String message) {
        requireImGuiFrame("TextWrapped");
        imgui.ImGui.textWrapped(message);
    }

    @Override
    public void imguiTextDisabled(String message) {
        requireImGuiFrame("TextDisabled");
        imgui.ImGui.textDisabled(message);
    }

    @Override
    public void imguiLabelText(String label, String value) {
        requireImGuiFrame("LabelText");
        imgui.ImGui.labelText(label, value);
    }

    @Override
    public boolean imguiArrowButton(String id, String direction) {
        requireImGuiFrame("ArrowButton");
        return imgui.ImGui.arrowButton(id, parseImGuiArrowDir(direction));
    }

    @Override
    public boolean imguiTabItemButton(String label) {
        requireImGuiFrame("TabItemButton");
        return imgui.ImGui.tabItemButton(label);
    }

    @Override
    public boolean imguiBeginMainMenuBar() {
        requireImGuiFrame("BeginMainMenuBar");
        return imgui.ImGui.beginMainMenuBar();
    }

    @Override
    public void imguiEndMainMenuBar() {
        requireImGuiFrame("EndMainMenuBar");
        imgui.ImGui.endMainMenuBar();
    }

    @Override
    public void imguiPushItemWidth(float width) {
        requireImGuiFrame("PushItemWidth");
        imgui.ImGui.pushItemWidth(width);
        imguiItemWidthDepth++;
    }

    @Override
    public void imguiPopItemWidth() {
        requireImGuiFrame("PopItemWidth");
        if (imguiItemWidthDepth <= 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ImGui] PopItemWidth() with no pushed width; call ignored.");
            return;
        }
        imguiItemWidthDepth--;
        imgui.ImGui.popItemWidth();
    }

    @Override
    public void imguiSetNextItemWidth(float width) {
        requireImGuiFrame("SetNextItemWidth");
        imgui.ImGui.setNextItemWidth(width);
    }

    @Override
    public boolean imguiBeginCombo(String label, String preview) {
        requireImGuiFrame("BeginCombo");
        // Pushes ONLY on true, like menus and tab items.
        boolean open = imgui.ImGui.beginCombo(label, preview);
        if (open) {
            imguiComboDepth++;
        }
        return open;
    }

    @Override
    public void imguiEndCombo() {
        requireImGuiFrame("EndCombo");
        if (imguiComboDepth <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] EndCombo() with no open combo; call ignored"
                    + " (call EndCombo only inside 'if BeginCombo then ... end').");
            return;
        }
        imguiComboDepth--;
        imgui.ImGui.endCombo();
    }

    @Override
    public void imguiPlotLines(String label, float[] values) {
        requireImGuiFrame("PlotLines");
        if (values == null || values.length == 0) {
            return;
        }
        imgui.ImGui.plotLines(label, values, values.length);
    }

    @Override
    public void imguiPlotHistogram(String label, float[] values) {
        requireImGuiFrame("PlotHistogram");
        if (values == null || values.length == 0) {
            return;
        }
        imgui.ImGui.plotHistogram(label, values, values.length);
    }

    @Override
    public String imguiInputTextFlags(String label, String current, String flagsCsv) {
        requireImGuiFrame("InputTextFlags");
        String seed = current == null ? "" : current;
        imgui.type.ImString buffer = new imgui.type.ImString(seed, Math.max(seed.length() + 128, 256));
        imgui.ImGui.inputText(label, buffer,
                cachedFlags(imguiInputFlagCache, flagsCsv, NeoForgeClientPlatform::parseImGuiInputFlags));
        return buffer.get();
    }

    @Override
    public boolean imguiIsItemActive() {
        requireImGuiFrame("IsItemActive");
        return imgui.ImGui.isItemActive();
    }

    @Override
    public boolean imguiIsItemFocused() {
        requireImGuiFrame("IsItemFocused");
        return imgui.ImGui.isItemFocused();
    }

    @Override
    public boolean imguiIsItemVisible() {
        requireImGuiFrame("IsItemVisible");
        return imgui.ImGui.isItemVisible();
    }

    @Override
    public boolean imguiIsItemEdited() {
        requireImGuiFrame("IsItemEdited");
        return imgui.ImGui.isItemEdited();
    }

    @Override
    public java.util.List<Double> imguiCalcTextSize(String text) {
        requireImGuiFrame("CalcTextSize");
        return java.util.List.of((double) imgui.ImGui.calcTextSizeX(text), (double) imgui.ImGui.calcTextSizeY(text));
    }

    @Override
    public java.util.List<Double> imguiGetItemRectSize() {
        requireImGuiFrame("GetItemRectSize");
        return java.util.List.of((double) imgui.ImGui.getItemRectSizeX(), (double) imgui.ImGui.getItemRectSizeY());
    }

    @Override
    public boolean imguiBeginDragDropSource() {
        requireImGuiFrame("BeginDragDropSource");
        // Pushes ONLY on true — EndDragDropSource runs only while dragging.
        boolean dragging = imgui.ImGui.beginDragDropSource();
        if (dragging) {
            imguiDragSourceDepth++;
        }
        return dragging;
    }

    @Override
    public boolean imguiSetDragDropPayload(String type, String data) {
        requireImGuiFrame("SetDragDropPayload");
        return imgui.ImGui.setDragDropPayload(type, data);
    }

    @Override
    public void imguiEndDragDropSource() {
        requireImGuiFrame("EndDragDropSource");
        if (imguiDragSourceDepth <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] EndDragDropSource() with no active source; call ignored."
                    + " (call EndDragDropSource only inside 'if BeginDragDropSource then ... end').");
            return;
        }
        imguiDragSourceDepth--;
        imgui.ImGui.endDragDropSource();
    }

    @Override
    public boolean imguiBeginDragDropTarget() {
        requireImGuiFrame("BeginDragDropTarget");
        boolean hovered = imgui.ImGui.beginDragDropTarget();
        if (hovered) {
            imguiDragTargetDepth++;
        }
        return hovered;
    }

    @Override
    public String imguiAcceptDragDropPayload(String type) {
        requireImGuiFrame("AcceptDragDropPayload");
        // String payloads only: Lua sends strings, Lua receives strings.
        // Foreign payload families return null instead of throwing.
        return ImGuiNativeGuard.supply(
                () -> imgui.ImGui.<String>acceptDragDropPayload(type, String.class),
                null,
                ImGuiNativeGuard.debugLogger(
                        () -> "[ImGui] AcceptDragDropPayload('" + type + "') miss"));
    }

    @Override
    public void imguiEndDragDropTarget() {
        requireImGuiFrame("EndDragDropTarget");
        if (imguiDragTargetDepth <= 0) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[ImGui] EndDragDropTarget() with no active target; call ignored."
                    + " (call EndDragDropTarget only inside 'if BeginDragDropTarget then ... end').");
            return;
        }
        imguiDragTargetDepth--;
        imgui.ImGui.endDragDropTarget();
    }

    private final java.util.Set<String> warnedUnknownImGuiTextures = new java.util.HashSet<>();

    /**
     * Name-to-bit mapping using the real Dear ImGui constants (no hardcoded
     * bits: the values are ABI and must come from the binding). Unknown names
     * fail fast with the valid list, mirroring the KeyNames pattern.
     */
    private static final String WINDOW_FLAG_VALID =
            "None,NoTitleBar,NoResize,NoMove,NoScrollbar,NoCollapse,AlwaysAutoResize,NoBackground,"
            + "NoMouseInputs,MenuBar,HorizontalScrollbar,NoFocusOnAppearing,NoBringToFrontOnFocus,"
            + "AlwaysVerticalScrollbar,AlwaysHorizontalScrollbar,NoNavInputs,NoDecoration";
    private static final String TABLE_FLAG_VALID =
            "None,Resizable,Reorderable,Hideable,Sortable,RowBg,Borders,"
            + "SizingFixedFit,SizingStretchProp,ScrollX,ScrollY,NoSavedSettings";
    private static final String TABLE_COLUMN_FLAG_VALID =
            "None,WidthFixed,WidthStretch,NoResize,NoReorder,NoHide,NoSort,NoHeaderLabel";
    private static final String INPUT_FLAG_VALID =
            "None,CharsDecimal,CharsHexadecimal,Password,NoUndoRedo";
    private static final String COLOR_VALID =
            "Text,TextDisabled,WindowBg,Button,ButtonHovered,ButtonActive,FrameBg,FrameBgHovered,"
            + "FrameBgActive,Header,HeaderHovered,HeaderActive,CheckMark,SliderGrab,SliderGrabActive,"
            + "TitleBg,TitleBgActive,Separator,Border";

    /** Name-to-constant tables: a lookup keeps the parser branch-free as the flag set grows. */
    private static final Map<String, Integer> WINDOW_FLAGS = ImGuiFlagTables.windowFlags();
    private static final Map<String, Integer> TABLE_FLAGS = ImGuiFlagTables.tableFlags();
    private static final Map<String, Integer> TABLE_COLUMN_FLAGS = ImGuiFlagTables.tableColumnFlags();
    private static final Map<String, Integer> INPUT_FLAGS = ImGuiFlagTables.inputFlags();
    private static final Map<String, Integer> IMGUI_COLORS = ImGuiFlagTables.colors();

    static int parseImGuiWindowFlags(String csv) {
        return parseFlagCsv(csv, WINDOW_FLAGS, "ImGui:Begin unknown window flag '", WINDOW_FLAG_VALID);
    }

    static int parseImGuiTableFlags(String csv) {
        return parseFlagCsv(csv, TABLE_FLAGS, "ImGui:BeginTable unknown table flag '", TABLE_FLAG_VALID);
    }

    /**
     * Column flags use the real {@code ImGuiTableColumnFlags} constants.
     * Same fail-fast contract as the other flag parsers.
     */
    static int parseImGuiTableColumnFlags(String csv) {
        return parseFlagCsv(csv, TABLE_COLUMN_FLAGS,
                "ImGui:TableSetupColumn unknown column flag '", TABLE_COLUMN_FLAG_VALID);
    }

    /** Input-text flags use the real {@code ImGuiInputTextFlags} constants. */
    static int parseImGuiInputFlags(String csv) {
        return parseFlagCsv(csv, INPUT_FLAGS, "ImGui:InputTextFlags unknown flag '", INPUT_FLAG_VALID);
    }

    /**
     * Shared CSV-to-bitmask parser. Blank input is {@code 0}; an unknown name fails fast with the
     * command-specific prefix plus the full list of accepted names.
     */
    private static int parseFlagCsv(@org.jetbrains.annotations.Nullable String csv,
                                    @org.jetbrains.annotations.NotNull Map<String, Integer> lookup,
                                    @org.jetbrains.annotations.NotNull String unknownPrefix,
                                    @org.jetbrains.annotations.NotNull String validList) {
        if (csv == null || csv.isBlank()) {
            return 0;
        }
        int bits = 0;
        for (String raw : csv.split(",")) {
            String name = raw.strip();
            if (name.isEmpty()) {
                continue;
            }
            Integer flag = lookup.get(name);
            if (flag == null) {
                throw new IllegalArgumentException(unknownPrefix + name + "'. Valid: " + validList + ".");
            }
            bits |= flag;
        }
        return bits;
    }

    /** Arrow directions use the real {@code ImGuiDir} constants. */
    static int parseImGuiArrowDir(String name) {
        if (name == null) {
            throw new IllegalArgumentException(
                    "ImGui:ArrowButton needs a direction: Left,Right,Up,Down.");
        }
        return switch (name.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "left" -> imgui.flag.ImGuiDir.Left;
            case "right" -> imgui.flag.ImGuiDir.Right;
            case "up" -> imgui.flag.ImGuiDir.Up;
            case "down" -> imgui.flag.ImGuiDir.Down;
            default -> throw new IllegalArgumentException(
                    "ImGui:ArrowButton unknown direction '" + name + "'."
                    + " Valid: Left,Right,Up,Down.");
        };
    }

    @Override
    public void imguiSetScreen(boolean open) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            if (open) {
                if (!(mc.screen instanceof com.luatweaker.platform.client.gui.imgui.ImGuiOverlayScreen)) {
                    mc.setScreen(new com.luatweaker.platform.client.gui.imgui.ImGuiOverlayScreen());
                }
            } else {
                if (mc.screen instanceof com.luatweaker.platform.client.gui.imgui.ImGuiOverlayScreen) {
                    mc.setScreen(null);
                }
            }
        });
    }

    @Override
    public boolean imguiIsScreenOpen() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.screen instanceof com.luatweaker.platform.client.gui.imgui.ImGuiOverlayScreen;
    }

    @Override
    public boolean imguiToggleScreen() {
        boolean nextState = !imguiIsScreenOpen();
        imguiSetScreen(nextState);
        return nextState;
    }

    @Override
    public boolean imguiInvisibleButton(String id, double width, double height) {
        requireImGuiFrame("InvisibleButton");
        return imgui.ImGui.invisibleButton(id == null ? "" : id, (float) width, (float) height);
    }

    @Override
    public double imguiGetMousePosX() {
        requireImGuiFrame("GetMousePosX");
        return imgui.ImGui.getMousePosX();
    }

    @Override
    public double imguiGetMousePosY() {
        requireImGuiFrame("GetMousePosY");
        return imgui.ImGui.getMousePosY();
    }

    @Override
    public double imguiGetCursorPosX() {
        requireImGuiFrame("GetCursorPosX");
        return imgui.ImGui.getCursorPosX();
    }

    @Override
    public double imguiGetCursorPosY() {
        requireImGuiFrame("GetCursorPosY");
        return imgui.ImGui.getCursorPosY();
    }

    @Override
    public void imguiSetCursorPos(double x, double y) {
        requireImGuiFrame("SetCursorPos");
        imgui.ImGui.setCursorPos((float) x, (float) y);
    }

    @Override
    public double imguiGetWindowPosX() {
        requireImGuiFrame("GetWindowPosX");
        return imgui.ImGui.getWindowPosX();
    }

    @Override
    public double imguiGetWindowPosY() {
        requireImGuiFrame("GetWindowPosY");
        return imgui.ImGui.getWindowPosY();
    }

    @Override
    public double imguiGetWindowWidth() {
        requireImGuiFrame("GetWindowWidth");
        return imgui.ImGui.getWindowWidth();
    }

    @Override
    public double imguiGetWindowHeight() {
        requireImGuiFrame("GetWindowHeight");
        return imgui.ImGui.getWindowHeight();
    }

    /**
     * Name-to-constant mapping using the real ImGuiCol values. Same fail-fast
     * contract as the window flags.
     */
    static int parseImGuiColor(String name) {
        if (name == null) {
            throw new IllegalArgumentException("ImGui:PushStyleColor needs a color name.");
        }
        Integer color = IMGUI_COLORS.get(name);
        if (color == null) {
            throw new IllegalArgumentException(
                    "ImGui:PushStyleColor unknown color '" + name + "'. Valid: " + COLOR_VALID + ".");
        }
        return color;
    }
}
