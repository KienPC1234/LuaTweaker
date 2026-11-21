package com.luatweaker.client;

import com.luatweaker.api.client.IShaderService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.pal.Platform;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

abstract class ShaderServiceBase1 implements IShaderService {


    /** Named camera/overlay effects, alphabetical (also the order used in loud errors). */

    static final Pattern PASS_ID = Pattern.compile("^[a-z0-9_]{1,48}$");
    static final int MAX_FRAGMENT_CHARS = 16_384;
    static final int MAX_SPIRV_BASE64_CHARS = 360_000;
    static final int MAX_REGISTERED_PASSES = 16;
    static final int RGB_HEX_LENGTH = 6;
    static final int ARGB_HEX_LENGTH = 8;
    static final int HEX_RADIX = 16;
    static final long IDLE_TIME_MS = 0L;
    static final double ZERO_SECONDS = 0.0;
    static final double MILLIS_PER_SECOND = 1000.0;

    static final String PARAM_STRENGTH = "strength";
    static final String PARAM_SECONDS = "seconds";
    static final String PARAM_COLOR = "color";
    static final String ERR_SPIRV = "Shaders:registerSpirvPass('";
    static final String ERR_SET_EFFECT = "Shaders:setEffect('";
    static final String ERR_PARAM = "') param '";
    static final String JSON_SUFFIX = ".json";

    static final double KICK_DEFAULT_STRENGTH = 3.0;
    static final double KICK_DEFAULT_SECONDS = 0.3;
    static final double ROLL_DEFAULT_DEGREES = 10.0;
    static final double ROLL_DEFAULT_SECONDS = 0.6;
    static final double SWAY_DEFAULT_AMPLITUDE = 1.5;
    static final double SWAY_DEFAULT_SECONDS = 3.0;
    static final double FOV_DEFAULT_AMOUNT = 12.0;
    static final double FOV_DEFAULT_SECONDS = 0.4;

    // Camera one-shots: start 0 = idle. Written on the Lua thread, read per frame.
    static final AtomicReference<Long> KICK_START_MS = new AtomicReference<>(0L);
    static final AtomicReference<Double> KICK_STRENGTH = new AtomicReference<>(0.0);
    static final AtomicReference<Double> KICK_DIRECTION_DEG = new AtomicReference<>(0.0);
    static final AtomicReference<Double> KICK_SECONDS = new AtomicReference<>(0.0);
    static final AtomicReference<Long> ROLL_START_MS = new AtomicReference<>(0L);
    static final AtomicReference<Double> ROLL_DEGREES = new AtomicReference<>(0.0);
    static final AtomicReference<Double> ROLL_SECONDS = new AtomicReference<>(0.0);
    static final AtomicReference<Long> SWAY_START_MS = new AtomicReference<>(0L);
    static final AtomicReference<Double> SWAY_AMPLITUDE = new AtomicReference<>(0.0);
    static final AtomicReference<Double> SWAY_SECONDS = new AtomicReference<>(0.0);
    static final AtomicReference<Long> FOV_START_MS = new AtomicReference<>(0L);
    static final AtomicReference<Double> FOV_AMOUNT = new AtomicReference<>(0.0);
    static final AtomicReference<Double> FOV_SECONDS = new AtomicReference<>(0.0);

    // Persistent overlays. Strengths are 0..1; 0 = off.
    static final AtomicReference<Long> FADE_START_MS = new AtomicReference<>(0L);
    static final AtomicReference<Long> FADE_RAMP_MS = new AtomicReference<>(0L);
    static final AtomicReference<Integer> FADE_ARGB = new AtomicReference<>(0);
    static final AtomicReference<Boolean> FADE_ACTIVE = new AtomicReference<>(false);
    static final AtomicReference<Double> LETTERBOX_HEIGHT = new AtomicReference<>(0.0);
    static final AtomicReference<Double> VIGNETTE_STRENGTH = new AtomicReference<>(0.0);
    static final AtomicReference<Integer> VIGNETTE_ARGB = new AtomicReference<>(0xFF000000);
    static final AtomicReference<Double> TINT_STRENGTH = new AtomicReference<>(0.0);
    static final AtomicReference<Integer> TINT_ARGB = new AtomicReference<>(0xFFFFFFFF);
    static final AtomicReference<Double> PULSE_STRENGTH = new AtomicReference<>(0.0);
    static final AtomicReference<Integer> PULSE_ARGB = new AtomicReference<>(0xFF880000);
    static final AtomicReference<Double> FROST_STRENGTH = new AtomicReference<>(0.0);
    static final AtomicReference<Double> BLOOD_STRENGTH = new AtomicReference<>(0.0);

    /** Registered pass id -> chain location (e.g. "luatweaker:shaders/post/lt_warp.json"). */
    static final Map<String, String> PASS_REGISTRY = new ConcurrentHashMap<>();
    static final AtomicReference<String> ACTIVE_PASS_ID = new AtomicReference<>("");

    final IDatapackService datapackService;
    final File cacheDir;


















    // ------------------------------------------------------------------
    // Render-thread readers (called every frame by the platform hook)
    // ------------------------------------------------------------------

    /**
     * Combined camera delta for this frame: {yaw, pitch, roll, fovAdd}.
     * Returns null when every one-shot has expired (zero per-frame cost path).
     */

    /** Mutable per-frame accumulator; only a render-thread local, never shared. */
    static final class CameraDelta {
        float yaw;
        float pitch;
        float roll;
        float fov;
        boolean any;
    }





    /** Current fade color with ramp applied, or 0 when no fade is showing. */










    // ------------------------------------------------------------------
    // File generation (pure string building, no new deps)
    // ------------------------------------------------------------------




    // ------------------------------------------------------------------
    // Params, colors, hashing, cache
    // ------------------------------------------------------------------

    protected ShaderServiceBase1(@NotNull IDatapackService datapackService, @NotNull File cacheDir) {
        this.datapackService = datapackService;
        this.cacheDir = cacheDir;
    }

    static final List<String> DIRECT_EFFECTS = List.of(
            "bloodSplatter", "fadeTo", "fovPunch", "frost", "kick",
            "letterbox", "lowHealthPulse", "roll", "sway", "tint", "vignette");

    /** Every setEffect/clearEffect name: direct effects plus shader-backed builtins. */
    public static final List<String> SUPPORTED_EFFECTS = buildSupportedEffects();

    static List<String> buildSupportedEffects() {
        List<String> all = new ArrayList<>(DIRECT_EFFECTS);
        List<String> builtins = new ArrayList<>(BuiltinShaderPasses.ALL.keySet());
        Collections.sort(builtins);
        all.addAll(builtins);
        Collections.sort(all);
        return List.copyOf(all);
    }

    @Override
    public @NotNull String getBackend() {
        return Platform.getClientOrNull() != null ? "opengl" : "unknown";
    }

    @Override
    public boolean supportsCustomPasses() {
        return Platform.getClientOrNull() != null;
    }

    void checkPassId(@NotNull String id) {
        if (id == null || !PASS_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Shader pass id must match [a-z0-9_]{1,48}, got '" + id + "'.");
        }
        if (PASS_REGISTRY.size() >= MAX_REGISTERED_PASSES && !PASS_REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException(
                    "Shader pass '" + id + "' rejected: already holding "
                            + MAX_REGISTERED_PASSES + " passes.");
        }
    }

    static String buildChainJson(String id) {
        return "{\n"
                + "  \"targets\": [\"swap\"],\n"
                + "  \"passes\": [\n"
                + "    {\"name\": \"luatweaker:lt_" + id + "\","
                + " \"intarget\": \"minecraft:main\", \"outtarget\": \"swap\", \"uniforms\": []},\n"
                + "    {\"name\": \"minecraft:blit\","
                + " \"intarget\": \"swap\", \"outtarget\": \"minecraft:main\"}\n"
                + "  ]\n"
                + "}\n";
    }
}
