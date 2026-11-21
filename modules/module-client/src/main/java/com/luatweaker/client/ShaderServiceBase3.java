package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class ShaderServiceBase3 extends ShaderServiceBase2 {

    protected ShaderServiceBase3(IDatapackService datapackService, File cacheDir) {
        super(datapackService, cacheDir);
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
        String sha = sha256Hex(id + "\nbody\n" + normalized);
        publishPass(id, buildFragmentFile(id, sha, normalized), sha);
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
}
