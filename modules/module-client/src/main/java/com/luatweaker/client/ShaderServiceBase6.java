package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.Base64;

abstract class ShaderServiceBase6 extends ShaderServiceBase5 {

    protected ShaderServiceBase6(IDatapackService datapackService, File cacheDir) {
        super(datapackService, cacheDir);
    }

    @Override
    public void registerSpirvPass(@NotNull String id, @NotNull String spirvBase64) {
        checkPassId(id);
        String trimmed = validateSpirvBase64(id, spirvBase64);
        String fullGlsl = translateSpirv(id, decodeSpirv(id, trimmed));
        requireVoidMain(id, fullGlsl);
        String sha = sha256Hex(id + "\nspirv\n" + trimmed);
        // Translated output is a complete shader (own #version and declarations),
        // so it is published verbatim — no header injection.
        publishPass(id, fullGlsl, sha);
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
}
