package com.luatweaker.client.tween;

public class EasingHelper {

    public enum EasingStyle {
        LINEAR,
        QUAD,
        SINE
    }

    public enum EasingDirection {
        IN,
        OUT,
        IN_OUT
    }

    public static double interpolate(double start, double end, double progress, EasingStyle style, EasingDirection direction) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        double eased = applyEasing(progress, style, direction);
        return start + (end - start) * eased;
    }

    private static double applyEasing(double t, EasingStyle style, EasingDirection direction) {
        if (style == EasingStyle.LINEAR) return t;
        
        switch (style) {
            case QUAD:
                switch (direction) {
                    case IN: return t * t;
                    case OUT: return t * (2 - t);
                    case IN_OUT: return t < .5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
                }
                break;
            case SINE:
                switch (direction) {
                    case IN: return 1 - Math.cos((t * Math.PI) / 2);
                    case OUT: return Math.sin((t * Math.PI) / 2);
                    case IN_OUT: return -(Math.cos(Math.PI * t) - 1) / 2;
                }
                break;
            default:
                break;
        }
        return t;
    }
}
