package com.luatweaker.math;

public record Color3(double r, double g, double b) {
    public static final Color3 BLACK = new Color3(0, 0, 0);
    public static final Color3 WHITE = new Color3(1, 1, 1);

    public static Color3 fromRGB(double r, double g, double b) {
        return new Color3(r / 255.0, g / 255.0, b / 255.0);
    }

    public static Color3 fromHex(String hex) {
        String cleanHex = hex.replace("#", "");
        if (cleanHex.length() == 6) {
            int rInt = Integer.parseInt(cleanHex.substring(0, 2), 16);
            int gInt = Integer.parseInt(cleanHex.substring(2, 4), 16);
            int bInt = Integer.parseInt(cleanHex.substring(4, 6), 16);
            return fromRGB(rInt, gInt, bInt);
        }
        return BLACK;
    }

    public int toRGBInt() {
        int rInt = (int) Math.clamp(r * 255.0, 0, 255);
        int gInt = (int) Math.clamp(g * 255.0, 0, 255);
        int bInt = (int) Math.clamp(b * 255.0, 0, 255);
        return (rInt << 16) | (gInt << 8) | bInt;
    }

    public Color3 lerp(Color3 other, double alpha) {
        return new Color3(
            r + (other.r - r) * alpha,
            g + (other.g - g) * alpha,
            b + (other.b - b) * alpha
        );
    }
}
