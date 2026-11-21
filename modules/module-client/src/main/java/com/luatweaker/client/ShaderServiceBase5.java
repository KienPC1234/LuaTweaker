package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;

abstract class ShaderServiceBase5 extends ShaderServiceBase4 {

    protected ShaderServiceBase5(IDatapackService datapackService, File cacheDir) {
        super(datapackService, cacheDir);
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
}
