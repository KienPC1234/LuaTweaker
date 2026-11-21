package com.luatweaker.client.tween;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Parsed {@code TweenInfo} fields. Kept out of {@link TweenImpl} so its constructor stays a
 * small assignment block and invalid enums are recovered in one documented place.
 */
record TweenConfig(double duration, double delayTime, int repeatCount, boolean reverses,
                   EasingHelper.EasingStyle style, EasingHelper.EasingDirection direction) {

    /** Roblox uses -1 for "repeat forever". */
    private static final int INFINITE_REPEAT = -1;
    private static final double DEFAULT_DURATION = 1.0;

    static @NotNull TweenConfig parse(@Nullable ILuaTable tweenInfo) {
        if (tweenInfo == null) {
            return new TweenConfig(DEFAULT_DURATION, 0.0, 0, false,
                    EasingHelper.EasingStyle.LINEAR, EasingHelper.EasingDirection.OUT);
        }
        // The shipped TweenInfo.new writes Time; older/hand-built tables use Duration.
        double duration = readNumber(tweenInfo, "Duration", readNumber(tweenInfo, "Time", DEFAULT_DURATION));
        EasingHelper.EasingStyle style = parseStyle(tweenInfo);
        EasingHelper.EasingDirection direction = parseDirection(tweenInfo);
        double delayTime = Math.max(0.0, readNumber(tweenInfo, "DelayTime", 0.0));
        int repeatCount = Math.max(INFINITE_REPEAT, (int) readNumber(tweenInfo, "RepeatCount", 0.0));
        ILuaValue reversesValue = tweenInfo.rawget("Reverses");
        boolean reverses = reversesValue != null && reversesValue.isBoolean()
                && reversesValue.asBoolean();
        return new TweenConfig(duration, delayTime, repeatCount, reverses, style, direction);
    }

    private static EasingHelper.EasingStyle parseStyle(@NotNull ILuaTable tweenInfo) {
        ILuaValue styleValue = tweenInfo.rawget("EasingStyle");
        if (styleValue != null && styleValue.isString()) {
            try {
                return EasingHelper.EasingStyle.valueOf(styleValue.asString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Unknown Tween EasingStyle '" + styleValue.asString() + "', using LINEAR");
            }
        }
        return EasingHelper.EasingStyle.LINEAR;
    }

    private static EasingHelper.EasingDirection parseDirection(@NotNull ILuaTable tweenInfo) {
        ILuaValue directionValue = tweenInfo.rawget("EasingDirection");
        if (directionValue != null && directionValue.isString()) {
            try {
                return EasingHelper.EasingDirection.valueOf(
                        directionValue.asString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Unknown Tween EasingDirection '" + directionValue.asString() + "', using OUT");
            }
        }
        return EasingHelper.EasingDirection.OUT;
    }

    private static double readNumber(@NotNull ILuaTable table, @NotNull String key, double fallback) {
        ILuaValue value = table.rawget(key);
        return value != null && value.isNumber() ? value.asDouble() : fallback;
    }
}
