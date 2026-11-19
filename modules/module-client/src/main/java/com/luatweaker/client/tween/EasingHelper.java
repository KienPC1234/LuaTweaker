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

    private static final double BACK_OVERSHOOT = 1.70158;
    private static final double ELASTIC_PERIOD = (2.0 * Math.PI) / 3.0;

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
        return switch (direction == null ? EasingDirection.IN : direction) {
            case IN -> easeIn(t, style);
            case OUT -> 1.0 - easeIn(1.0 - t, style);
            case IN_OUT -> t < 0.5
                    ? easeIn(t * 2.0, style) / 2.0
                    : 1.0 - easeIn(2.0 - t * 2.0, style) / 2.0;
        };
    }

    private static double easeIn(double t, EasingStyle style) {
        return switch (style) {
            case LINEAR -> t;
            case SINE -> 1.0 - Math.cos((t * Math.PI) / 2.0);
            case QUAD -> t * t;
            case CUBIC -> t * t * t;
            case QUART -> t * t * t * t;
            case QUINT -> t * t * t * t * t;
            case EXPO -> t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * t - 10.0);
            case CIRC -> 1.0 - Math.sqrt(1.0 - t * t);
            case BACK -> {
                double c3 = BACK_OVERSHOOT + 1.0;
                yield c3 * t * t * t - BACK_OVERSHOOT * t * t;
            }
            case ELASTIC -> {
                if (t == 0.0 || t == 1.0) {
                    yield t;
                }
                yield -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * ELASTIC_PERIOD);
            }
            case BOUNCE -> bounceIn(t);
        };
    }

    private static double bounceIn(double t) {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (t < 1.0 / d1) {
            return n1 * t * t;
        }
        if (t < 2.0 / d1) {
            double shifted = t - 1.5 / d1;
            return n1 * shifted * shifted + 0.75;
        }
        if (t < 2.5 / d1) {
            double shifted = t - 2.25 / d1;
            return n1 * shifted * shifted + 0.9375;
        }
        double shifted = t - 2.625 / d1;
        return n1 * shifted * shifted + 0.984375;
    }
}
