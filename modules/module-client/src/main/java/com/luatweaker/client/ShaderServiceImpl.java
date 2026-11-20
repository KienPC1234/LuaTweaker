package com.luatweaker.client;

import com.luatweaker.api.client.IShaderService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * {@link IShaderService} implementation: pure-Java state, validation and file
 * generation. All real rendering happens platform-side: camera/overlay effects
 * are consumed by the client's render hook every frame, custom passes run
 * through the vanilla post pipeline.
 *
 * <p>Render-thread state lives in intentional static volatiles (same pattern as
 * {@link CameraServiceImpl}): Lua and reloads replace the single service
 * instance, but the render thread must keep reading one stable location.</p>
 */
public class ShaderServiceImpl implements IShaderService {

    /** Named camera/overlay effects, alphabetical (also the order used in loud errors). */
    private static final List<String> DIRECT_EFFECTS = List.of(
            "bloodSplatter", "fadeTo", "fovPunch", "frost", "kick",
            "letterbox", "lowHealthPulse", "roll", "sway", "tint", "vignette");

    /** Every setEffect/clearEffect name: direct effects plus shader-backed builtins. */
    public static final List<String> SUPPORTED_EFFECTS = buildSupportedEffects();

    private static List<String> buildSupportedEffects() {
        List<String> all = new ArrayList<>(DIRECT_EFFECTS);
        List<String> builtins = new ArrayList<>(BuiltinShaderPasses.ALL.keySet());
        Collections.sort(builtins);
        all.addAll(builtins);
        Collections.sort(all);
        return List.copyOf(all);
    }

    private static final Pattern PASS_ID = Pattern.compile("^[a-z0-9_]{1,48}$");
    private static final int MAX_FRAGMENT_CHARS = 16384;
    private static final int MAX_REGISTERED_PASSES = 16;

    private static final double KICK_DEFAULT_STRENGTH = 3.0;
    private static final double KICK_DEFAULT_SECONDS = 0.3;
    private static final double ROLL_DEFAULT_DEGREES = 10.0;
    private static final double ROLL_DEFAULT_SECONDS = 0.6;
    private static final double SWAY_DEFAULT_AMPLITUDE = 1.5;
    private static final double SWAY_DEFAULT_SECONDS = 3.0;
    private static final double FOV_DEFAULT_AMOUNT = 12.0;
    private static final double FOV_DEFAULT_SECONDS = 0.4;

    // Camera one-shots: start 0 = idle. Written on the Lua thread, read per frame.
    private static volatile long kickStartMs;
    private static volatile double kickStrength;
    private static volatile double kickDirectionDeg;
    private static volatile double kickSeconds;
    private static volatile long rollStartMs;
    private static volatile double rollDegrees;
    private static volatile double rollSeconds;
    private static volatile long swayStartMs;
    private static volatile double swayAmplitude;
    private static volatile double swaySeconds;
    private static volatile long fovStartMs;
    private static volatile double fovAmount;
    private static volatile double fovSeconds;

    // Persistent overlays. Strengths are 0..1; 0 = off.
    private static volatile long fadeStartMs;
    private static volatile long fadeRampMs;
    private static volatile int fadeArgb;
    private static volatile boolean fadeActive;
    private static volatile double letterboxHeight;
    private static volatile double vignetteStrength;
    private static volatile int vignetteArgb = 0xFF000000;
    private static volatile double tintStrength;
    private static volatile int tintArgb = 0xFFFFFFFF;
    private static volatile double pulseStrength;
    private static volatile int pulseArgb = 0xFF880000;
    private static volatile double frostStrength;
    private static volatile double bloodStrength;

    /** Registered pass id -> chain location (e.g. "luatweaker:shaders/post/lt_warp.json"). */
    private static final Map<String, String> PASS_REGISTRY = new ConcurrentHashMap<>();
    private static volatile String activePassId;

    private final IDatapackService datapackService;
    private final File cacheDir;

    public ShaderServiceImpl(@NotNull IDatapackService datapackService, @NotNull File cacheDir) {
        this.datapackService = datapackService;
        this.cacheDir = cacheDir;
    }

    @Override
    public @NotNull String getBackend() {
        return Platform.getClientOrNull() != null ? "opengl" : "unknown";
    }

    @Override
    public boolean supportsCustomPasses() {
        return Platform.getClientOrNull() != null;
    }

    @Override
    public void setEffect(@NotNull String name, @Nullable ILuaTable params) {
        if (name == null || !SUPPORTED_EFFECTS.contains(name)) {
            throw new IllegalArgumentException(
                    "Shaders:setEffect unknown effect '" + name + "'. Supported: "
                            + String.join(", ", SUPPORTED_EFFECTS));
        }
        BuiltinShaderPasses.BuiltinPass builtin = BuiltinShaderPasses.ALL.get(name);
        if (builtin != null) {
            applyBuiltin(builtin, params);
            return;
        }
        switch (name) {
            case "kick" -> {
                kickStrength = readNumber(params, "strength", KICK_DEFAULT_STRENGTH, 0.0, 20.0, name);
                kickDirectionDeg = readNumber(params, "direction", 0.0, -180.0, 180.0, name);
                kickSeconds = readNumber(params, "seconds", KICK_DEFAULT_SECONDS, 0.05, 3.0, name);
                kickStartMs = System.currentTimeMillis();
            }
            case "roll" -> {
                rollDegrees = readNumber(params, "degrees", ROLL_DEFAULT_DEGREES, -45.0, 45.0, name);
                rollSeconds = readNumber(params, "seconds", ROLL_DEFAULT_SECONDS, 0.05, 3.0, name);
                rollStartMs = System.currentTimeMillis();
            }
            case "sway" -> {
                swayAmplitude = readNumber(params, "amplitude", SWAY_DEFAULT_AMPLITUDE, 0.0, 10.0, name);
                swaySeconds = readNumber(params, "seconds", SWAY_DEFAULT_SECONDS, 0.5, 30.0, name);
                swayStartMs = System.currentTimeMillis();
            }
            case "fovPunch" -> {
                fovAmount = readNumber(params, "amount", FOV_DEFAULT_AMOUNT, -30.0, 30.0, name);
                fovSeconds = readNumber(params, "seconds", FOV_DEFAULT_SECONDS, 0.05, 3.0, name);
                fovStartMs = System.currentTimeMillis();
            }
            case "fadeTo" -> {
                fadeArgb = readColor(params, "color", 0xFF000000, name);
                fadeRampMs = (long) (readNumber(params, "seconds", 0.5, 0.05, 10.0, name) * 1000.0);
                fadeStartMs = System.currentTimeMillis();
                fadeActive = true;
            }
            case "letterbox" -> letterboxHeight = readNumber(params, "height", 0.12, 0.0, 0.4, name);
            case "vignette" -> {
                vignetteStrength = readNumber(params, "strength", 0.6, 0.0, 1.0, name);
                vignetteArgb = readColor(params, "color", 0xFF000000, name);
            }
            case "tint" -> {
                tintStrength = readNumber(params, "strength", 0.3, 0.0, 1.0, name);
                tintArgb = readColor(params, "color", 0xFFFFFFFF, name);
            }
            case "lowHealthPulse" -> {
                pulseStrength = readNumber(params, "strength", 0.7, 0.0, 1.0, name);
                pulseArgb = readColor(params, "color", 0xFF880000, name);
            }
            case "frost" -> frostStrength = readNumber(params, "strength", 0.5, 0.0, 1.0, name);
            case "bloodSplatter" -> bloodStrength = readNumber(params, "strength", 0.6, 0.0, 1.0, name);
            default -> throw new IllegalArgumentException(
                    "Shaders:setEffect unknown effect '" + name + "'. Supported: "
                            + String.join(", ", SUPPORTED_EFFECTS));
        }
    }

    @Override
    public void clearEffect(@NotNull String name) {
        if (name == null || !SUPPORTED_EFFECTS.contains(name)) {
            throw new IllegalArgumentException(
                    "Shaders:clearEffect unknown effect '" + name + "'. Supported: "
                            + String.join(", ", SUPPORTED_EFFECTS));
        }
        BuiltinShaderPasses.BuiltinPass builtin = BuiltinShaderPasses.ALL.get(name);
        if (builtin != null) {
            if (builtin.id().equals(activePassId)) {
                deactivatePass();
            }
            return;
        }
        switch (name) {
            case "kick" -> kickStartMs = 0L;
            case "roll" -> rollStartMs = 0L;
            case "sway" -> swayStartMs = 0L;
            case "fovPunch" -> fovStartMs = 0L;
            case "fadeTo" -> fadeActive = false;
            case "letterbox" -> letterboxHeight = 0.0;
            case "vignette" -> vignetteStrength = 0.0;
            case "tint" -> tintStrength = 0.0;
            case "lowHealthPulse" -> pulseStrength = 0.0;
            case "frost" -> frostStrength = 0.0;
            case "bloodSplatter" -> bloodStrength = 0.0;
            default -> throw new IllegalArgumentException(
                    "Shaders:clearEffect unknown effect '" + name + "'.");
        }
    }

    @Override
    public void clearAll() {
        kickStartMs = 0L;
        rollStartMs = 0L;
        swayStartMs = 0L;
        fovStartMs = 0L;
        fadeActive = false;
        letterboxHeight = 0.0;
        vignetteStrength = 0.0;
        tintStrength = 0.0;
        pulseStrength = 0.0;
        frostStrength = 0.0;
        bloodStrength = 0.0;
        deactivatePass();
    }

    @Override
    public void registerGlslPass(@NotNull String id, @NotNull String fragmentSrc) {
        checkPassId(id);
        if (fragmentSrc == null || fragmentSrc.isBlank()) {
            throw new IllegalArgumentException(
                    "Shaders:registerGlslPass('" + id + "') needs a non-blank fragment body.");
        }
        String normalized = fragmentSrc.strip();
        if (normalized.length() > MAX_FRAGMENT_CHARS) {
            throw new IllegalArgumentException(
                    "Shaders:registerGlslPass('" + id + "') body is " + normalized.length()
                            + " chars, max is " + MAX_FRAGMENT_CHARS + ".");
        }
        String flat = normalized.replaceAll("\\s+", "");
        if (!flat.contains("voidmain")) {
            throw new IllegalArgumentException(
                    "Shaders:registerGlslPass('" + id + "') body must contain 'void main'.");
        }
        if (flat.contains("#version")) {
            throw new IllegalArgumentException(
                    "Shaders:registerGlslPass('" + id + "') must NOT declare '#version'"
                            + " — the engine injects it. Write the body only.");
        }
        if (flat.contains("uniformsampler2D")) {
            throw new IllegalArgumentException(
                    "Shaders:registerGlslPass('" + id + "') must NOT declare 'uniform sampler2D'"
                            + " — the engine declares DiffuseSampler. Use it, don't redeclare it.");
        }
        if (flat.contains("invec2texCoord")) {
            throw new IllegalArgumentException(
                    "Shaders:registerGlslPass('" + id + "') must NOT declare 'in vec2 texCoord'"
                            + " — the engine declares it. Use it, don't redeclare it.");
        }
        if (flat.contains("outvec4fragColor")) {
            throw new IllegalArgumentException(
                    "Shaders:registerGlslPass('" + id + "') must NOT declare 'out vec4 fragColor'"
                            + " — the engine declares it. Write to it, don't redeclare it.");
        }
        String sha = sha256Hex(id + "\nbody\n" + normalized);
        publishPass(id, buildFragmentFile(id, sha, normalized), sha);
    }

    @Override
    public void registerSpirvPass(@NotNull String id, @NotNull String spirvBase64) {
        checkPassId(id);
        if (spirvBase64 == null || spirvBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "Shaders:registerSpirvPass('" + id + "') needs a non-blank base64 SPIR-V module.");
        }
        String trimmed = spirvBase64.strip();
        if (trimmed.length() > 360000) {
            throw new IllegalArgumentException(
                    "Shaders:registerSpirvPass('" + id + "') base64 is " + trimmed.length()
                            + " chars, max is 360000 (~256KB of SPIR-V).");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Shaders:registerSpirvPass('" + id + "') is not valid base64.", e);
        }
        String fullGlsl;
        try {
            fullGlsl = SpirvTranslator.translateToGlsl150(bytes);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Shaders:registerSpirvPass('" + id + "') " + e.getMessage(), e);
        }
        if (!fullGlsl.replaceAll("\\s+", "").contains("voidmain")) {
            throw new IllegalArgumentException(
                    "Shaders:registerSpirvPass('" + id + "') translated to GLSL without 'void main'.");
        }
        String sha = sha256Hex(id + "\nspirv\n" + trimmed);
        // Translated output is a complete shader (own #version and declarations),
        // so it is published verbatim — no header injection.
        publishPass(id, fullGlsl, sha);
    }

    private void checkPassId(@NotNull String id) {
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

    private void publishPass(String id, String fragmentFile, String sha) {
        boolean cacheHit = readCacheMarker(id, sha);
        String chainJson = buildChainJson(id);
        String programJson = buildProgramJson(id);
        writeCacheMarker(id, sha);
        datapackService.addData("assets/luatweaker/shaders/post/lt_" + id + ".json", chainJson);
        datapackService.addData("assets/luatweaker/shaders/program/lt_" + id + ".json", programJson);
        datapackService.addData("assets/luatweaker/shaders/program/lt_" + id + ".fsh", fragmentFile);
        PASS_REGISTRY.put(id, "luatweaker:shaders/post/lt_" + id + ".json");
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Shaders] pass '" + id + "' registered (sha " + sha.substring(0, 12)
                        + (cacheHit ? ", cache hit" : ", cache write") + ").");
    }

    private void applyBuiltin(BuiltinShaderPasses.BuiltinPass builtin, @Nullable ILuaTable params) {
        // Always (re)publish: a reload wipes the virtual-files map while this
        // static registry survives, so skipping here would leave stale state.
        // Re-registration is idempotent (replace semantics + SHA-256 cache hit).
        registerGlslPass(builtin.id(), builtin.body());
        activatePass(builtin.id());
        for (BuiltinShaderPasses.UniformSpec spec : builtin.uniforms()) {
            double value = readNumber(params, spec.param(), spec.def(), spec.min(), spec.max(), builtin.id());
            setPassUniform(spec.uniform(), value);
        }
    }

    @Override
    public void activatePass(@NotNull String id) {
        String chainLocation = PASS_REGISTRY.get(id);
        if (chainLocation == null) {
            List<String> known = new ArrayList<>(PASS_REGISTRY.keySet());
            Collections.sort(known);
            throw new IllegalArgumentException(
                    "Shaders:activatePass unknown id '" + id + "'. Register it first"
                            + " with registerGlslPass. Registered: "
                            + (known.isEmpty() ? "(none)" : String.join(", ", known)));
        }
        activePassId = id;
        IPlatformClient client = Platform.getClientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Shaders] activatePass('" + id + "') recorded on a dedicated server (no client to render on).");
            return;
        }
        client.activateShaderPost(chainLocation, "luatweaker:lt_" + id);
    }

    @Override
    public void deactivatePass() {
        activePassId = null;
        IPlatformClient client = Platform.getClientOrNull();
        if (client != null) {
            client.deactivateShaderPost();
        }
    }

    @Override
    public @Nullable String getActivePass() {
        return activePassId;
    }

    @Override
    public void setPassUniform(@NotNull String name, double value) {
        String active = activePassId;
        if (active == null) {
            throw new IllegalArgumentException(
                    "Shaders:setPassUniform('" + name + "') has no active pass — call activatePass first.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Shaders:setPassUniform needs a non-blank uniform name.");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Shaders:setPassUniform('" + name + "') value must be finite, got " + value + ".");
        }
        IPlatformClient client = Platform.getClientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Shaders] setPassUniform('" + name + "') recorded on a dedicated server.");
            return;
        }
        client.setShaderFloat(name, (float) value);
    }

    @Override
    public void setPassUniformVec(@NotNull String name, @NotNull float[] values) {
        String active = activePassId;
        if (active == null) {
            throw new IllegalArgumentException(
                    "Shaders:setPassUniformVec('" + name + "') has no active pass — call activatePass first.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Shaders:setPassUniformVec needs a non-blank uniform name.");
        }
        if (values == null || values.length < 2 || values.length > 4) {
            throw new IllegalArgumentException(
                    "Shaders:setPassUniformVec('" + name + "') needs 2-4 numbers (vec2/vec3/vec4).");
        }
        for (float component : values) {
            if (!Float.isFinite(component)) {
                throw new IllegalArgumentException(
                        "Shaders:setPassUniformVec('" + name + "') values must be finite.");
            }
        }
        IPlatformClient client = Platform.getClientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Shaders] setPassUniformVec('" + name + "') recorded on a dedicated server.");
            return;
        }
        client.setShaderFloatVec(name, values);
    }

    // ------------------------------------------------------------------
    // Render-thread readers (called every frame by the platform hook)
    // ------------------------------------------------------------------

    /**
     * Combined camera delta for this frame: {yaw, pitch, roll, fovAdd}.
     * Returns null when every one-shot has expired (zero per-frame cost path).
     */
    public static float @Nullable [] getCameraDelta(long nowMs) {
        float yaw = 0.0f;
        float pitch = 0.0f;
        float roll = 0.0f;
        float fov = 0.0f;
        boolean any = false;
        if (kickStartMs != 0L) {
            double elapsed = (nowMs - kickStartMs) / 1000.0;
            if (elapsed >= kickSeconds || kickSeconds <= 0.0) {
                kickStartMs = 0L;
            } else {
                double falloff = 1.0 - elapsed / kickSeconds;
                double radians = Math.toRadians(kickDirectionDeg);
                yaw += (float) (kickStrength * falloff * Math.cos(radians));
                pitch += (float) (kickStrength * falloff * Math.sin(radians));
                any = true;
            }
        }
        if (rollStartMs != 0L) {
            double elapsed = (nowMs - rollStartMs) / 1000.0;
            if (elapsed >= rollSeconds || rollSeconds <= 0.0) {
                rollStartMs = 0L;
            } else {
                roll += (float) (rollDegrees * (1.0 - elapsed / rollSeconds));
                any = true;
            }
        }
        if (swayStartMs != 0L) {
            double elapsed = (nowMs - swayStartMs) / 1000.0;
            if (elapsed >= swaySeconds || swaySeconds <= 0.0) {
                swayStartMs = 0L;
            } else {
                double phase = elapsed / swaySeconds * Math.PI * 2.0;
                yaw += (float) (swayAmplitude * Math.sin(phase));
                pitch += (float) (swayAmplitude * 0.6 * Math.cos(phase));
                any = true;
            }
        }
        if (fovStartMs != 0L) {
            double elapsed = (nowMs - fovStartMs) / 1000.0;
            if (elapsed >= fovSeconds || fovSeconds <= 0.0) {
                fovStartMs = 0L;
            } else {
                fov += (float) (fovAmount * (1.0 - elapsed / fovSeconds));
                any = true;
            }
        }
        return any ? new float[] { yaw, pitch, roll, fov } : null;
    }

    /** Current fade color with ramp applied, or 0 when no fade is showing. */
    public static int getFadeArgb(long nowMs) {
        if (!fadeActive) {
            return 0;
        }
        long ramp = Math.max(1L, fadeRampMs);
        float progress = Math.min(1.0f, (float) (nowMs - fadeStartMs) / ramp);
        int baseAlpha = (fadeArgb >>> 24) & 0xFF;
        int alpha = (int) (baseAlpha * progress);
        if (alpha <= 0) {
            return 0;
        }
        return (alpha << 24) | (fadeArgb & 0x00FFFFFF);
    }

    public static double getLetterboxHeight() {
        return letterboxHeight;
    }

    public static double getVignetteStrength() {
        return vignetteStrength;
    }

    public static int getVignetteArgb() {
        return vignetteArgb;
    }

    public static double getTintStrength() {
        return tintStrength;
    }

    public static int getTintArgb() {
        return tintArgb;
    }

    public static double getPulseStrength() {
        return pulseStrength;
    }

    public static int getPulseArgb() {
        return pulseArgb;
    }

    public static double getFrostStrength() {
        return frostStrength;
    }

    public static double getBloodStrength() {
        return bloodStrength;
    }

    // ------------------------------------------------------------------
    // File generation (pure string building, no new deps)
    // ------------------------------------------------------------------

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

    static String buildProgramJson(String id) {
        return "{\n"
                + "  \"blend\": {\"func\": \"add\", \"srcrgb\": \"srcalpha\", \"dstrgb\": \"1-srcalpha\"},\n"
                + "  \"vertex\": \"minecraft:blit\",\n"
                + "  \"fragment\": \"luatweaker:lt_" + id + "\",\n"
                + "  \"attributes\": [\"Position\"],\n"
                + "  \"samplers\": [{\"name\": \"DiffuseSampler\"}],\n"
                + "  \"uniforms\": [\n"
                + "    {\"name\": \"ProjMat\", \"type\": \"matrix4x4\", \"count\": 16,"
                + " \"values\": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0,"
                + " 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]},\n"
                + "    {\"name\": \"InSize\", \"type\": \"float\", \"count\": 2,"
                + " \"values\": [1.0, 1.0]},\n"
                + "    {\"name\": \"OutSize\", \"type\": \"float\", \"count\": 2,"
                + " \"values\": [1.0, 1.0]}\n"
                + "  ]\n"
                + "}\n";
    }

    static String buildFragmentFile(String id, String sha, String body) {
        return "#version 150\n"
                + "// Generated by LuaTweaker Shaders: pass '" + id + "' (sha " + sha + ").\n"
                + "// Engine-provided: DiffuseSampler, Time, InSize, OutSize, ScreenSize,\n"
                + "// texCoord, fragColor. Write 'void main' only.\n"
                + "uniform sampler2D DiffuseSampler;\n"
                + "uniform float Time;\n"
                + "uniform vec2 InSize;\n"
                + "uniform vec2 OutSize;\n"
                + "uniform vec2 ScreenSize;\n"
                + "in vec2 texCoord;\n"
                + "out vec4 fragColor;\n"
                + body + "\n";
    }

    // ------------------------------------------------------------------
    // Params, colors, hashing, cache
    // ------------------------------------------------------------------

    private static double readNumber(@Nullable ILuaTable params, String key,
                                     double def, double min, double max, String effect) {
        ILuaValue value = readKey(params, key);
        if (value == null || value.isNil()) {
            return def;
        }
        Object raw = value.toJavaObject();
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Shaders:setEffect('" + effect + "') param '" + key
                            + "' must be a number, got: " + raw + ".");
        }
        double parsed = number.doubleValue();
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(
                    "Shaders:setEffect('" + effect + "') param '" + key + "' must be finite.");
        }
        if (parsed < min || parsed > max) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] effect '" + effect + "' param '" + key + "' clamped to ["
                            + min + ", " + max + "], got " + parsed + ".");
            return Math.min(max, Math.max(min, parsed));
        }
        return parsed;
    }

    private static int readColor(@Nullable ILuaTable params, String key, int def, String effect) {
        ILuaValue value = readKey(params, key);
        if (value == null || value.isNil()) {
            return def;
        }
        Object raw = value.toJavaObject();
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (!(raw instanceof String text)) {
            throw new IllegalArgumentException(
                    "Shaders:setEffect('" + effect + "') param '" + key
                            + "' must be a hex color ('#RRGGBB'/'#AARRGGBB') or integer, got: " + raw + ".");
        }
        return parseHexColor(text, effect, key);
    }

    private static @Nullable ILuaValue readKey(@Nullable ILuaTable params, String key) {
        if (params == null) {
            return null;
        }
        ILuaValue value = params.rawget(key);
        if (value == null || value.isNil()) {
            String alt = Character.toUpperCase(key.charAt(0)) + key.substring(1);
            value = params.rawget(alt);
        }
        return value;
    }

    static int parseHexColor(String text, String effect, String key) {
        String hex = text.strip();
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6 && hex.length() != 8) {
            throw new IllegalArgumentException(
                    "Shaders:setEffect('" + effect + "') param '" + key
                            + "' must be '#RRGGBB' or '#AARRGGBB', got '" + text + "'.");
        }
        int color;
        try {
            color = (int) Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Shaders:setEffect('" + effect + "') param '" + key
                            + "' is not a hex color: '" + text + "'.", e);
        }
        if (hex.length() == 6) {
            color |= 0xFF000000;
        }
        return color;
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private boolean readCacheMarker(String id, String sha) {
        try {
            File marker = new File(cacheDir, sha + ".json");
            if (!marker.isFile()) {
                return false;
            }
            String content = Files.readString(marker.toPath(), StandardCharsets.UTF_8);
            return content.contains("\"sha\": \"" + sha + "\"") && content.contains("\"id\": \"" + id + "\"");
        } catch (IOException | SecurityException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] cache read failed for pass '" + id + "' (" + e.getMessage() + "); regenerating.");
            return false;
        }
    }

    private void writeCacheMarker(String id, String sha) {
        try {
            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
                throw new IOException("mkdirs failed for " + cacheDir);
            }
            String content = "{\"id\": \"" + id + "\", \"sha\": \"" + sha + "\"}\n";
            Files.writeString(new File(cacheDir, sha + ".json").toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] cache write failed for pass '" + id + "' (" + e.getMessage()
                            + "); continuing RAM-only.");
        }
    }
}
