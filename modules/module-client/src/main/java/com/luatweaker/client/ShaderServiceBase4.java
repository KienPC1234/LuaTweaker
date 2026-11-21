package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;
import java.io.File;

abstract class ShaderServiceBase4 extends ShaderServiceBase3 {

    protected ShaderServiceBase4(IDatapackService datapackService, File cacheDir) {
        super(datapackService, cacheDir);
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
}
