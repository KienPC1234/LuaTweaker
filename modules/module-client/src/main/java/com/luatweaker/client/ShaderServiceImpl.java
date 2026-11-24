package com.luatweaker.client;

import com.luatweaker.api.client.IShaderService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Screen-space visual effects service facade: named camera/overlay effects
 * plus custom GLSL/SPIR-V post passes.
 */
public class ShaderServiceImpl implements IShaderService {

    public ShaderServiceImpl(@NotNull IDatapackService datapackService, @NotNull File cacheDir) {
        this.datapackService = datapackService;
        this.cacheDir = cacheDir;
    }

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

    static String buildProgramJson(String id, boolean useInSize,
                                   boolean useScreenSize, boolean useTime,
                                   List<String> customUniforms) {
        StringBuilder uniforms = new StringBuilder(640);
        uniforms.append("    {\"name\": \"ProjMat\", \"type\": \"matrix4x4\", \"count\": 16,"
                + " \"values\": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0,"
                + " 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]},\n"
                + "    {\"name\": \"OutSize\", \"type\": \"float\", \"count\": 2, \"values\": [1.0, 1.0]}");
        if (useInSize) {
            uniforms.append(",\n    {\"name\": \"InSize\", \"type\": \"float\", \"count\": 2, \"values\": [1.0, 1.0]}");
        }
        if (useScreenSize) {
            uniforms.append(",\n    {\"name\": \"ScreenSize\", \"type\": \"float\", \"count\": 2, \"values\": [1.0, 1.0]}");
        }
        if (useTime) {
            uniforms.append(",\n    {\"name\": \"Time\", \"type\": \"float\", \"count\": 1, \"values\": [0.0]}");
        }
        for (String custom : customUniforms) {
            uniforms.append(",\n    ").append(custom);
        }
        return "{\n"
                + "  \"blend\": {\"func\": \"add\", \"srcrgb\": \"srcalpha\", \"dstrgb\": \"1-srcalpha\"},\n"
                + "  \"vertex\": \"minecraft:blit\",\n"
                + "  \"fragment\": \"luatweaker:lt_" + id + "\",\n"
                + "  \"attributes\": [\"Position\"],\n"
                + "  \"samplers\": [{\"name\": \"DiffuseSampler\"}],\n"
                + "  \"uniforms\": [\n"
                + uniforms + "\n"
                + "  ]\n"
                + "}\n";
    }

    /** True when the authored fragment body actually reads the named size uniform. */
    static boolean usesSizeUniform(@NotNull String body, @NotNull String uniform) {
        return Pattern.compile("\\b" + Pattern.quote(uniform) + "\\b").matcher(body).find();
    }

    boolean readCacheMarker(String id, String sha) {
        try {
            File marker = new File(cacheDir, sha + JSON_SUFFIX);
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

    void writeCacheMarker(String id, String sha) {
        try {
            if (!cacheDir.isDirectory() && !cacheDir.mkdirs()) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Shaders] cache directory creation failed for " + cacheDir
                                + "; continuing RAM-only.");
                return;
            }
            String content = "{\"id\": \"" + id + "\", \"sha\": \"" + sha + "\"}\n";
            Files.writeString(new File(cacheDir, sha + JSON_SUFFIX).toPath(), content,
                    StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] cache write failed for pass '" + id + "' (" + e.getMessage()
                            + "); continuing RAM-only.");
        }
    }

    void publishPass(String id, String fragmentFile, String sha, String fragmentBody) {
        boolean cacheHit = readCacheMarker(id, sha);
        String chainJson = buildChainJson(id);
        // OutSize is always emitted because minecraft:blit.vsh divides Position.xy by OutSize;
        // optional engine globals and declared user uniforms are bound in EffectInstance.
        List<String> customUniforms = GlslUniformExtractor.extractJsonEntries(fragmentBody);
        String programJson = buildProgramJson(id,
                usesSizeUniform(fragmentBody, "InSize"),
                usesSizeUniform(fragmentBody, "ScreenSize"),
                usesSizeUniform(fragmentBody, "Time"),
                customUniforms);
        writeCacheMarker(id, sha);
        datapackService.addData("assets/luatweaker/shaders/post/lt_" + id + JSON_SUFFIX, chainJson);
        datapackService.addData("assets/luatweaker/shaders/program/lt_" + id + JSON_SUFFIX, programJson);
        datapackService.addData("assets/luatweaker/shaders/program/lt_" + id + ".fsh", fragmentFile);
        PASS_REGISTRY.put(id, "luatweaker:shaders/post/lt_" + id + JSON_SUFFIX);
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "[Shaders] pass '" + id + "' registered (sha " + sha.substring(0, 12)
                        + (cacheHit ? ", cache hit" : ", cache write") + ").");
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

static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, HEX_RADIX))
                        .append(Character.forDigit(b & 0xF, HEX_RADIX));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
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
        // Register-time lint with line numbers: catches driver-rejected shapes
        // (header redeclares, GLSL1-isms, unbalanced delimiters) while the author
        // is still looking at the source. Driver-side type errors surface later
        // through the post-activation check, which points at the driver info log.
        GlslLint.lintFragmentBody(id, normalized);
        String sha = sha256Hex("v2\n" + id + "\nbody\n" + normalized);
        publishPass(id, buildFragmentFile(id, sha, normalized), sha, normalized);
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
        ACTIVE_PASS_ID.set(id);
        IPlatformClient client = Platform.getClientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Shaders] activatePass('" + id + "') recorded on a dedicated server (no client to render on).");
            return;
        }
        client.activateShaderPost(chainLocation, "luatweaker:lt_" + id);
    }

    @Override
    public @Nullable String getActivePass() {
        return ACTIVE_PASS_ID.get().isEmpty() ? null : ACTIVE_PASS_ID.get();
    }

    @Override
    public void setPassUniform(@NotNull String name, double value) {
        String active = getActivePass();
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

static @Nullable ILuaValue readKey(@Nullable ILuaTable params, String key) {
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

    static double readNumber(@Nullable ILuaTable params, String key,
                                     double def, double min, double max, String effect) {
        ILuaValue value = readKey(params, key);
        if (value == null || value.isNil()) {
            return def;
        }
        Object raw = value.toJavaObject();
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(
                    ERR_SET_EFFECT + effect + ERR_PARAM + key
                            + "' must be a number, got: " + raw + ".");
        }
        double parsed = number.doubleValue();
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(
                    ERR_SET_EFFECT + effect + ERR_PARAM + key + "' must be finite.");
        }
        if (parsed < min || parsed > max) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Shaders] effect '" + effect + "' param '" + key + "' clamped to ["
                            + min + ", " + max + "], got " + parsed + ".");
            return Math.min(max, Math.max(min, parsed));
        }
        return parsed;
    }

    void applyBuiltin(BuiltinShaderPasses.BuiltinPass builtin, @Nullable ILuaTable params) {
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

    static String stripHexPrefix(String text, String effect, String key) {
        String hex = text.strip();
        if (hex.startsWith("0x") || hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != RGB_HEX_LENGTH && hex.length() != ARGB_HEX_LENGTH) {
            throw new IllegalArgumentException(
                    ERR_SET_EFFECT + effect + ERR_PARAM + key
                            + "' must be '#RRGGBB' or '#AARRGGBB', got '" + text + "'.");
        }
        return hex;
    }

    static int parseHexColor(String text, String effect, String key) {
        String hex = stripHexPrefix(text, effect, key);
        int color;
        try {
            color = (int) Long.parseLong(hex, HEX_RADIX);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    ERR_SET_EFFECT + effect + ERR_PARAM + key
                            + "' is not a hex color: '" + text + "'.", e);
        }
        if (hex.length() == RGB_HEX_LENGTH) {
            color |= 0xFF000000;
        }
        return color;
    }

static int readColor(@Nullable ILuaTable params, String key, int def, String effect) {
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
                    ERR_SET_EFFECT + effect + ERR_PARAM + key
                            + "' must be a hex color ('#RRGGBB'/'#AARRGGBB') or integer, got: " + raw + ".");
        }
        return parseHexColor(text, effect, key);
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
        if (!applyCameraEffect(name, params)) {
            applyOverlayEffect(name, params);
        }
    }

    private boolean applyCameraEffect(@NotNull String name, @Nullable ILuaTable params) {
        switch (name) {
            case "kick" -> {
                KICK_STRENGTH.set(readNumber(params, PARAM_STRENGTH, KICK_DEFAULT_STRENGTH, 0.0, 20.0, name));
                KICK_DIRECTION_DEG.set(readNumber(params, "direction", 0.0, -180.0, 180.0, name));
                KICK_SECONDS.set(readNumber(params, PARAM_SECONDS, KICK_DEFAULT_SECONDS, 0.05, 3.0, name));
                KICK_START_MS.set(System.currentTimeMillis());
                return true;
            }
            case "roll" -> {
                ROLL_DEGREES.set(readNumber(params, "degrees", ROLL_DEFAULT_DEGREES, -45.0, 45.0, name));
                ROLL_SECONDS.set(readNumber(params, PARAM_SECONDS, ROLL_DEFAULT_SECONDS, 0.05, 3.0, name));
                ROLL_START_MS.set(System.currentTimeMillis());
                return true;
            }
            case "sway" -> {
                SWAY_AMPLITUDE.set(readNumber(params, "amplitude", SWAY_DEFAULT_AMPLITUDE, 0.0, 10.0, name));
                SWAY_SECONDS.set(readNumber(params, PARAM_SECONDS, SWAY_DEFAULT_SECONDS, 0.5, 30.0, name));
                SWAY_START_MS.set(System.currentTimeMillis());
                return true;
            }
            case "fovPunch" -> {
                FOV_AMOUNT.set(readNumber(params, "amount", FOV_DEFAULT_AMOUNT, -30.0, 30.0, name));
                FOV_SECONDS.set(readNumber(params, PARAM_SECONDS, FOV_DEFAULT_SECONDS, 0.05, 3.0, name));
                FOV_START_MS.set(System.currentTimeMillis());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void applyOverlayEffect(@NotNull String name, @Nullable ILuaTable params) {
        switch (name) {
            case "fadeTo" -> {
                FADE_ARGB.set(readColor(params, PARAM_COLOR, 0xFF000000, name));
                FADE_RAMP_MS.set((long) (readNumber(params, PARAM_SECONDS, 0.5, 0.05, 10.0, name) * 1000.0));
                FADE_START_MS.set(System.currentTimeMillis());
                FADE_ACTIVE.set(true);
            }
            case "letterbox" -> LETTERBOX_HEIGHT.set(readNumber(params, "height", 0.12, 0.0, 0.4, name));
            case "vignette" -> {
                VIGNETTE_STRENGTH.set(readNumber(params, PARAM_STRENGTH, 0.6, 0.0, 1.0, name));
                VIGNETTE_ARGB.set(readColor(params, PARAM_COLOR, 0xFF000000, name));
            }
            case "tint" -> {
                TINT_STRENGTH.set(readNumber(params, PARAM_STRENGTH, 0.3, 0.0, 1.0, name));
                TINT_ARGB.set(readColor(params, PARAM_COLOR, 0xFFFFFFFF, name));
            }
            case "lowHealthPulse" -> {
                PULSE_STRENGTH.set(readNumber(params, PARAM_STRENGTH, 0.7, 0.0, 1.0, name));
                PULSE_ARGB.set(readColor(params, PARAM_COLOR, 0xFF880000, name));
            }
            case "frost" -> FROST_STRENGTH.set(readNumber(params, PARAM_STRENGTH, 0.5, 0.0, 1.0, name));
            case "bloodSplatter" -> BLOOD_STRENGTH.set(readNumber(params, PARAM_STRENGTH, 0.6, 0.0, 1.0, name));
            default -> throw new IllegalArgumentException(
                    "Shaders:setEffect unknown effect '" + name + "'. Supported: "
                            + String.join(", ", SUPPORTED_EFFECTS));
        }
    }

    @Override
    public void deactivatePass() {
        ACTIVE_PASS_ID.set("");
        IPlatformClient client = Platform.getClientOrNull();
        if (client != null) {
            client.deactivateShaderPost();
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
            if (builtin.id().equals(ACTIVE_PASS_ID.get())) {
                deactivatePass();
            }
            return;
        }
        if (!clearCameraEffect(name)) {
            clearOverlayEffect(name);
        }
    }

    private boolean clearCameraEffect(@NotNull String name) {
        switch (name) {
            case "kick" -> KICK_START_MS.set(IDLE_TIME_MS);
            case "roll" -> ROLL_START_MS.set(IDLE_TIME_MS);
            case "sway" -> SWAY_START_MS.set(IDLE_TIME_MS);
            case "fovPunch" -> FOV_START_MS.set(IDLE_TIME_MS);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void clearOverlayEffect(@NotNull String name) {
        switch (name) {
            case "fadeTo" -> FADE_ACTIVE.set(false);
            case "letterbox" -> LETTERBOX_HEIGHT.set(0.0);
            case "vignette" -> VIGNETTE_STRENGTH.set(0.0);
            case "tint" -> TINT_STRENGTH.set(0.0);
            case "lowHealthPulse" -> PULSE_STRENGTH.set(0.0);
            case "frost" -> FROST_STRENGTH.set(0.0);
            case "bloodSplatter" -> BLOOD_STRENGTH.set(0.0);
            default -> throw new IllegalArgumentException(
                    "Shaders:clearEffect unknown effect '" + name + "'.");
        }
    }

    @Override
    public void clearAll() {
        KICK_START_MS.set(0L);
        ROLL_START_MS.set(0L);
        SWAY_START_MS.set(0L);
        FOV_START_MS.set(0L);
        FADE_ACTIVE.set(false);
        LETTERBOX_HEIGHT.set(0.0);
        VIGNETTE_STRENGTH.set(0.0);
        TINT_STRENGTH.set(0.0);
        PULSE_STRENGTH.set(0.0);
        FROST_STRENGTH.set(0.0);
        BLOOD_STRENGTH.set(0.0);
        deactivatePass();
    }

@Override
    public void registerSpirvPass(@NotNull String id, @NotNull String spirvBase64) {
        checkPassId(id);
        String trimmed = validateSpirvBase64(id, spirvBase64);
        String fullGlsl = translateSpirv(id, decodeSpirv(id, trimmed));
        requireVoidMain(id, fullGlsl);
        String sha = sha256Hex("v2\n" + id + "\nspirv\n" + trimmed);
        // Translated output is a complete shader (own #version and declarations),
        // so it is published verbatim — no header injection.
        publishPass(id, fullGlsl, sha, fullGlsl);
    }

    private static String validateSpirvBase64(String id, String spirvBase64) {
        if (spirvBase64 == null || spirvBase64.isBlank()) {
            throw new IllegalArgumentException(
                    ERR_SPIRV + id + "') needs a non-blank base64 SPIR-V module.");
        }
        String trimmed = spirvBase64.strip();
        if (trimmed.length() > MAX_SPIRV_BASE64_CHARS) {
            throw new IllegalArgumentException(
                    ERR_SPIRV + id + "') base64 is " + trimmed.length()
                            + " chars, max is " + MAX_SPIRV_BASE64_CHARS + " (~256KB of SPIR-V).");
        }
        return trimmed;
    }

    private static byte[] decodeSpirv(String id, String trimmed) {
        try {
            return Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ERR_SPIRV + id + "') is not valid base64.", e);
        }
    }

    private static String translateSpirv(String id, byte[] bytes) {
        try {
            return SpirvTranslator.translateToGlsl150(bytes);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ERR_SPIRV + id + "') " + e.getMessage(), e);
        }
    }

    private static void requireVoidMain(String id, String fullGlsl) {
        if (!fullGlsl.replaceAll("\\s+", "").contains("voidmain")) {
            throw new IllegalArgumentException(
                    ERR_SPIRV + id + "') translated to GLSL without 'void main'.");
        }
    }

    static void validateVec(String name, float... values) {
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
    }

    @Override
    public void setPassUniformVec(@NotNull String name, @NotNull float[] values) {
        String active = getActivePass();
        if (active == null) {
            throw new IllegalArgumentException(
                    "Shaders:setPassUniformVec('" + name + "') has no active pass — call activatePass first.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Shaders:setPassUniformVec needs a non-blank uniform name.");
        }
        validateVec(name, values);
        IPlatformClient client = Platform.getClientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "[Shaders] setPassUniformVec('" + name + "') recorded on a dedicated server.");
            return;
        }
        client.setShaderFloatVec(name, values);
    }

    static void addKickDelta(CameraDelta delta, long nowMs) {
        if (KICK_START_MS.get() == IDLE_TIME_MS) {
            return;
        }
        double elapsed = (nowMs - KICK_START_MS.get()) / MILLIS_PER_SECOND;
        if (elapsed >= KICK_SECONDS.get() || KICK_SECONDS.get() <= ZERO_SECONDS) {
            KICK_START_MS.set(IDLE_TIME_MS);
            return;
        }
        double falloff = 1.0 - elapsed / KICK_SECONDS.get();
        double radians = Math.toRadians(KICK_DIRECTION_DEG.get());
        delta.yaw += (float) (KICK_STRENGTH.get() * falloff * Math.cos(radians));
        delta.pitch += (float) (KICK_STRENGTH.get() * falloff * Math.sin(radians));
        delta.any = true;
    }

    static void addRollDelta(CameraDelta delta, long nowMs) {
        if (ROLL_START_MS.get() == IDLE_TIME_MS) {
            return;
        }
        double elapsed = (nowMs - ROLL_START_MS.get()) / MILLIS_PER_SECOND;
        if (elapsed >= ROLL_SECONDS.get() || ROLL_SECONDS.get() <= ZERO_SECONDS) {
            ROLL_START_MS.set(IDLE_TIME_MS);
            return;
        }
        delta.roll += (float) (ROLL_DEGREES.get() * (1.0 - elapsed / ROLL_SECONDS.get()));
        delta.any = true;
    }

static void addSwayDelta(CameraDelta delta, long nowMs) {
        if (SWAY_START_MS.get() == IDLE_TIME_MS) {
            return;
        }
        double elapsed = (nowMs - SWAY_START_MS.get()) / MILLIS_PER_SECOND;
        if (elapsed >= SWAY_SECONDS.get() || SWAY_SECONDS.get() <= ZERO_SECONDS) {
            SWAY_START_MS.set(IDLE_TIME_MS);
            return;
        }
        double phase = elapsed / SWAY_SECONDS.get() * Math.PI * 2.0;
        delta.yaw += (float) (SWAY_AMPLITUDE.get() * Math.sin(phase));
        delta.pitch += (float) (SWAY_AMPLITUDE.get() * 0.6 * Math.cos(phase));
        delta.any = true;
    }

    static void addFovDelta(CameraDelta delta, long nowMs) {
        if (FOV_START_MS.get() == IDLE_TIME_MS) {
            return;
        }
        double elapsed = (nowMs - FOV_START_MS.get()) / MILLIS_PER_SECOND;
        if (elapsed >= FOV_SECONDS.get() || FOV_SECONDS.get() <= ZERO_SECONDS) {
            FOV_START_MS.set(IDLE_TIME_MS);
            return;
        }
        delta.fov += (float) (FOV_AMOUNT.get() * (1.0 - elapsed / FOV_SECONDS.get()));
        delta.any = true;
    }

    public static float @Nullable [] getCameraDelta(long nowMs) {
        CameraDelta delta = new CameraDelta();
        addKickDelta(delta, nowMs);
        addRollDelta(delta, nowMs);
        addSwayDelta(delta, nowMs);
        addFovDelta(delta, nowMs);
        return delta.any ? new float[] { delta.yaw, delta.pitch, delta.roll, delta.fov } : null;
    }

    public static int getFadeArgb(long nowMs) {
        if (!FADE_ACTIVE.get()) {
            return 0;
        }
        long ramp = Math.max(1L, FADE_RAMP_MS.get());
        float progress = Math.min(1.0f, (float) (nowMs - FADE_START_MS.get()) / ramp);
        int baseAlpha = (FADE_ARGB.get() >>> 24) & 0xFF;
        int alpha = (int) (baseAlpha * progress);
        if (alpha <= 0) {
            return 0;
        }
        return (alpha << 24) | (FADE_ARGB.get() & 0x00FFFFFF);
    }

    public static double getLetterboxHeight() {
        return LETTERBOX_HEIGHT.get();
    }

public static double getVignetteStrength() {
        return VIGNETTE_STRENGTH.get();
    }

    public static int getVignetteArgb() {
        return VIGNETTE_ARGB.get();
    }

    public static double getTintStrength() {
        return TINT_STRENGTH.get();
    }

    public static int getTintArgb() {
        return TINT_ARGB.get();
    }

    public static double getPulseStrength() {
        return PULSE_STRENGTH.get();
    }

public static int getPulseArgb() {
        return PULSE_ARGB.get();
    }

    public static double getFrostStrength() {
        return FROST_STRENGTH.get();
    }

    public static double getBloodStrength() {
        return BLOOD_STRENGTH.get();
    }
}
