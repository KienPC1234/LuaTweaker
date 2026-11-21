package com.luatweaker.client.tween;

/**
 * Roblox-style easing curves.
 *
 * <p>Every style implements its "in" shape; "out" and "in-out" are derived from it so the
 * three directions stay consistent. BACK and ELASTIC intentionally overshoot.</p>
 */
public final class EasingHelper {

    private EasingHelper() {
    }

    public enum EasingStyle {
        LINEAR, SINE, QUAD, CUBIC, QUART, QUINT, EXPO, CIRC, BACK, ELASTIC, BOUNCE
    }

    public enum EasingDirection {
        IN, OUT, IN_OUT
    }

    private static final double BACK_OVERSHOOT = 1.701_58;
    private static final double ELASTIC_PERIOD = 2.0 * Math.PI / 3.0;
    private static final double HALF = 0.5;
    private static final double FULL = 2.0;
    private static final double BOUNCE_N1 = 7.5625;
    private static final double BOUNCE_D1 = 2.75;

    public static double interpolate(double start, double end, double progress,
                                     EasingStyle style, EasingDirection direction) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        double eased = applyEasing(clamped, style, direction);
        return start + (end - start) * eased;
    }

    public static double applyEasing(double t, EasingStyle style, EasingDirection direction) {
        if (style == null || style == EasingStyle.LINEAR) {
            return t;
        }
        EasingDirection resolved = direction == null ? EasingDirection.IN : direction;
        return switch (resolved) {
            case IN -> easeIn(t, style);
            case OUT -> 1.0 - easeIn(1.0 - t, style);
            case IN_OUT -> inOutEase(t, style);
        };
    }

    private static double inOutEase(double t, EasingStyle style) {
        return t < HALF
                ? easeIn(t * FULL, style) / FULL
                : 1.0 - easeIn(FULL - t * FULL, style) / FULL;
    }

    private static double easeIn(double t, EasingStyle style) {
        return switch (style) {
            case LINEAR -> t;
            case SINE -> 1.0 - Math.cos(t * Math.PI / 2.0);
            case QUAD -> t * t;
            case CUBIC -> t * t * t;
            case QUART -> t * t * t * t;
            case QUINT -> t * t * t * t * t;
            default -> advancedEaseIn(t, style);
        };
    }

    private static double advancedEaseIn(double t, EasingStyle style) {
        return switch (style) {
            case EXPO -> t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * t - 10.0);
            case CIRC -> 1.0 - Math.sqrt(1.0 - t * t);
            case BACK -> backIn(t);
            case ELASTIC -> elasticIn(t);
            case BOUNCE -> bounceIn(t);
            default -> t;
        };
    }

    private static double backIn(double t) {
        double c3 = BACK_OVERSHOOT + 1.0;
        return c3 * t * t * t - BACK_OVERSHOOT * t * t;
    }

    private static double elasticIn(double t) {
        if (t == 0.0 || t == 1.0) {
            return t;
        }
        return -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * ELASTIC_PERIOD);
    }

    private static double bounceIn(double t) {
        if (t < 1.0 / BOUNCE_D1) {
            return BOUNCE_N1 * t * t;
        }
        if (t < 2.0 / BOUNCE_D1) {
            double shifted = t - 1.5 / BOUNCE_D1;
            return BOUNCE_N1 * shifted * shifted + 0.75;
        }
        if (t < 2.5 / BOUNCE_D1) {
            double shifted = t - 2.25 / BOUNCE_D1;
            return BOUNCE_N1 * shifted * shifted + 0.9375;
        }
        double shifted = t - 2.625 / BOUNCE_D1;
        return BOUNCE_N1 * shifted * shifted + 0.984_375;
    }
}
